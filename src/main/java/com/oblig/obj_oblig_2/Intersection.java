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
    private final int SIZE = ConfigLoader.getInstance().getIntersectionRadius() * 2; // Use configurable radius
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

    public void toggleGreenLightIndex() {
        // Assuming just two phases (N-S and E-W)
        currentGreenLightIndex = (currentGreenLightIndex + 1) % 2;
    }

    public int getCurrentGreenLightIndex() {
        return currentGreenLightIndex;
    }

    // Helper method to get the radius of the intersection
    public int getRadius() {
        return SIZE / 2;
    }
    
    // Get all roads connected to this intersection (to be used in path planning)
    public List<Road> getConnectedRoads(List<Road> allRoads) {
        List<Road> connectedRoads = new ArrayList<>();
        
        for (Road road : allRoads) {
            // Check if this road passes through the intersection
            if (road.isHorizontal()) {
                double x1 = road.getX1();
                double x2 = road.getX2();
                double y = road.getY1();
                
                if (x1 <= position.getX() && position.getX() <= x2 && 
                    Math.abs(position.getY() - y) < SIZE / 2) {
                    connectedRoads.add(road);
                }
            } else if (road.isVertical()) {
                double y1 = road.getY1();
                double y2 = road.getY2();
                double x = road.getX1();
                
                if (y1 <= position.getY() && position.getY() <= y2 && 
                    Math.abs(position.getX() - x) < SIZE / 2) {
                    connectedRoads.add(road);
                }
            }
        }
        
        return connectedRoads;
    }
}
