package com.oblig.obj_oblig_2;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

public class Intersection {
    private List<TrafficLight> trafficLights;
    private Map<TrafficLight.Direction, TrafficLight> lightMap;
    private Map<TrafficLight.Direction, Integer> waitingCars; // Track waiting cars per direction
    private Position position;
    private final int SIZE = ConfigLoader.getInstance().getIntersectionRadius() * 2;
    private int currentGreenLightIndex = 0;
    private long greenLightStartTime = 0;
    
    // Min/max duration for adaptive timing
    private final long MIN_GREEN_DURATION = ConfigLoader.getInstance().getMinGreenLightDuration();
    private final long MAX_GREEN_DURATION = ConfigLoader.getInstance().getMaxGreenLightDuration();
    private final long BASE_GREEN_DURATION = ConfigLoader.getInstance().getTrafficLightUpdateInterval();
    private final int CARS_PER_TIME_UNIT = ConfigLoader.getInstance().getCarsPerTimeUnit();
    
    private long greenLightDuration = BASE_GREEN_DURATION;
    
    // Add a random offset to make intersections change at different times
    private Random random = new Random();
    private long randomOffset;
    
    // Add fields to track cars that are already counted
    private Map<TrafficLight.Direction, Set<Car>> countedCars = new HashMap<>();

    public Intersection(double x, double y) {
        this.position = new Position(x, y);
        this.trafficLights = new ArrayList<>();
        this.lightMap = new HashMap<>();
        this.waitingCars = new HashMap<>();
        
        // Give each intersection a random timing offset (0-5 seconds)
        this.randomOffset = random.nextInt(5000);
    }

    public void setupTrafficLights() {
        // Create traffic lights in all four directions
        TrafficLight northLight = new TrafficLight(position, TrafficLight.Direction.NORTH);
        TrafficLight eastLight = new TrafficLight(position, TrafficLight.Direction.EAST);
        TrafficLight southLight = new TrafficLight(position, TrafficLight.Direction.SOUTH);
        TrafficLight westLight = new TrafficLight(position, TrafficLight.Direction.WEST);

        trafficLights.add(northLight);
        trafficLights.add(eastLight);
        trafficLights.add(southLight);
        trafficLights.add(westLight);

        // Map the lights by direction for easy access
        lightMap.put(TrafficLight.Direction.NORTH, northLight);
        lightMap.put(TrafficLight.Direction.EAST, eastLight);
        lightMap.put(TrafficLight.Direction.SOUTH, southLight);
        lightMap.put(TrafficLight.Direction.WEST, westLight);

        // Initialize waiting cars counts
        waitingCars.put(TrafficLight.Direction.NORTH, 0);
        waitingCars.put(TrafficLight.Direction.EAST, 0);
        waitingCars.put(TrafficLight.Direction.SOUTH, 0);
        waitingCars.put(TrafficLight.Direction.WEST, 0);
        
        // Initialize the sets for tracked cars
        countedCars.put(TrafficLight.Direction.NORTH, new HashSet<>());
        countedCars.put(TrafficLight.Direction.EAST, new HashSet<>());
        countedCars.put(TrafficLight.Direction.SOUTH, new HashSet<>());
        countedCars.put(TrafficLight.Direction.WEST, new HashSet<>());

        // Initialize with random lights being green
        // 50% chance to start with North/South green, 50% East/West
        if (random.nextBoolean()) {
            trafficLights.get(0).setState(TrafficLight.LightState.GREEN); // North
            trafficLights.get(2).setState(TrafficLight.LightState.GREEN); // South
            currentGreenLightIndex = 0;
        } else {
            trafficLights.get(1).setState(TrafficLight.LightState.GREEN); // East
            trafficLights.get(3).setState(TrafficLight.LightState.GREEN); // West 
            currentGreenLightIndex = 1;
        }
        
        // Remove the random offset
        greenLightStartTime = System.currentTimeMillis();
    }

