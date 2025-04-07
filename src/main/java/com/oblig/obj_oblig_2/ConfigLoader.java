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

    // TrafficLight config
    public int getTrafficLightSize() {
        return config.path("trafficLight").path("size").asInt(15);
    }

    public int getTrafficLightOffset() {
        return config.path("trafficLight").path("offset").asInt(25);
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

    // Simulation config
    public int getTrafficLightUpdateInterval() {
        return config.path("simulation").path("trafficLightUpdateInterval").asInt(100);
    }
}