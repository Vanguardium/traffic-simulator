package com.oblig.obj_oblig_2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

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
    
    // Path planning for turning
    private List<Position> turningPath;
    private int currentPathIndex;
    private boolean isFollowingPath;

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
        this.turningPath = new ArrayList<>();
        this.currentPathIndex = 0;
        this.isFollowingPath = false;
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
        this.turningPath = new ArrayList<>();
        this.currentPathIndex = 0;
        this.isFollowingPath = false;
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

        // Step 1: Generate turning path on first entry into intersection
        if (!turningInProgress && !isFollowingPath) {
            turningInProgress = true;
            
            // Choose random direction (not current and not opposite)
            List<CarDirection> validDirections = new ArrayList<>();
            for (CarDirection dir : CarDirection.values()) {
                if (dir != direction && dir != getOppositeDirection(direction)) {
                    validDirections.add(dir);
                }
            }

            if (!validDirections.isEmpty()) {
                Random random = new Random();
                targetDirection = validDirections.get(random.nextInt(validDirections.size()));
                System.out.println("Preparing to turn from " + direction + " to " + targetDirection);
                
                // Generate turning path - directly from entry to exit point
                generateDirectTurningPath(direction, targetDirection, intersectionPos);
                isFollowingPath = true;
                currentPathIndex = 0;
                
                // Use a slower turning speed from config
                speed = ConfigLoader.getInstance().getTurningSpeed();
            } else {
                // Fallback - continue straight
                targetDirection = direction;
                exitIntersection();
            }
            return;
        }

        // Step 2: Follow the turning path
        if (isFollowingPath && currentPathIndex < turningPath.size()) {
            // Move to the next point on the path
            Position nextPoint = turningPath.get(currentPathIndex);
            
            // Calculate direction to next point
            double dx = nextPoint.getX() - position.getX();
            double dy = nextPoint.getY() - position.getY();
            double distanceToPoint = Math.sqrt(dx * dx + dy * dy);
            
            if (distanceToPoint < speed) {
                // We've reached or can surpass this point in this step
                position.setX(nextPoint.getX());
                position.setY(nextPoint.getY());
                currentPathIndex++;
            } else {
                // Move toward the point at our current speed
                position.setX(position.getX() + (dx / distanceToPoint) * speed);
                position.setY(position.getY() + (dy / distanceToPoint) * speed);
            }
            
            // Check if we've reached the end of the path
            if (currentPathIndex >= turningPath.size()) {
                // Find matching road and finalize the turn
                setDirection(targetDirection);
                Road bestRoad = findMatchingRoad(targetDirection, intersectionPos);
                if (bestRoad != null) {
                    currentRoad = bestRoad;
                    adjustFinalPositionOnRoad(bestRoad);
                } else {
                    emergencyExitIntersection(intersectionPos);
                }
                
                // Reset path following and restore normal speed
                isFollowingPath = false;
                turningPath.clear();
                speed = ConfigLoader.getInstance().getCarSpeed(); // Restore normal speed
                exitIntersection();
            }
            return;
        }
    }
    
    private void generateDirectTurningPath(CarDirection fromDirection, CarDirection toDirection, Position center) {
        turningPath.clear();
        
        int intersectionRadius = ConfigLoader.getInstance().getIntersectionRadius();
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        
        // Calculate entry and exit points of the intersection
        // These will be at the edges of the intersection, not in the center
        Position entryPoint = calculateIntersectionEntryPoint(center, fromDirection, intersectionRadius);
        Position exitPoint = calculateIntersectionExitPoint(center, toDirection, intersectionRadius);
        
        // Add current position as first point
        turningPath.add(new Position(position.getX(), position.getY()));
        
        // Determine control points for a smooth curve without going to center
        Position controlPoint1, controlPoint2;
        
        // For a smooth turn, control points should be positioned along the path
        // but shifted to create a natural arc without going through the center
        
        // First control point - from entry point in the entry direction
        controlPoint1 = createControlPoint(entryPoint, fromDirection, intersectionRadius * 0.6);
        
        // Second control point - from exit point in the opposite of exit direction
        controlPoint2 = createControlPoint(exitPoint, getOppositeDirection(toDirection), intersectionRadius * 0.6);
        
        // Generate points along the Bezier curve
        int numPoints = ConfigLoader.getInstance().getTurningPathPoints(); 
        for (int i = 1; i <= numPoints; i++) {
            double t = i / (double) numPoints;
            
            // Cubic Bezier formula: B(t) = (1-t)³P₀ + 3(1-t)²tP₁ + 3(1-t)t²P₂ + t³P₃
            double x = bezierPoint(t, entryPoint.getX(), controlPoint1.getX(), controlPoint2.getX(), exitPoint.getX());
            double y = bezierPoint(t, entryPoint.getY(), controlPoint1.getY(), controlPoint2.getY(), exitPoint.getY());
            
            turningPath.add(new Position(x, y));
        }
    }
    
    private Position createControlPoint(Position basePoint, CarDirection direction, double distance) {
        double x = basePoint.getX();
        double y = basePoint.getY();
        
        switch (direction) {
            case NORTH: return new Position(x, y - distance);
            case SOUTH: return new Position(x, y + distance);
            case EAST: return new Position(x + distance, y);
            case WEST: return new Position(x - distance, y);
            default: return new Position(x, y);
        }
    }
    
    private Position calculateIntersectionEntryPoint(Position center, CarDirection direction, int radius) {
        // Adjust entry point based on lane offset to keep cars in their proper lane
        double adjustedRadius = radius * 0.9; // Slightly inside the intersection edge
        
        switch (direction) {
            case NORTH: return new Position(center.getX() + laneOffset, center.getY() + adjustedRadius);
            case SOUTH: return new Position(center.getX() + laneOffset, center.getY() - adjustedRadius);
            case EAST: return new Position(center.getX() - adjustedRadius, center.getY() + laneOffset);
            case WEST: return new Position(center.getX() + adjustedRadius, center.getY() + laneOffset);
            default: return new Position(center.getX(), center.getY());
        }
    }
    
    private Position calculateIntersectionExitPoint(Position center, CarDirection direction, int radius) {
        // Calculate where the car should exit, taking into account proper lane placement
        double adjustedRadius = radius * 0.9; // Slightly inside the intersection edge
        int laneOffset = calculateLaneOffset(direction);
        
        switch (direction) {
            case NORTH: return new Position(center.getX() + laneOffset, center.getY() - adjustedRadius);
            case SOUTH: return new Position(center.getX() + laneOffset, center.getY() + adjustedRadius);
            case EAST: return new Position(center.getX() + adjustedRadius, center.getY() + laneOffset);
            case WEST: return new Position(center.getX() - adjustedRadius, center.getY() + laneOffset);
            default: return new Position(center.getX(), center.getY());
        }
    }
    
    // Calculate lane offset for a specific direction (helpful for exit point calculations)
    private int calculateLaneOffset(CarDirection dir) {
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        int laneWidth = roadWidth / 2;

        switch (dir) {
            case NORTH: return laneWidth/2;   // RIGHT side of road
            case SOUTH: return -laneWidth/2;  // LEFT side of road
            case EAST: return laneWidth/2;    // BOTTOM side of road
            case WEST: return -laneWidth/2;   // TOP side of road
            default: return 0;
        }
    }

    private void adjustFinalPositionOnRoad(Road road) {
        // Place the car correctly on the destination road with proper lane offset
        if (targetDirection == CarDirection.NORTH || targetDirection == CarDirection.SOUTH) {
            position.setX(road.getX1() + laneOffset);
        } else { // EAST or WEST
            position.setY(road.getY1() + laneOffset);
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

    private CarDirection getOppositeDirection(CarDirection dir) {
        switch (dir) {
            case NORTH: return CarDirection.SOUTH;
            case SOUTH: return CarDirection.NORTH;
            case EAST: return CarDirection.WEST;
            case WEST: return CarDirection.EAST;
            default: return dir;
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

    private double bezierPoint(double t, double p0, double p1, double p2, double p3) {
        double mt = 1 - t;
        return mt * mt * mt * p0 + 3 * mt * mt * t * p1 + 3 * mt * t * t * p2 + t * t * t * p3;
    }
    
    private Road findMatchingRoad(CarDirection direction, Position intersectionPos) {
        // Get all roads from simulation
        List<Road> allRoads = getRoadsFromSimulation();
        if (allRoads.isEmpty()) {
            return null; // No roads available
        }
        
        Road bestRoad = null;
        double bestDistance = Double.MAX_VALUE;
        
        // Find a road that matches the target direction and is closest to the intersection
        for (Road road : allRoads) {
            // For NORTH/SOUTH, we need a vertical road
            // For EAST/WEST, we need a horizontal road
            boolean isDirectionCompatible = false;
            
            if ((direction == CarDirection.NORTH || direction == CarDirection.SOUTH) && road.isVertical()) {
                isDirectionCompatible = true;
            } else if ((direction == CarDirection.EAST || direction == CarDirection.WEST) && road.isHorizontal()) {
                isDirectionCompatible = true;
            }
            
            if (isDirectionCompatible) {
                // Calculate how close this road passes to the intersection
                double distance;
                if (road.isHorizontal()) {
                    // For horizontal roads, check vertical distance
                    distance = Math.abs(road.getY1() - intersectionPos.getY());
                } else {
                    // For vertical roads, check horizontal distance
                    distance = Math.abs(road.getX1() - intersectionPos.getX());
                }
                
                // If this road is closer to the intersection than previous best match
                if (distance < bestDistance) {
                    bestRoad = road;
                    bestDistance = distance;
                }
            }
        }
        
        return bestRoad;
    }

}