    public void updateTrafficLights() {
        long currentTime = System.currentTimeMillis();
        
        // Add debug to show time remaining until next change
        long timeRemaining = greenLightDuration - (currentTime - greenLightStartTime);
        if (timeRemaining > 0 && timeRemaining % 1000 < 20) {
        }
        
        // Each intersection decides on its own when to change
        if (currentTime - greenLightStartTime >= greenLightDuration) {
            // Calculate cars waiting in each direction pair
            int northSouthWaiting = waitingCars.get(TrafficLight.Direction.NORTH) + 
                                   waitingCars.get(TrafficLight.Direction.SOUTH);
            int eastWestWaiting = waitingCars.get(TrafficLight.Direction.EAST) + 
                                 waitingCars.get(TrafficLight.Direction.WEST);
            
            // Set all lights to red
            for (TrafficLight light : trafficLights) {
                light.setState(TrafficLight.LightState.RED);
            }

            // Logic to decide which direction gets green next
            // Always favor the direction with more waiting cars
            if (northSouthWaiting > eastWestWaiting) {
                // North/South has more cars, give it green
                trafficLights.get(0).setState(TrafficLight.LightState.GREEN); // North
                trafficLights.get(2).setState(TrafficLight.LightState.GREEN); // South
                currentGreenLightIndex = 0;
                
                // Calculate duration based on number of cars waiting
                greenLightDuration = calculateGreenDuration(northSouthWaiting);
                
                // Reset car count for directions that just turned green
                waitingCars.put(TrafficLight.Direction.NORTH, 0);
                waitingCars.put(TrafficLight.Direction.SOUTH, 0);
                
                // IMPORTANT: Clear countedCars sets for directions that just turned green
                countedCars.get(TrafficLight.Direction.NORTH).clear();
                countedCars.get(TrafficLight.Direction.SOUTH).clear();
            } else {
                // East/West has more (or equal) cars, give it green
                trafficLights.get(1).setState(TrafficLight.LightState.GREEN); // East
                trafficLights.get(3).setState(TrafficLight.LightState.GREEN); // West
                currentGreenLightIndex = 1;
                
                // Calculate duration based on number of cars waiting
                greenLightDuration = calculateGreenDuration(eastWestWaiting);
                
                // Reset car count for directions that just turned green
                waitingCars.put(TrafficLight.Direction.EAST, 0);
                waitingCars.put(TrafficLight.Direction.WEST, 0);
                
                // IMPORTANT: Clear countedCars sets for directions that just turned green
                countedCars.get(TrafficLight.Direction.EAST).clear();
                countedCars.get(TrafficLight.Direction.WEST).clear();
            }

            // Set green light start time to current time
            greenLightStartTime = currentTime;
        }
    }
    
    // Calculate green light duration based on waiting cars
    private long calculateGreenDuration(int waitingCarCount) {
        // Enforce a more reasonable base duration
        long base = BASE_GREEN_DURATION;  // 2000ms
        
        // Cap the number of waiting cars to prevent excessively long durations
        int cappedCarCount = Math.min(waitingCarCount, 10);
        
        // Formula: Base duration + (cars * additional time per car)
        long additionalTimePerCar = 500;  // 0.5s per car
        long calculatedDuration = base + (waitingCarCount * additionalTimePerCar);
        
        // Enforce strict min/max limits
        long minDuration = 1000;  // Min 1 second
        long maxDuration = 3500;  // Max 3.5 seconds (reduced from 5 seconds)
        
        // Add debug print
        long finalDuration = Math.max(minDuration, Math.min(maxDuration, calculatedDuration));        
        return finalDuration;
    }
    
    // Modified method to take a car reference to avoid duplicate counting
    public void addWaitingCar(TrafficLight.Direction direction, Car car) {
        // Only count this car if we haven't counted it before
        if (!countedCars.get(direction).contains(car)) {
            int currentCount = waitingCars.get(direction);
            waitingCars.put(direction, currentCount + 1);
            countedCars.get(direction).add(car);
        }
    }
    
    // Method to register a car that's passed through the intersection
    public void carPassed(TrafficLight.Direction direction) {
        int currentCount = waitingCars.get(direction);
        if (currentCount > 0) {
            waitingCars.put(direction, currentCount - 1);
        }
    }

    public void draw(GraphicsContext gc) {
        // Draw the intersection
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(position.getX() - SIZE / 2, position.getY() - SIZE / 2, SIZE, SIZE);

        // Draw traffic lights
        for (TrafficLight light : trafficLights) {
            light.draw(gc);
        }
    }

    public Position getPosition() {
        return position;
    }

    public TrafficLight getTrafficLight(TrafficLight.Direction direction) {
        return lightMap.get(direction);
    }
    
    // Get number of cars waiting at a specific direction
    public int getWaitingCarsCount(TrafficLight.Direction direction) {
        return waitingCars.get(direction);
    }
}