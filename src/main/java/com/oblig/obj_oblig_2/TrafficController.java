package com.oblig.obj_oblig_2;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        ConfigLoader config = ConfigLoader.getInstance();

        // Create animation timer
        animationTimer = new AnimationTimer() {
            private int frameCount = 0;
            private int updateInterval = config.getTrafficLightUpdateInterval();

            @Override
            public void handle(long now) {
                // Update traffic lights based on configured interval
                if (frameCount % updateInterval == 0) {
                    updateTrafficLights();
                }

                // Redraw everything
                drawMap();
                frameCount++;
            }
        };
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
    private void setupSimulation() {
        ConfigLoader config = ConfigLoader.getInstance();

        // Create intersections from config
        for (Position pos : config.getIntersectionPositions()) {
            intersections.add(new Intersection(pos.getX(), pos.getY()));
        }

        // Create roads based on intersection positions
        createRoadsFromIntersections();

        // Setup traffic lights
        for (Intersection intersection : intersections) {
            intersection.setupTrafficLights();
        }
    }

    private void createRoadsFromIntersections() {
        // Group intersections by X and Y coordinates
        Set<Double> xCoords = new HashSet<>();
        Set<Double> yCoords = new HashSet<>();

        for (Intersection intersection : intersections) {
            Position pos = intersection.getPosition();
            xCoords.add(pos.getX());
            yCoords.add(pos.getY());
        }

        // Create horizontal roads for each Y coordinate
        for (Double y : yCoords) {
            roads.add(new Road(0, y, trafficCanvas.getWidth(), y));
        }

        // Create vertical roads for each X coordinate
        for (Double x : xCoords) {
            roads.add(new Road(x, 0, x, trafficCanvas.getHeight()));
        }
    }
}