package com.oblig.obj_oblig_2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import javafx.scene.paint.Color;

public class Car extends Thread {
    // Add this field to store the car's color
    private Color color;
    
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
    private Position lastRecordedPosition;
    private long lastMovementTime;
    private static final long STUCK_THRESHOLD_MS = 5000; // 5 seconds
    
    // Add Random object for all methods that need randomness
    private final Random random = new Random();
    
    // Path planning for turning
    private List<Position> turningPath;
    private int currentPathIndex;
    private boolean isFollowingPath;

    // Add a new field to store the original speed
    private double originalSpeed;

    // Add a variable to track when the car entered an intersection
    private long intersectionEntryTime;
    private static final long MAX_INTERSECTION_TIME = 5000; // 5 seconds max in intersection

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
        this.color = generateRandomColor();
    }

    // Fix the constructor to initialize originalSpeed
    public Car(Position position, double speed, CarDirection direction, TrafficLight trafficLight) {
        this.position = position;
        this.speed = speed;
        this.originalSpeed = speed; // Add this line to initialize originalSpeed
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
        this.color = generateRandomColor();
    }

    // Add getter for color
    public Color getColor() {
        return color;
    }
    
    // Add setter for color
    public void setColor(Color color) {
        this.color = color;
    }
    
    // Method to generate random colors
    private Color generateRandomColor() {
        Random random = new Random();
        // Generate colors excluding very light or very dark ones
        return Color.rgb(
            50 + random.nextInt(180),  // Red: 50-229
            50 + random.nextInt(180),  // Green: 50-229
            50 + random.nextInt(180)   // Blue: 50-229
        );
    }

    // Fix the calculateLaneOffset method to be consistent
    private int calculateLaneOffset() {
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        int laneWidth = roadWidth / 2;

        switch (direction) {
            // Right-side driving system
            case NORTH: return laneWidth/2;    // RIGHT side of vertical road (positive X offset)
            case SOUTH: return -laneWidth/2;   // LEFT side of vertical road (negative X offset)
            case EAST: return laneWidth/2;     // BOTTOM side of horizontal road (positive Y offset)
            case WEST: return -laneWidth/2;    // TOP side of horizontal road (negative Y offset)
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

    public Road getCurrentRoad() {
        return currentRoad;
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
        if (lastRecordedPosition == null) {
            lastRecordedPosition = new Position(position.getX(), position.getY());
            lastMovementTime = System.currentTimeMillis();
        }
        synchronized(positionLock) {
            // Check for collisions first, but use new behavior
            if (shouldStopForNearbyVehicle()) {
                // Add small random movement to try to resolve deadlocks
                if (random.nextInt(100) < 5) { // 5% chance to jiggle
                    double jiggle = 0.2;
                    position.setX(position.getX() + (random.nextDouble() * jiggle * 2) - jiggle);
                    position.setY(position.getY() + (random.nextDouble() * jiggle * 2) - jiggle);
                }
                return;
            }

            // Handle intersection logic - completely separate from normal movement
            if (currentIntersection != null) {
                // Add this at the beginning of handleIntersectionMovement
                // Check for too long in intersection
                if (System.currentTimeMillis() - intersectionEntryTime > 5000) {
                    System.out.println("Car stuck in intersection for too long - forcing emergency exit");
                    forceExitIntersection();
                    return;
                }
                
                handleIntersectionMovement();
                return; // Don't proceed with normal road logic
            }

            // Normal road handling (outside of intersections)
            if (isInCooldown) {
                if (System.currentTimeMillis() < cooldownEndTime) {
                    moveInDirection();
                    adjustPositionForLane(); // Add this line to maintain lane positioning
                    return;
                } else {
                    isInCooldown = false;
                    // Immediately restore speed when cooldown ends
                    speed = originalSpeed;
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
            adjustPositionForLane(); // Add this line to maintain lane positioning

            // After normal movement, ensure we restore speed much more aggressively
            if (speed < originalSpeed) {
                // Always restore speed by 5% each frame (no random chance)
                speed = Math.min(originalSpeed, speed * 1.05);
                
                // If speed is severely reduced, boost it more aggressively
                if (speed < originalSpeed * 0.5) {
                    speed = Math.max(speed, originalSpeed * 0.5);
                }
            }
            
            // Try to recover if car might be stuck
            if (speed < originalSpeed * 0.3) {
                recoverFromStuck();
            }

            double distance = Math.sqrt(
                    Math.pow(position.getX() - lastRecordedPosition.getX(), 2) +
                            Math.pow(position.getY() - lastRecordedPosition.getY(), 2));

            if (distance > 5.0) {
                // Car has moved, update the reference position and time
                lastRecordedPosition = new Position(position.getX(), position.getY());
                lastMovementTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastMovementTime > STUCK_THRESHOLD_MS) {
                // Car is stuck, try to recover
                recoverFromStuck();
                // Update time to avoid immediate re-detection
                lastMovementTime = System.currentTimeMillis();
            }

        }
    }

    private void handleIntersectionMovement() {
        // check for nearby cars and stop if needed
        if (shouldStopForNearbyVehicle()) {
            return; // Don't move if hit another car
        }
        
        Position intersectionPos = currentIntersection.getPosition();

        // Calculate distance to intersection center
        double distanceToCenter = Math.sqrt(
                Math.pow(position.getX() - intersectionPos.getX(), 2) +
                        Math.pow(position.getY() - intersectionPos.getY(), 2)
        );

        // Generate turning path on first entry into intersection
        if (!turningInProgress && !isFollowingPath) {
            turningInProgress = true;
            
            // Choose a direction, straight or turning
            List<CarDirection> validDirections = new ArrayList<>();
            
            // Add going straight as a valid option
            validDirections.add(direction);
            
            // Add turning options (not opposite direction)
            for (CarDirection dir : CarDirection.values()) {
                if (dir != direction && dir != getOppositeDirection(direction)) {
                    validDirections.add(dir);
                }
            }

            if (!validDirections.isEmpty()) {
                Random random = new Random();
                
                // higher chance to go straight
                double goStraightProbability = 0.6; // 60% chance to go straight
                
                if (random.nextDouble() < goStraightProbability && validDirections.contains(direction)) {
                    // Go straight
                    targetDirection = direction;
                    System.out.println("Car continuing straight through intersection in direction: " + direction);
                    
                    //  simplified path for going straight
                    generateStraightPath(direction, intersectionPos);
                } else {
                    // Choose a random turning direction (exclude current direction)
                    List<CarDirection> turningOptions = new ArrayList<>(validDirections);
                    turningOptions.remove(direction); // Remove straight option
                    
                    if (!turningOptions.isEmpty()) {
                        targetDirection = turningOptions.get(random.nextInt(turningOptions.size()));
                        System.out.println("Preparing to turn from " + direction + " to " + targetDirection);
                        
                        // Generate turning path. directly from entry to exit point
                        generateDirectTurningPath(direction, targetDirection, intersectionPos);
                    } else {
                        // Fallback. continue straight
                        targetDirection = direction;
                        generateStraightPath(direction, intersectionPos);
                    }
                }
                
                isFollowingPath = true;
                currentPathIndex = 0;
                
                // Adjust speed based on, going straight or turning
                if (targetDirection == direction) {
                    // Maintain normal speed when going straight
                    speed = ConfigLoader.getInstance().getCarSpeed();
                } else {
                    // Slow down for turns
                    speed = ConfigLoader.getInstance().getTurningSpeed();
                }
            } else {
                // Fallback. continue straight
                targetDirection = direction;
                exitIntersection();
            }
            return;
        }

        // Follow the turning path
        if (isFollowingPath && currentPathIndex < turningPath.size()) {
            // Move to the next point on the path
            Position nextPoint = turningPath.get(currentPathIndex);
            
            // Calculate direction to next point
            double dx = nextPoint.getX() - position.getX();
            double dy = nextPoint.getY() - position.getY();
            double distanceToPoint = Math.sqrt(dx * dx + dy * dy);
            
            if (distanceToPoint < speed) {
                // reached or can surpass this point in this step
                position.setX(nextPoint.getX());
                position.setY(nextPoint.getY());
                currentPathIndex++;
            } else {
                // Move toward the point at current speed
                position.setX(position.getX() + (dx / distanceToPoint) * speed);
                position.setY(position.getY() + (dy / distanceToPoint) * speed);
            }
            
            // Check if reached the end of the path
            if (currentPathIndex >= turningPath.size()) {
                // Find matching road, finalize the turn
                setDirection(targetDirection);
                Road bestRoad = findMatchingRoad(targetDirection, intersectionPos);
                if (bestRoad != null) {
                    currentRoad = bestRoad;
                    adjustFinalPositionOnRoad(bestRoad);
                } else {
                    emergencyExitIntersection(intersectionPos);
                }
                
                // Reset path following and restore ORIGINAL speed (not default)
                isFollowingPath = false;
                turningPath.clear();
                speed = originalSpeed;
                exitIntersection();
            }
        }
    }
    
    // generates a straight path through intersection
    private void generateStraightPath(CarDirection travelDirection, Position center) {
        turningPath.clear();
        
        int intersectionRadius = ConfigLoader.getInstance().getIntersectionRadius();
        
        // Calculate entry and exit points of the intersection
        Position entryPoint = calculateIntersectionEntryPoint(center, travelDirection, intersectionRadius);
        Position exitPoint = calculateIntersectionExitPoint(center, travelDirection, intersectionRadius);
        
        // For going straight, entry, exit, and maybe one point in between
        turningPath.add(new Position(position.getX(), position.getY()));
        turningPath.add(exitPoint);
    }

    // Generates a direct turning path without going to center
    private void generateDirectTurningPath(CarDirection fromDirection, CarDirection toDirection, Position center) {
        turningPath.clear();
        
        int intersectionRadius = ConfigLoader.getInstance().getIntersectionRadius();
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        
        // Calculate entry and exit points of the intersection
        Position entryPoint = calculateIntersectionEntryPoint(center, fromDirection, intersectionRadius);
        Position exitPoint = calculateIntersectionExitPoint(center, toDirection, intersectionRadius);
        
        // Add current position as first point
        turningPath.add(new Position(position.getX(), position.getY()));
        
        // Determine control points without going to center
        Position controlPoint1, controlPoint2;
        
        // Create control points for natural arc
        controlPoint1 = createControlPoint(entryPoint, fromDirection, intersectionRadius * 0.6);
        controlPoint2 = createControlPoint(exitPoint, getOppositeDirection(toDirection), intersectionRadius * 0.6);
        
        // Generate points along the curve
        int numPoints = ConfigLoader.getInstance().getTurningPathPoints(); 
        for (int i = 1; i <= numPoints; i++) {
            double t = i / (double) numPoints;
            
            // Calculate point along curve
            double x = bezierPoint(t, entryPoint.getX(), controlPoint1.getX(), 
                                 controlPoint2.getX(), exitPoint.getX());
            double y = bezierPoint(t, entryPoint.getY(), controlPoint1.getY(), 
                                 controlPoint2.getY(), exitPoint.getY());
            
            // Add point to path
            turningPath.add(new Position(x, y));
        }
        
        // Add exit point, final destination
        turningPath.add(exitPoint);
    }

    // Bezier curve calculation
    private Position createControlPoint(Position startPoint, CarDirection direction, double distance) {
        double x = startPoint.getX();
        double y = startPoint.getY();
        
        // Extend in the specified direction
        switch (direction) {
            case NORTH: return new Position(x, y - distance);
            case SOUTH: return new Position(x, y + distance);
            case EAST: return new Position(x + distance, y);
            case WEST: return new Position(x - distance, y);
            default: return new Position(x, y);
        }
    }

    //calcuclate entry point
    private Position calculateIntersectionEntryPoint(Position center, CarDirection direction, int radius) {
        // Adjust entry point based on lane offset, to keep cars in their lane
        double adjustedRadius = radius * 0.9; //
        
        switch (direction) {
            case NORTH: return new Position(center.getX() + laneOffset, center.getY() + adjustedRadius);
            case SOUTH: return new Position(center.getX() + laneOffset, center.getY() - adjustedRadius);
            case EAST: return new Position(center.getX() - adjustedRadius, center.getY() + laneOffset);
            case WEST: return new Position(center.getX() + adjustedRadius, center.getY() + laneOffset);
            default: return new Position(center.getX(), center.getY());
        }
    }

    // Calculate where the car should exit, taking into account proper lane placement
    private Position calculateIntersectionExitPoint(Position center, CarDirection direction, int radius) {
        double adjustedRadius = radius * 0.9;
        int laneOffset = calculateLaneOffset(direction);
        
        switch (direction) {
            case NORTH: return new Position(center.getX() + laneOffset, center.getY() - adjustedRadius);
            case SOUTH: return new Position(center.getX() + laneOffset, center.getY() + adjustedRadius);
            case EAST: return new Position(center.getX() + adjustedRadius, center.getY() + laneOffset);
            case WEST: return new Position(center.getX() - adjustedRadius, center.getY() + laneOffset);
            default: return new Position(center.getX(), center.getY());
        }
    }
    
    //laneOffset calculation
    private int calculateLaneOffset(CarDirection dir) {
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        int laneWidth = roadWidth / 2;

        switch (dir) {
            // Right-side driving system (MUST MATCH the method above)
            case NORTH: return laneWidth/2;    // RIGHT side of vertical road (positive X offset)
            case SOUTH: return -laneWidth/2;   // LEFT side of vertical road (negative X offset)
            case EAST: return laneWidth/2;     // BOTTOM side of horizontal road
            case WEST: return -laneWidth/2;    // TOP side of horizontal road
            default: return 0;
        }
    }

    // Place the car correctly on the destination road with proper lane offset
    // This is called after exiting the intersection
    private void adjustFinalPositionOnRoad(Road road) {

        if (targetDirection == CarDirection.NORTH || targetDirection == CarDirection.SOUTH) {
            position.setX(road.getX1() + laneOffset);
        } else { // EAST or WEST
            position.setY(road.getY1() + laneOffset);
        }
    }

    // this method does not seem to be in use anymore, but we keep it for reference and we are scared of breaking something vital
    // this method was used to find the best road for the car to exit the intersection
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
            position.setY(road.getY1() + laneOffset);  // Use consistent lane offset
    
            // Set X position to be outside intersection
            if (direction == CarDirection.WEST) {
                position.setX(intersectionPos.getX() - exitDistance);
            } else { // EAST
                position.setX(intersectionPos.getX() + exitDistance);
            }
        }
    
        exitIntersection();
    }

    // emergency exit logic, if cars get stuck in intersection
    private void emergencyExitIntersection(Position intersectionPos) {
        // Log this emergency situation
        System.out.println("Emergency exit triggered at: " + intersectionPos.getX() + ", " + intersectionPos.getY());

        // Force refresh of car's current road
        Road newRoad = findAnyValidRoad(direction);
        if (newRoad != null) {
            currentRoad = newRoad;
            System.out.println("Found recovery road");
        } else {
            System.out.println("Failed to find recovery road - car might disappear");
        }
    }

    // method to find any valid road in the given direction
    private Road findAnyValidRoad(CarDirection direction) {
        List<Road> allRoads = getRoadsFromSimulation();
        for (Road road : allRoads) {
            if ((direction == CarDirection.NORTH || direction == CarDirection.SOUTH) && road.isVertical()) {
                return road;
            } else if ((direction == CarDirection.EAST || direction == CarDirection.WEST) && road.isHorizontal()) {
                return road;
            }
        }
        return null;
    }

    //initialize the entry time
    private void checkIntersectionEntry() {
        if (currentIntersection == null) {
            for (Intersection intersection : intersections) {
                if (isInIntersection(intersection)) {
                    currentIntersection = intersection;
                    // Set the entry time when car enters an intersection
                    intersectionEntryTime = System.currentTimeMillis();
                    System.out.println("Car entered intersection at " +
                            intersection.getPosition().getX() + "," +
                            intersection.getPosition().getY());
                    break;
                }
            }
        }
    }

    private void moveInDirection() {
        synchronized(positionLock) {
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

    // calculates if car can exit intersection, forces car out if stuck
    private boolean isSafeToExitIntersection() {
        // More aggressive timeout - force exit after just 2 seconds
        if (System.currentTimeMillis() - intersectionEntryTime > 2000) {
            System.out.println("Forcing exit from intersection due to TIMEOUT");
            return true;
        }
        
        // Check if we have a valid target direction
        double exitSafetyRadius = minSafeDistance * 0.5;
        
        int blockedExitCount = 0;
        
        for (Car otherCar : nearbyVehicles) {
            if (otherCar == this) continue;
            
            // Only check cars in our target direction
            if (targetDirection != null && otherCar.getDirection() != targetDirection) continue;
            
            double distance = distanceTo(otherCar.getPosition());
            if (distance < exitSafetyRadius) {
                blockedExitCount++;
            }
        }
        
        //single blocking car should be ignored after 1.5 seconds
        if (blockedExitCount > 0 && System.currentTimeMillis() - intersectionEntryTime > 1500) {
            System.out.println("Forcing exit despite " + blockedExitCount + " blocking cars");
            return true;
        }
        
        return blockedExitCount == 0;
    }

    // super-emergency exit mechanism
    private void forceExitIntersection() {
        System.out.println("EMERGENCY FORCE EXIT");
        
        // Ensure there is a valid direction
        if (targetDirection == null) {
            targetDirection = direction;
        }
        
        // Set this direction
        setDirection(targetDirection);
        
        // Force position to be outside the intersection
        Position intersectionPos = currentIntersection.getPosition();
        double forcedDistance = 50; // Force 50 pixels away from intersection
        
        // Reposition the car outside the intersection in the direction of travel
        switch (targetDirection) {
            case NORTH:
                position.setX(intersectionPos.getX() + laneOffset);
                position.setY(intersectionPos.getY() - forcedDistance);
                break;
            case SOUTH:
                position.setX(intersectionPos.getX() + laneOffset);
                position.setY(intersectionPos.getY() + forcedDistance);
                break;
            case EAST:
                position.setX(intersectionPos.getX() + forcedDistance);
                position.setY(intersectionPos.getY() + laneOffset);
                break;
            case WEST:
                position.setX(intersectionPos.getX() - forcedDistance);
                position.setY(intersectionPos.getY() + laneOffset);
                break;
        }
        
        // Try to find a valid road, but don't worry if we can't
        Road bestRoad = findMatchingRoad(targetDirection, intersectionPos);
        if (bestRoad != null) {
            currentRoad = bestRoad;
        }
        
        // Reset all intersection-related states
        currentIntersection = null;
        turningInProgress = false;
        isFollowingPath = false;
        targetDirection = null;
        speed = originalSpeed;
        
        // cooldown period
        isInCooldown = true;
        cooldownEndTime = System.currentTimeMillis() + COOLDOWN_DURATION;
    }

    // exit intersection logic
    private void exitIntersection() {
        // safety check
        if (!isSafeToExitIntersection()) {
            // Wait before trying to exit
            return;
        }
        
        // Make sure direction set before exiting
        if (targetDirection != null) {
            setDirection(targetDirection);
        }
        
        // Try to ensure we're on a valid road
        if (currentRoad == null) {
            Road bestRoad = findMatchingRoad(direction, currentIntersection.getPosition());
            if (bestRoad != null) {
                currentRoad = bestRoad;
                adjustFinalPositionOnRoad(bestRoad);
            }
        }
        
        currentIntersection = null;
        turningInProgress = false;
        targetDirection = null;

        // cooldown period after exiting intersection
        isInCooldown = true;
        cooldownEndTime = System.currentTimeMillis() + COOLDOWN_DURATION;
        
        // Make sure speed is restored to original
        speed = originalSpeed;
    }


    private void adjustPositionForLane() {
        if (currentIntersection != null || currentRoad == null) return;
        
        // Calculate precise lane offset based on direction
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        int laneWidth = roadWidth / 2;
        int centerOffset = laneWidth / 2; // Position in center of lane
        
        // Apply lane offset based on direction and road orientation
        if (currentRoad.isHorizontal()) {
            // Horizontal road - adjust Y position
            double targetY;
            if (direction == CarDirection.EAST) {
                // East - use bottom lane (positive offset)
                targetY = currentRoad.getY1() + centerOffset;
            } else {
                // West - use top lane (negative offset)  
                targetY = currentRoad.getY1() - centerOffset;
            }
            // More assertive adjustment 0.9/0.1
            position.setY(position.getY() * 0.8 + targetY * 0.2);
        } else {
            // Vertical road - adjust X position
            double targetX;
            if (direction == CarDirection.SOUTH) {
                // South - use LEFT lane (negative offset)
                targetX = currentRoad.getX1() - centerOffset;
            } else {
                // North - use RIGHT lane (positive offset)
                targetX = currentRoad.getX1() + centerOffset;
            }
            // More assertive adjustment 0.9/0.1
            position.setX(position.getX() * 0.8 + targetX * 0.2);
        }
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
        
        // If we're entering an intersection, store the current speed
        if (distance < 30 && currentIntersection == null) {
            originalSpeed = speed;
        }
        
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
                        // Notify the intersection that a car is waiting
                        // Convert car direction to traffic light direction
                        TrafficLight.Direction waitDirection;
                        switch (direction) {
                            case NORTH: waitDirection = TrafficLight.Direction.SOUTH; break;
                            case SOUTH: waitDirection = TrafficLight.Direction.NORTH; break;
                            case EAST: waitDirection = TrafficLight.Direction.WEST; break;
                            case WEST: waitDirection = TrafficLight.Direction.EAST; break;
                            default: waitDirection = TrafficLight.Direction.NORTH;
                        }
                        
                        // Register this car as waiting
                        intersection.addWaitingCar(waitDirection, this);
                        
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

    // stop for nearby vehicles
    private boolean shouldStopForNearbyVehicle() {
        if (nearbyVehicles == null || nearbyVehicles.isEmpty()) {
            return false;
        }

        boolean shouldSlow = false;
        double slowDownFactor = 1.0;
        
        for (Car otherCar : nearbyVehicles) {
            if (otherCar == this) continue;

            // Only consider cars that are directly ahead
            Position otherPos = otherCar.getPosition();
            boolean isAhead = false;
            
            // more precise angles to check only cars directly ahead
            switch (direction) {
                case NORTH:
                    isAhead = otherPos.getY() < position.getY() && 
                              Math.abs(otherPos.getX() - position.getX()) < size * 1.2;
                    break;
                case SOUTH:
                    isAhead = otherPos.getY() > position.getY() && 
                              Math.abs(otherPos.getX() - position.getX()) < size * 1.2;
                    break;
                case EAST:
                    isAhead = otherPos.getX() > position.getX() && 
                              Math.abs(otherPos.getY() - position.getY()) < size * 1.2;
                    break;
                case WEST:
                    isAhead = otherPos.getX() < position.getX() && 
                              Math.abs(otherPos.getY() - position.getY()) < size * 1.2;
                    break;
            }

            if (isAhead) {
                double distance = distanceTo(otherPos);
                if (distance < size + 2) {  // Only emergency brake when actually touching
                    // Slow dramatically but don't completely stop
                    speed *= 0.3;
                    return false; // Don't stop completely
                } else if (distance < minSafeDistance) {
                    // Slow down based on distance, but less aggressively
                    shouldSlow = true;
                    double factor = distance / minSafeDistance;
                    // Scale the factor to be less harsh (0.6-1.0 instead of 0-1.0)
                    factor = 0.6 + factor * 0.4;
                    slowDownFactor = Math.min(slowDownFactor, factor);
                }
            }
        }
        
        // Apply more gentle slowing
        if (shouldSlow) {
            // Less aggressive slowdown (0.9 instead of 0.8)
            speed = speed * slowDownFactor * 0.9;
            return false;
        }

        return false; // Never completely stop
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

    public void setSpeed(double speed) {
        this.speed = speed;
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

    // Add a method to help "jiggle" cars out of stuck positions
    private void recoverFromStuck() {
        double recoveryDistance = 1.0; // Was 0.5
        double angle = random.nextDouble() * 2 * Math.PI;
        
        position.setX(position.getX() + Math.cos(angle) * recoveryDistance);
        position.setY(position.getY() + Math.sin(angle) * recoveryDistance);
        
        // aggressive speed recovery
        speed = originalSpeed * 0.9; // for less aggressive recovery, 0.7-0.5
    }
}

