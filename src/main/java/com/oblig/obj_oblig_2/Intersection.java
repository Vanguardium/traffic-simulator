package com.oblig.obj_oblig_2;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;

public class Intersection {
    private List<TrafficLight> trafficLights;
    private Position position;
    private final int SIZE = 60;
    private int currentGreenLightIndex = 0;

    public Intersection(double x, double y) {
        this.position = new Position(x, y);
        this.trafficLights = new ArrayList<>();
    }

    public void setupTrafficLights() {
        // Create traffic lights in all four directions
        trafficLights.add(new TrafficLight(position, TrafficLight.Direction.NORTH));
        trafficLights.add(new TrafficLight(position, TrafficLight.Direction.EAST));
        trafficLights.add(new TrafficLight(position, TrafficLight.Direction.SOUTH));
        trafficLights.add(new TrafficLight(position, TrafficLight.Direction.WEST));

        // Initialize with the north traffic light being green
        trafficLights.get(0).setState(TrafficLight.LightState.GREEN);
    }

    public void draw(GraphicsContext gc) {
        // Draw the intersection
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(position.getX() - SIZE/2, position.getY() - SIZE/2, SIZE, SIZE);

        // Draw all traffic lights
        for (TrafficLight light : trafficLights) {
            light.draw(gc);
        }
    }

    public void updateTrafficLights() {
        // Set all traffic lights to red
        for (TrafficLight light : trafficLights) {
            light.setState(TrafficLight.LightState.RED);
        }

        // Move to next light and set it to green
        currentGreenLightIndex = (currentGreenLightIndex + 1) % trafficLights.size();
        trafficLights.get(currentGreenLightIndex).setState(TrafficLight.LightState.GREEN);
    }

    public void resetTrafficLights() {
        // Set all traffic lights to red
        for (TrafficLight light : trafficLights) {
            light.setState(TrafficLight.LightState.RED);
        }

        // Reset to first light being green
        currentGreenLightIndex = 0;
        trafficLights.get(0).setState(TrafficLight.LightState.GREEN);
    }
}