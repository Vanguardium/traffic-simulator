package com.oblig.obj_oblig_2;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class Intersection {
    private List<TrafficLight> trafficLights;
    private Map<TrafficLight.Direction, TrafficLight> lightMap;
    private Position position;
    private final int SIZE = 60;
    private int currentGreenLightIndex = 0;

    public Intersection(double x, double y) {
        this.position = new Position(x, y);
        this.trafficLights = new ArrayList<>();
        this.lightMap = new HashMap<>();
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

        // Initialize with the north traffic light being green
        trafficLights.get(0).setState(TrafficLight.LightState.GREEN);
    }
    
    public TrafficLight getTrafficLight(TrafficLight.Direction direction) {
        return lightMap.get(direction);
    }

    public void draw(GraphicsContext gc) {
        // Draw the intersection
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(position.getX() - SIZE/2, position.getY() - SIZE/2, SIZE, SIZE);
        
        // Draw traffic lights
        for (TrafficLight light : trafficLights) {
            light.draw(gc);
        }
    }
    
    public void updateTrafficLights() {
        // Set all lights to red initially
        for (TrafficLight light : trafficLights) {
            light.setState(TrafficLight.LightState.RED);
        }
        
        // Set the current green light
        if (!trafficLights.isEmpty()) {
            trafficLights.get(currentGreenLightIndex).setState(TrafficLight.LightState.GREEN);
            
            // Move to the next traffic light for the next update
            currentGreenLightIndex = (currentGreenLightIndex + 1) % trafficLights.size();
        }
    }
    
    public void resetTrafficLights() {
        currentGreenLightIndex = 0;
        
        // Set all lights to red except the first one
        for (int i = 0; i < trafficLights.size(); i++) {
            if (i == 0) {
                trafficLights.get(i).setState(TrafficLight.LightState.GREEN);
            } else {
                trafficLights.get(i).setState(TrafficLight.LightState.RED);
            }
        }
    }
    
    public Position getPosition() {
        return position;
    }
    
    public List<TrafficLight> getTrafficLights() {
        return trafficLights;
    }
}
