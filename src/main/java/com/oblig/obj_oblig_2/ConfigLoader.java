package com.oblig.obj_oblig_2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class ConfigLoader {
    private static ConfigLoader instance;
    private JsonNode config;
    private double currentCarSpeed = 0;

    private ConfigLoader(String configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        config = mapper.readTree(new File(configFile));
    }

    public static ConfigLoader getInstance() {
        if (instance == null) {
            try {
                instance = new ConfigLoader("config/default-map.json");
            } catch (IOException e) {
                throw new RuntimeException("Failed to load configuration", e);
            }
        }
        return instance;
    }

    public static void initialize(String configFile) throws IOException {
        instance = new ConfigLoader(configFile);
    }

    // Application config
    public int getWindowWidth() {
        return config.path("application").path("windowWidth").asInt(1200);
    }

    public int getWindowHeight() {
        return config.path("application").path("windowHeight").asInt(800);
    }

    // Car config
    public int getCarSize() {
        return config.path("car").path("size").asInt(10);
    }
    
    public double getMinCarDistance() {
        // Default is 2 car lengths
        return getCarSize() * 2;
    }

    // Turning parameters
    public int getTurningPathPoints() {
        return config.path("car").path("turningPathPoints").asInt(30); // Increased from 20 to 30 for smoother curves
    }
    
    public double getTurningSpeed() {
        double baseSpeed = getCarSpeed();
        double turningFactor = config.path("car").path("turningSpeedFactor").asDouble(0.6);
        return baseSpeed * turningFactor; // Reduced from 0.8 to 0.6 for smoother turns
    }


    public double getStraightThroughProbability() {
        return config.path("car").path("straightThroughProbability").asDouble(0.6);
    }

    // TrafficLight config
    public int getTrafficLightSize() {
        return config.path("trafficLight").path("size").asInt(15);
    }

    public int getTrafficLightOffset() {
        return config.path("trafficLight").path("offset").asInt(25);
    }

    public int getTrafficLightDetectionRadius() {
        return config.path("trafficLight").path("detectionRadius").asInt(50);
    }

    // Map config
    public List<Position> getIntersectionPositions() {
        List<Position> positions = new ArrayList<>();
        JsonNode intersections = config.path("map").path("intersections");

        for (JsonNode intersection : intersections) {
            double x = intersection.path("x").asDouble();
            double y = intersection.path("y").asDouble();
            positions.add(new Position(x, y));
        }

        return positions;
    }

    public int getRoadWidth() {
        return config.path("map").path("roadWidth").asInt(40);
    }
    
    // Add intersection radius parameter (for turning calculations)
    public int getIntersectionRadius() {
        return config.path("map").path("intersectionRadius").asInt(30);
    }

    // Simulation config
    public int getTrafficLightUpdateInterval() {
        return config.path("simulation").path("trafficLightUpdateInterval").asInt(500);
    }
    
    public double getCarSpeed() {
        return config.path("simulation").path("carSpeed").asDouble(2.0);
    }
    
    public int getCarsPerRoad() {
        return config.path("simulation").path("carsPerRoad").asInt(2);
    }
    public int getMaxCars() {
        return config.path("simulation").path("maxCars").asInt(50); // Default maximum of 50 cars
    }

    // This is an in-memory update, not a file update
    public void setCarSpeed(double speed) {
        // This would require a mutable configuration or a way to update the config
        // Since the current implementation doesn't support this, you'd need to 
        // maintain a separate value
        this.currentCarSpeed = speed;
    }

    // Add this method to update the current car speed 
    // (alternative name with same functionality as setCarSpeed)
    public void setCurrentCarSpeed(double speed) {
        this.currentCarSpeed = speed;
    }

    // And a getter for this field
    public double getCurrentCarSpeed() {
        return currentCarSpeed != 0 ? currentCarSpeed : getCarSpeed();
    }

    // Fix these methods to use the proper section structure
    public long getMinGreenLightDuration() {
        return config.path("simulation").path("minGreenLightDuration").asLong(3000); // Default 3 seconds
    }

    public long getMaxGreenLightDuration() {
        return config.path("simulation").path("maxGreenLightDuration").asLong(15000); // Default 15 seconds
    }

    public int getCarsPerTimeUnit() {
        return config.path("simulation").path("carsPerTimeUnit").asInt(3); // Default 5 cars per time unit
    }

    private int getConfigInt(String key, int defaultValue) {
        if (config.has(key)) {
            return config.path(key).asInt(defaultValue);
        }
        return defaultValue;
    }
}
