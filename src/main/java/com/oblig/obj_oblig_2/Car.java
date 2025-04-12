package com.oblig.obj_oblig_2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Car extends Thread {
    private Position position;
    private double speed;
    private CarDirection direction;
    private Road currentRoad;
    private TrafficLight trafficLight;
    private List<Intersection> intersections;
    private int detectionRadius;
    private int laneOffset;
    private List<Car> nearbyVehicles;
    private double minSafeDistance = ConfigLoader.getInstance().getMinCarDistance();
    private int size;
    private Intersection currentIntersection;
    private boolean hasPassedTrafficLight;
    private boolean isInCooldown;
    private long cooldownEndTime;
    private static final long COOLDOWN_DURATION = 2000;
    private Simulation simulation;
    private boolean turningInProgress = false;
    private CarDirection targetDirection = null;
    private final Object positionLock = new Object();

    public Car() {
        this.position = new Position(0, 0);
        this.speed = 0;
        this.direction = CarDirection.NORTH;
        this.detectionRadius = ConfigLoader.getInstance().getTrafficLightDetectionRadius();
        this.intersections = new ArrayList<>();
        this.laneOffset = calculateLaneOffset();
        this.nearbyVehicles = new ArrayList<>();
        this.size = ConfigLoader.getInstance().getCarSize();
        this.minSafeDistance = ConfigLoader.getInstance().getMinCarDistance();
        this.currentIntersection = null;
    }

    public Car(Position position, double speed, CarDirection direction, TrafficLight trafficLight) {
        this.position = position;
        this.speed = speed;
        this.direction = direction;
        this.trafficLight = trafficLight;
        this.detectionRadius = ConfigLoader.getInstance().getTrafficLightDetectionRadius();
        this.intersections = new ArrayList<>();
        this.laneOffset = calculateLaneOffset();
        this.nearbyVehicles = new ArrayList<>();
        this.size = ConfigLoader.getInstance().getCarSize();
        this.minSafeDistance = ConfigLoader.getInstance().getMinCarDistance();
        this.currentIntersection = null;
    }

    private int calculateLaneOffset() {
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        int laneWidth = roadWidth / 2;

        switch (direction) {
            case NORTH: return laneWidth/2;   // RIGHT side of road
            case SOUTH: return -laneWidth/2;  // LEFT side of road
            case EAST: return laneWidth/2;    // BOTTOM side of road (changed from top)
            case WEST: return -laneWidth/2;   // TOP side of road (changed from bottom)
            default: return 0;
        }
    }

    public Position getPosition() {
        return position;
    }

    public void setIntersections(List<Intersection> intersections) {
        this.intersections = intersections;
    }

    public void setCurrentRoad(Road road) {
        this.currentRoad = road;
    }

    public void setDirection(CarDirection direction) {
        this.direction = direction;
        this.laneOffset = calculateLaneOffset();
    }

    public CarDirection getDirection() {
        return direction;
    }

    public void setNearbyVehicles(List<Car> vehicles) {
        this.nearbyVehicles = vehicles;
    }

    public int getSize() {
        return size;
    }

    @Override
    public void run() {
        while(!Thread.interrupted()) {
            move();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void move() {
        synchronized(positionLock) {
            // Check for collisions first
            if (shouldStopForNearbyVehicle()) {
                return;
            }

            // Handle intersection logic - completely separate from normal movement
            if (currentIntersection != null) {
                handleIntersectionMovement();
                return; // Don't proceed with normal road logic
            }

            // Normal road handling (outside of intersections)
            if (isInCooldown) {
                if (System.currentTimeMillis() < cooldownEndTime) {
                    moveInDirection();
                    return;
                } else {
                    isInCooldown = false;
                }
            }

            // Traffic light logic
            if (!hasPassedTrafficLight && shouldStopForTrafficLight()) {
                return;
            }

            if (trafficLight != null) {
                if (checkCollision(trafficLight)) {
                    return;
                }

                if (hasCrossedTrafficLight()) {
                    hasPassedTrafficLight = true;
                }
            }

            // Check if entering an intersection
            checkIntersectionEntry();

            // Normal movement on road
            moveInDirection();
        }
    }

    private void handleIntersectionMovement() {
        Position intersectionPos = currentIntersection.getPosition();

        // Calculate distance to intersection center
        double distanceToCenter = Math.sqrt(
                Math.pow(position.getX() - intersectionPos.getX(), 2) +
                        Math.pow(position.getY() - intersectionPos.getY(), 2)
        );

        // Step 1: Approach center until we're close enough to turn
        if (!turningInProgress && distanceToCenter > 5) {
            // Keep heading toward center at reduced speed
            double moveSpeed = Math.min(speed * 0.75, distanceToCenter);

            // Calculate normalized direction vector toward intersection center
            double dx = intersectionPos.getX() - position.getX();
            double dy = intersectionPos.getY() - position.getY();
            double length = Math.sqrt(dx * dx + dy * dy);

            // Move toward center using vector
            if (length > 0) {
                position.setX(position.getX() + (dx / length) * moveSpeed);
                position.setY(position.getY() + (dy / length) * moveSpeed);
            }
            return;
        }

        // Step 2: Prepare to turn when very close to center
        if (!turningInProgress && distanceToCenter <= 5) {
            turningInProgress = true;
            // Move exactly to center for clean turn
            position.setX(intersectionPos.getX());
            position.setY(intersectionPos.getY());

            // Choose random direction (not current and not opposite)
            List<CarDirection> validDirections = new ArrayList<>();
            for (CarDirection dir : CarDirection.values()) {
                if (dir != direction && dir != getOppositeDirection()) {
                    validDirections.add(dir);
                }
            }

            if (!validDirections.isEmpty()) {
                Random random = new Random();
                targetDirection = validDirections.get(random.nextInt(validDirections.size()));
                System.out.println("Preparing to turn from " + direction + " to " + targetDirection);
            } else {
                // Fallback - continue straight
                targetDirection = direction;
            }
            return;
        }

        // Step 3: Execute turn and find appropriate road
        if (turningInProgress) {
            // Change direction and update lane offset
            setDirection(targetDirection);
            System.out.println("Executing turn to " + targetDirection);

            // Find best matching road with improved algorithm
            Road bestRoad = null;
            for (Road road : getRoadsFromSimulation()) {
                // Only check roads that match our direction
                if ((direction == CarDirection.NORTH || direction == CarDirection.SOUTH) &&
                        road.isVertical()) {
                    double xDiff = Math.abs(road.getX1() - intersectionPos.getX());
                    if (xDiff < 30) { // Allow for some tolerance
                        bestRoad = road;
                        break; // Take the first good match
                    }
                }
                else if ((direction == CarDirection.EAST || direction == CarDirection.WEST) &&
                        road.isHorizontal()) {
                    double yDiff = Math.abs(road.getY1() - intersectionPos.getY());
                    if (yDiff < 30) { // Allow for some tolerance
                        bestRoad = road;
                        break; // Take the first good match
                    }
                }
            }

            // Set new road and position car properly outside the intersection
            if (bestRoad != null) {
                currentRoad = bestRoad;
                exitIntersectionToRoad(bestRoad, intersectionPos);
                System.out.println("Found matching road after turn");
            } else {
                // Emergency exit - use map coordinates
                emergencyExitIntersection(intersectionPos);
                System.out.println("No matching road - using emergency exit");
            }
        }
    }

    private void exitIntersectionToRoad(Road road, Position intersectionPos) {
        // Calculate exit position based on direction
        int exitDistance = 35; // Slightly larger than intersection radius

        // Set X position based on road with lane offset
        if (direction == CarDirection.NORTH || direction == CarDirection.SOUTH) {
            position.setX(road.getX1() + laneOffset);

            // Set Y position to be outside intersection
            if (direction == CarDirection.NORTH) {
                position.setY(intersectionPos.getY() - exitDistance);
            } else { // SOUTH
                position.setY(intersectionPos.getY() + exitDistance);
            }
        }
        else { // EAST or WEST
            position.setY(road.getY1() + laneOffset);

            // Set X position to be outside intersection
            if (direction == CarDirection.WEST) {
                position.setX(intersectionPos.getX() - exitDistance);
            } else { // EAST
                position.setX(intersectionPos.getX() + exitDistance);
            }
        }

        exitIntersection();
    }

    private void emergencyExitIntersection(Position intersectionPos) {
        // Use hard-coded map knowledge to find valid exit position
        double[] xCoords = {325.0, 900.0};
        double[] yCoords = {225.0, 525.0};

        // Find closest matching road coordinates
        double bestX = getClosestValue(intersectionPos.getX(), xCoords);
        double bestY = getClosestValue(intersectionPos.getY(), yCoords);

        // Calculate exit position based on direction and map coordinates
        int exitDistance = 35;

        // Move car to appropriate position on grid based on direction
        if (direction == CarDirection.NORTH || direction == CarDirection.SOUTH) {
            position.setX(bestX + laneOffset);
            position.setY(direction == CarDirection.NORTH ?
                    intersectionPos.getY() - exitDistance :
                    intersectionPos.getY() + exitDistance);
        } else {
            position.setY(bestY + laneOffset);
            position.setX(direction == CarDirection.WEST ?
                    intersectionPos.getX() - exitDistance :
                    intersectionPos.getX() + exitDistance);
        }

        exitIntersection();
    }

    private double getClosestValue(double value, double[] options) {
        double closestVal = options[0];
        double minDiff = Math.abs(value - closestVal);

        for (double option : options) {
            double diff = Math.abs(value - option);
            if (diff < minDiff) {
                minDiff = diff;
                closestVal = option;
            }
        }

        return closestVal;
    }

    private void checkIntersectionEntry() {
        if (currentIntersection == null) {
            for (Intersection intersection : intersections) {
                if (isInIntersection(intersection)) {
                    currentIntersection = intersection;
                    System.out.println("Car entered intersection at " +
                            intersection.getPosition().getX() + "," +
                            intersection.getPosition().getY());
                    break;
                }
            }
        }
    }

    private void moveInDirection() {
        switch (direction) {
            case NORTH:
                position.setY(position.getY() - speed);
                break;
            case SOUTH:
                position.setY(position.getY() + speed);
                break;
            case EAST:
                position.setX(position.getX() + speed);
                break;
            case WEST:
                position.setX(position.getX() - speed);
                break;
        }
    }


    private boolean hasCrossedTrafficLight() {
        if (trafficLight == null) return false;

        Position lightPos = trafficLight.getPosition();

        // Check if we've passed the light based on direction
        switch (direction) {
            case NORTH:
                return position.getY() < lightPos.getY();
            case SOUTH:
                return position.getY() > lightPos.getY();
            case EAST:
                return position.getX() > lightPos.getX();
            case WEST:
                return position.getX() < lightPos.getX();
            default:
                return false;
        }
    }

    private void updateIntersectionStatus() {
        // Implementation retained from original code
        // This will be called by other methods
    }

    private void exitIntersection() {
        currentIntersection = null;
        turningInProgress = false;
        targetDirection = null;

        // Set a cooldown period after exiting intersection
        isInCooldown = true;
        cooldownEndTime = System.currentTimeMillis() + COOLDOWN_DURATION;
    }

    private void adjustPositionForLane() {
        // Implementation retained from original code
    }

    private List<Road> getRoadsFromSimulation() {
        return simulation != null ? simulation.getRoads() : new ArrayList<>();
    }

    private boolean isInIntersection(Intersection intersection) {
        Position intPos = intersection.getPosition();
        double distance = Math.sqrt(
                Math.pow(position.getX() - intPos.getX(), 2) +
                        Math.pow(position.getY() - intPos.getY(), 2)
        );
        return distance < 30; // Intersection radius
    }


    private boolean shouldStopForTrafficLight() {
        // If we're already in an intersection, don't check traffic lights
        if (currentIntersection != null) return false;

        // Check for relevant traffic lights in our path
        for (Intersection intersection : intersections) {
            // Only check if we're approaching this intersection
            if (isApproachingIntersection(intersection)) {
                // Get the traffic light facing our direction
                TrafficLight relevantLight = getRelevantTrafficLight(intersection);

                if (relevantLight != null && relevantLight.getLightState() == TrafficLight.LightState.RED) {
                    double distance = distanceTo(relevantLight.getPosition());

                    // Stop if we're within detection radius but not too close
                    if (distance < detectionRadius && distance > 5) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    private boolean isApproachingIntersection(Intersection intersection) {
        Position intPos = intersection.getPosition();
        double distance = distanceTo(intPos);

        // Check if we're close enough and heading toward it
        if (distance > 100) return false;

        switch (direction) {
            case NORTH: return position.getY() > intPos.getY();
            case SOUTH: return position.getY() < intPos.getY();
            case EAST: return position.getX() < intPos.getX();
            case WEST: return position.getX() > intPos.getX();
            default: return false;
        }
    }

    private TrafficLight getRelevantTrafficLight(Intersection intersection) {
        // The traffic light we need to check is facing opposite our direction
        TrafficLight.Direction lightDirection;

        switch (direction) {
            case NORTH: lightDirection = TrafficLight.Direction.SOUTH; break;
            case SOUTH: lightDirection = TrafficLight.Direction.NORTH; break;
            case EAST: lightDirection = TrafficLight.Direction.WEST; break;
            case WEST: lightDirection = TrafficLight.Direction.EAST; break;
            default: return null;
        }

        return intersection.getTrafficLight(lightDirection);
    }


    private CarDirection getOppositeDirection() {
        switch (direction) {
            case NORTH: return CarDirection.SOUTH;
            case SOUTH: return CarDirection.NORTH;
            case EAST: return CarDirection.WEST;
            case WEST: return CarDirection.EAST;
            default: return direction;
        }
    }

    private boolean shouldStopForNearbyVehicle() {
        if (nearbyVehicles == null || nearbyVehicles.isEmpty()) {
            return false;
        }

        for (Car otherCar : nearbyVehicles) {
            if (otherCar == this) continue; // Skip self

            // Only consider cars on the same road and in same direction
            if (currentRoad != otherCar.currentRoad || direction != otherCar.getDirection()) {
                continue;
            }

            // Check if car is ahead of us based on direction
            Position otherPos = otherCar.getPosition();
            boolean isAhead = false;

            switch (direction) {
                case NORTH:
                    isAhead = otherPos.getY() < position.getY();
                    break;
                case SOUTH:
                    isAhead = otherPos.getY() > position.getY();
                    break;
                case EAST:
                    isAhead = otherPos.getX() > position.getX();
                    break;
                case WEST:
                    isAhead = otherPos.getX() < position.getX();
                    break;
            }

            if (isAhead) {
                double distance = distanceTo(otherPos);
                if (distance < minSafeDistance) {
                    return true; // Stop if too close
                }
            }
        }

        return false;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    private double distanceTo(Position target) {
        return Math.sqrt(
                Math.pow(position.getX() - target.getX(), 2) +
                        Math.pow(position.getY() - target.getY(), 2)
        );
    }


    public boolean checkCollision(TrafficLight trafficLight) {
        if (trafficLight == null) return false;

        // Calculate distance between car and traffic light
        double distance = distanceTo(trafficLight.getPosition());

        // Use ConfigLoader to get the traffic light size instead of direct access
        int trafficLightSize = ConfigLoader.getInstance().getTrafficLightSize();

        // Check if distance is less than sum of radiuses
        return distance < ((double) size / 2 + (double) trafficLightSize / 2);
    }



    private double distanceBetween(Position p1, Position p2) {
        return Math.sqrt(
                Math.pow(p1.getX() - p2.getX(), 2) +
                        Math.pow(p1.getY() - p2.getY(), 2)
        );
    }

}