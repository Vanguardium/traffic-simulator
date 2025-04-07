package com.oblig.obj_oblig_2;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;

public class TrafficController {
    @FXML
    private Canvas trafficCanvas;

    private GraphicsContext gc;
    private AnimationTimer animationTimer;
    private boolean simulationRunning = false;

    private List<Road> roads = new ArrayList<>();
    private List<Intersection> intersections = new ArrayList<>();

    @FXML
    public void initialize() {
        if (trafficCanvas == null) {
            System.err.println("Canvas is null in initialize()");
            return;
        }

        gc = trafficCanvas.getGraphicsContext2D();
        setupSimulation();
        drawMap();

        // Create animation timer
        animationTimer = new AnimationTimer() {
            private int frameCount = 0;

            @Override
            public void handle(long now) {
                // Update traffic lights every 100 frames
                if (frameCount % 100 == 0) {
                    updateTrafficLights();
                }

                // Redraw everything
                drawMap();
                frameCount++;
            }
        };
    }

    private void setupSimulation() {
        // Create intersections at specific positions
        Intersection intersection1 = new Intersection(200, 150);
        Intersection intersection2 = new Intersection(600, 150);
        Intersection intersection3 = new Intersection(200, 450);
        Intersection intersection4 = new Intersection(600, 450);

        // Add each intersection to the list
        intersections.add(intersection1);
        intersections.add(intersection2);
        intersections.add(intersection3);
        intersections.add(intersection4);

        // Create horizontal roads
        Road horizontalRoad1 = new Road(0, 150, trafficCanvas.getWidth(), 150);
        Road horizontalRoad2 = new Road(0, 450, trafficCanvas.getWidth(), 450);

        // Create vertical roads
        Road verticalRoad1 = new Road(200, 0, 200, trafficCanvas.getHeight());
        Road verticalRoad2 = new Road(600, 0, 600, trafficCanvas.getHeight());

        // Add roads to the list
        roads.add(horizontalRoad1);
        roads.add(horizontalRoad2);
        roads.add(verticalRoad1);
        roads.add(verticalRoad2);

        // Setup traffic lights for each intersection
        for (Intersection intersection : intersections) {
            intersection.setupTrafficLights();
        }
    }

    private void drawMap() {
        // Clear canvas
        gc.setFill(Color.LIGHTGREEN);
        gc.fillRect(0, 0, trafficCanvas.getWidth(), trafficCanvas.getHeight());

        // Draw roads
        for (Road road : roads) {
            road.draw(gc);
        }

        // Draw intersections and their traffic lights
        for (Intersection intersection : intersections) {
            intersection.draw(gc);
        }
    }

    private void updateTrafficLights() {
        // Update traffic light states for each intersection
        for (Intersection intersection : intersections) {
            intersection.updateTrafficLights();
        }
    }

    @FXML
    public void startSimulation() {
        if (!simulationRunning) {
            animationTimer.start();
            simulationRunning = true;
        }
    }

    @FXML
    public void stopSimulation() {
        if (simulationRunning) {
            animationTimer.stop();
            simulationRunning = false;
        }
    }

    @FXML
    public void resetSimulation() {
        stopSimulation();
        // Reset all intersections and traffic lights
        for (Intersection intersection : intersections) {
            intersection.resetTrafficLights();
        }
        drawMap();
    }
}