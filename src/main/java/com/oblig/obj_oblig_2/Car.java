package com.oblig.obj_oblig_2;

import java.util.ArrayList;
import java.util.List;

//Represents a car
public class Car extends Thread{
    private Position position;
    private double speed;
    private CarDirection direction;
    private Road currentRoad;
    private TrafficLight trafficLight;
    private List<Intersection> intersections;
    private int detectionRadius;
    private int laneOffset; // Added to track the lane offset
    private List<Car> nearbyVehicles;
    private double minSafeDistance = ConfigLoader.getInstance().getMinCarDistance();
    private int size;
    private Intersection currentIntersection; // Tracks the intersection the car is currently in
    private boolean hasPassedTrafficLight; // Tracks if the car has passed the traffic light
    private boolean isInCooldown; // Tracks if the car is in a cooldown period after exiting an intersection
    private long cooldownEndTime; // Tracks when the cooldown period ends
    private static final long COOLDOWN_DURATION = 2000; // Cooldown duration in milliseconds
    
    public Car () {
        this.position = new Position(0,0);
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
    
    public Car (Position position, double speed, CarDirection direction, TrafficLight trafficLight) {
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

    // Calculate lane offset based on direction to drive on right side
    private int calculateLaneOffset() {
        int roadWidth = ConfigLoader.getInstance().getRoadWidth();
        int laneWidth = roadWidth / 2;
        
        switch (direction) {
            case NORTH: return laneWidth/2;  // Right side of road when heading north is east (+X)
            case SOUTH: return -laneWidth/2; // Right side of road when heading south is west (-X)
            case EAST: return laneWidth/2;   // Right side of road when heading east is south (+Y)
            case WEST: return -laneWidth/2;  // Right side of road when heading west is north (-Y)
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
        this.laneOffset = calculateLaneOffset(); // Update lane offset when direction changes
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
    public void run(){
        while(!Thread.interrupted()){
            move();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void move() {
        // Always check for nearby vehicles to avoid collision, regardless of cooldown or intersection
        if (shouldStopForNearbyVehicle()) {
            return; // Safety first - stop if we're about to hit another car
        }
        
        // If the car is in an intersection, it should not stop for traffic lights
        if (currentIntersection == null) {
            // Check if the car is in a cooldown period
            if (isInCooldown) {
                if (System.currentTimeMillis() < cooldownEndTime) {
                    // During cooldown, the car should not stop for traffic lights
                    moveInDirection();
                    return;
                } else {
                    isInCooldown = false; // End cooldown
                }
            }
        
            // Stop if the car should stop for a traffic light
            if (!hasPassedTrafficLight && shouldStopForTrafficLight()) {
                return;
            }
        }
        
        // Check for potential collision with the traffic light
        if (trafficLight != null && checkCollision(trafficLight)) {
            return; // Stop the car to avoid collision
        }
        
        // Update the flag if the car has passed the traffic light
        if (trafficLight != null && hasCrossedTrafficLight()) {
            hasPassedTrafficLight = true;
        }
        
        // Check if we're entering or exiting an intersection
        updateIntersectionStatus();
        
        // Move the car in the direction it is facing
        moveInDirection();
    }

    private void moveInDirection() {
        switch (direction) {
            case NORTH:
                position.setY(position.getY() - speed);
                position.setX(currentRoad != null ? currentRoad.getX1() + laneOffset : position.getX());
                break;
            case SOUTH:
                position.setY(position.getY() + speed);
                position.setX(currentRoad != null ? currentRoad.getX1() + laneOffset : position.getX());
                break;
            case EAST:
                position.setX(position.getX() + speed);
                position.setY(currentRoad != null ? currentRoad.getY1() + laneOffset : position.getY());
                break;
            case WEST:
                position.setX(position.getX() - speed);
                position.setY(currentRoad != null ? currentRoad.getY1() + laneOffset : position.getY());
                break;
        }
    }
    

    private boolean hasCrossedTrafficLight() {
        if (trafficLight == null) {
            return false;
        }
    
        Position lightPosition = trafficLight.getPosition();
        switch (direction) {
            case NORTH:
                return position.getY() < lightPosition.getY();
            case SOUTH:
                return position.getY() > lightPosition.getY();
            case EAST:
                return position.getX() > lightPosition.getX();
            case WEST:
                return position.getX() < lightPosition.getX();
            default:
                return false;
        }
    }


    // Update whether the car is in an intersection or not
    private void updateIntersectionStatus() {
        // If already in an intersection, check if we've exited
        if (currentIntersection != null) {
            if (!isInIntersection(currentIntersection)) {
                currentIntersection = null; // Car has exited the intersection
                isInCooldown = true; // Start cooldown period
                cooldownEndTime = System.currentTimeMillis() + COOLDOWN_DURATION;
            }
            return;
        }
    
        // Check if we're entering any intersections
        for (Intersection intersection : intersections) {
            if (isInIntersection(intersection)) {
                currentIntersection = intersection;
                isInCooldown = false; // Reset cooldown when entering an intersection
                break;
            }
        }
    }
    
    // Check if the car is within an intersection's boundaries
    private boolean isInIntersection(Intersection intersection) {
        Position intersectionPos = intersection.getPosition();
        int intersectionSize = 60; // Should get from intersection or config
        
        // Check if car overlaps with intersection area
        return position.getX() >= intersectionPos.getX() - intersectionSize/2 &&
               position.getX() <= intersectionPos.getX() + intersectionSize/2 &&
               position.getY() >= intersectionPos.getY() - intersectionSize/2 &&
               position.getY() <= intersectionPos.getY() + intersectionSize/2;
    }

    private boolean shouldStopForTrafficLight() {
        // If the car is already in an intersection, it should not stop for its traffic light
        if (currentIntersection != null) {
            return false;
        }
    
        if (intersections == null || intersections.isEmpty()) {
            return false;
        }
    
        for (Intersection intersection : intersections) {
            Position intersectionPos = intersection.getPosition();
    
            // Calculate distance to intersection
            double dx = position.getX() - intersectionPos.getX();
            double dy = position.getY() - intersectionPos.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);
    
            // If we're close enough to check the traffic light
            if (distance <= detectionRadius) {
                // Get the relevant traffic light based on our direction
                TrafficLight relevantLight = intersection.getTrafficLight(convertToTrafficLightDirection());
    
                // If the light is red or yellow, stop
                if (relevantLight != null) {
                    TrafficLight.LightState lightState = relevantLight.getLightState();
                    if (lightState == TrafficLight.LightState.RED || 
                        lightState == TrafficLight.LightState.YELLOW) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private TrafficLight.Direction convertToTrafficLightDirection() {
        switch (direction) {
            case NORTH: return TrafficLight.Direction.NORTH;
            case SOUTH: return TrafficLight.Direction.SOUTH;
            case EAST: return TrafficLight.Direction.EAST;
            case WEST: return TrafficLight.Direction.WEST;
            default: return TrafficLight.Direction.NORTH;
        }
    }

    private boolean isAheadOf(Car otherCar) {
        // Check if the other car is ahead of this car based on direction
        switch (direction) {
            case NORTH:
                // When going north, a car with lower Y is ahead
                return otherCar.position.getY() < position.getY();
            case SOUTH:
                // When going south, a car with higher Y is ahead
                return otherCar.position.getY() > position.getY();
            case EAST:
                // When going east, a car with higher X is ahead
                return otherCar.position.getX() > position.getX();
            case WEST:
                // When going west, a car with lower X is ahead
                return otherCar.position.getX() < position.getX();
            default:
                return false;
        }
    }
    
    private double distanceBetween(Position pos1, Position pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        return Math.sqrt(dx*dx + dy*dy);
    }
    
    private TrafficLight.Direction getOppositeDirection() {
        switch (direction) {
            case NORTH: return TrafficLight.Direction.SOUTH;
            case SOUTH: return TrafficLight.Direction.NORTH;
            case EAST: return TrafficLight.Direction.WEST;
            case WEST: return TrafficLight.Direction.EAST;
            default: return TrafficLight.Direction.NORTH;
        }
    }

    public boolean checkCollision (TrafficLight trafficLight) {
        // Calculate distance between car and traffic light
        double dx = this.position.getX() - trafficLight.getPosition().getX();
        double dy = this.position.getY() - trafficLight.getPosition().getY();
        double distance = Math.sqrt(dx*dx + dy*dy);
        
        // Consider collision if distance is less than 10 units
        return distance < 10;
    }

    private boolean shouldStopForNearbyVehicle() {
        if (nearbyVehicles == null || nearbyVehicles.isEmpty()) {
            return false; // No nearby vehicles to check
        }
    
        for (Car otherCar : nearbyVehicles) {
            // Skip if it's the same car
            if (otherCar == this) {
                continue;
            }
    
            // Skip if the cars are on different roads
            if (otherCar.currentRoad != this.currentRoad) {
                continue;
            }
    
            // Check if cars are in the same lane by comparing their lane offsets
            boolean sameLane = false;
            if (direction == CarDirection.NORTH || direction == CarDirection.SOUTH) {
                // For vertical roads, check if X positions are close
                sameLane = Math.abs(otherCar.position.getX() - position.getX()) < 20;
            } else {
                // For horizontal roads, check if Y positions are close
                sameLane = Math.abs(otherCar.position.getY() - position.getY()) < 20;
            }
    
            if (!sameLane) {
                continue; // Skip if not in the same lane
            }
    
            // Calculate the distance between this car and the other car
            double distance = distanceBetween(this.position, otherCar.position);
    
            // Check if the other car is ahead and within the minimum safe distance
            if (isAheadOf(otherCar) && distance < minSafeDistance) {
                return true; // Stop to avoid collision
            }
        }
    
        return false; // No need to stop
    }

    public void updateNearbyVehicles(List<Car> allCars) {
        nearbyVehicles.clear();
        // Use a larger detection radius specifically for cars (double the traffic light detection radius)
        int carDetectionRadius = detectionRadius * 2; 
        
        for (Car otherCar : allCars) {
            if (otherCar != this) {
                double distance = distanceBetween(this.position, otherCar.position);
                if (distance <= carDetectionRadius) {
                    nearbyVehicles.add(otherCar);
                }
            }
        }
    }


    // ... existing getters and setters ...
}
