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
import java.util.Random;

public class TrafficController {
    @FXML
    private Canvas trafficCanvas;

    private GraphicsContext gc;
    private AnimationTimer animationTimer;
    private boolean simulationRunning = false;
    
    private Simulation simulation;

    private List<Road> roads = new ArrayList<>();
    private List<Intersection> intersections = new ArrayList<>();
    private Random random = new Random();
    
    // Parameters for continuous car spawning with randomization
    private int framesSinceLastSpawn = 0;
    private int currentSpawnInterval = 100; // Initial value, will be randomized
    private int minSpawnInterval = 50;
    private int maxSpawnInterval = 200;

    @FXML
    public void initialize() {
        if (trafficCanvas == null) {
            System.err.println("Canvas is null in initialize()");
            return;
        }

        gc = trafficCanvas.getGraphicsContext2D();
        simulation = new Simulation();
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
                
                // Update cars with information about nearby vehicles
                updateVehicleAwareness();
                
                // Spawn new cars with randomized intervals
                framesSinceLastSpawn++;
                if (framesSinceLastSpawn >= currentSpawnInterval) {
                    spawnNewCars();
                    framesSinceLastSpawn = 0;
                    // Randomize the next spawn interval
                    currentSpawnInterval = minSpawnInterval + random.nextInt(maxSpawnInterval - minSpawnInterval);
                }

                // Redraw everything
                drawMap();
                drawCars();
                frameCount++;
            }
        };
    }

    // Add methods to handle button actions from FXML
    @FXML
    public void startSimulation() {
        if (!simulationRunning) {
            simulation.start();
            animationTimer.start();
            simulationRunning = true;
        }
    }
    
    @FXML
    public void stopSimulation() {
        if (simulationRunning) {
            simulation.stop();
            animationTimer.stop();
            simulationRunning = false;
        }
    }
    
    @FXML
    public void resetSimulation() {
        stopSimulation();
        simulation = new Simulation();
        setupSimulation();
        drawMap();
        startSimulation();
    }

    private void drawMap() {
        if (gc == null) return;
        
        // Clear canvas
        gc.setFill(Color.FORESTGREEN);
        gc.fillRect(0, 0, trafficCanvas.getWidth(), trafficCanvas.getHeight());
        
        // Draw roads
        for (Road road : roads) {
            road.draw(gc);
        }
        
        // Draw intersections
        for (Intersection intersection : intersections) {
            intersection.draw(gc);
        }
    }
    
    private void drawCars() {
        if (gc == null) return;
        
        for (Car car : simulation.getCars()) {
            drawCar(car);
        }
    }
    
    private void drawCar(Car car) {
        Position pos = car.getPosition();
        gc.setFill(Color.RED);
        int carSize = car.getSize();
        gc.fillRect(pos.getX() - carSize/2, pos.getY() - carSize/2, carSize, carSize);
    }
    
    private void updateTrafficLights() {
        for (Intersection intersection : intersections) {
            // Get all traffic lights at this intersection
            List<TrafficLight> lights = intersection.getTrafficLights();

            // First set all lights to red
            for (TrafficLight light : lights) {
                light.setState(TrafficLight.LightState.RED);
            }

            // Determine which directions should be green
            int currentPhase = intersection.getCurrentGreenLightIndex();

            // Phase 0: North-South green
            // Phase 1: East-West green
            if (currentPhase == 0) {
                // Set North and South to green
                for (TrafficLight light : lights) {
                    if (light.getDirection() == TrafficLight.Direction.NORTH ||
                            light.getDirection() == TrafficLight.Direction.SOUTH) {
                        light.setState(TrafficLight.LightState.GREEN);
                    }
                }
            } else {
                // Set East and West to green
                for (TrafficLight light : lights) {
                    if (light.getDirection() == TrafficLight.Direction.EAST ||
                            light.getDirection() == TrafficLight.Direction.WEST) {
                        light.setState(TrafficLight.LightState.GREEN);
                    }
                }
            }

            // Toggle the phase for next update
            intersection.toggleGreenLightIndex();
        }
    }

    private void setupSimulation() {
        ConfigLoader config = ConfigLoader.getInstance();

        // Create intersections from config
        for (Position pos : config.getIntersectionPositions()) {
            Intersection intersection = new Intersection(pos.getX(), pos.getY());
            intersections.add(intersection);
            simulation.addIntersection(intersection);
        }

        // Create roads based on intersection positions
        createRoadsFromIntersections();

        // Setup traffic lights
        for (Intersection intersection : intersections) {
            intersection.setupTrafficLights();
        }

        // Add cars to multiple roads
        createCarsOnMultipleRoads();
    }

    private void createCarsOnMultipleRoads() {
        if (roads.isEmpty()) return;
        
        ConfigLoader config = ConfigLoader.getInstance();
        int maxCarsPerRoad = config.getCarsPerRoad();
        double baseCarSpeed = config.getCarSpeed();
        double canvasWidth = trafficCanvas.getWidth();
        double canvasHeight = trafficCanvas.getHeight();
        int roadWidth = config.getRoadWidth();
        int laneWidth = roadWidth / 2;
        
        for (Road road : roads) {
            // Random number of cars per road (1 to maxCarsPerRoad)
            int carsForThisRoad = 1 + random.nextInt(maxCarsPerRoad);
            createCarsForDirection(road, carsForThisRoad, baseCarSpeed, canvasWidth, canvasHeight, roadWidth);
        }
    }
    
    private void createCarsForDirection(Road road, int carsPerRoad, double baseCarSpeed, double canvasWidth, double canvasHeight, int roadWidth) {
        int laneWidth = roadWidth / 2;
        ConfigLoader config = ConfigLoader.getInstance();
        double minSpacing = config.getMinCarDistance() * 3; // Make initial spacing much larger than minimum safe distance
        
        // Create cars for both directions
        for (int direction = 0; direction < 2; direction++) {
            boolean goingPositive = (direction == 0); // Alternate direction
            
            for (int i = 0; i < carsPerRoad; i++) {
                Position startPos;
                CarDirection carDirection;
                
                // Randomize spacing between cars (wider variation)
                int spacing = (int)minSpacing + random.nextInt(150);
                
                // Randomize car speed (-15% to +15% of base speed)
                double speedVariation = 0.85 + (random.nextDouble() * 0.30);
                double carSpeed = baseCarSpeed * speedVariation;
                
                if (road.isHorizontal()) {
                    if (goingPositive) {
                        // East-bound cars on south side (right lane)
                        carDirection = CarDirection.EAST;
                        startPos = new Position(-50 - spacing * i, road.getY1() + laneWidth/2);
                    } else {
                        // West-bound cars on north side (right lane)
                        carDirection = CarDirection.WEST;
                        startPos = new Position(canvasWidth + 50 + spacing * i, road.getY1() - laneWidth/2);
                    }
                } else { // Vertical road
                    if (goingPositive) {
                        // South-bound cars on east side (right lane)
                        carDirection = CarDirection.SOUTH;
                        startPos = new Position(road.getX1() + laneWidth/2, -50 - spacing * i);
                    } else {
                        // North-bound cars on west side (right lane)
                        carDirection = CarDirection.NORTH;
                        startPos = new Position(road.getX1() - laneWidth/2, canvasHeight + 50 + spacing * i);
                    }
                }
                
                Car car = new Car(startPos, carSpeed, carDirection, null);
                car.setCurrentRoad(road);
                car.setIntersections(intersections);
                simulation.addCar(car);
            }
        }
    }

    private void createRoadsFromIntersections() {
        // Create set of unique X and Y coordinates from intersections
        Set<Double> xCoords = new HashSet<>();
        Set<Double> yCoords = new HashSet<>();
        
        for (Intersection intersection : intersections) {
            Position pos = intersection.getPosition();
            xCoords.add(pos.getX());
            yCoords.add(pos.getY());
        }
        
        // Get canvas dimensions
        double canvasWidth = trafficCanvas.getWidth();
        double canvasHeight = trafficCanvas.getHeight();
        
        // Create horizontal roads for each unique Y coordinate
        for (Double y : yCoords) {
            Road road = new Road(0, y, canvasWidth, y);
            roads.add(road);
            simulation.addRoad(road);
        }
        
        // Create vertical roads for each unique X coordinate
        for (Double x : xCoords) {
            Road road = new Road(x, 0, x, canvasHeight);
            roads.add(road);
            simulation.addRoad(road);
        }
    }
    
    private void spawnNewCars() {
        if (roads.isEmpty()) return;
        
        ConfigLoader config = ConfigLoader.getInstance();
        double baseCarSpeed = config.getCarSpeed();
        double canvasWidth = trafficCanvas.getWidth();
        double canvasHeight = trafficCanvas.getHeight();
        int roadWidth = config.getRoadWidth();
        int laneWidth = roadWidth / 2;
        
        // Random number of roads to spawn cars on (1-3)
        int roadsToUse = 1 + random.nextInt(3);
        
        // Select distinct roads to avoid spawning multiple cars on the same road
        Set<Integer> selectedRoadIndices = new HashSet<>();
        for (int r = 0; r < roadsToUse && r < roads.size(); r++) {
            // Try to find a road we haven't used yet
            int attempts = 0;
            int roadIndex;
            do {
                roadIndex = random.nextInt(roads.size());
                attempts++;
            } while (selectedRoadIndices.contains(roadIndex) && attempts < 10);
            
            selectedRoadIndices.add(roadIndex);
            Road road = roads.get(roadIndex);
            
            // Random chance to spawn in one or both directions
            int directionCount = random.nextDouble() < 0.7 ? 1 : 2; // 70% chance for 1 direction, 30% for both
            
            // Initialize goingPositive before the loop
            boolean goingPositive = random.nextBoolean();
            
            for (int d = 0; d < directionCount; d++) {
                if (d == 0) {
                    // Keep the initial random value for first direction
                } else {
                    // Flip the direction for the second car
                    goingPositive = !goingPositive;
                }
                
                // Randomize car speed (-20% to +20% of base speed)
                double speedVariation = 0.80 + (random.nextDouble() * 0.40);
                double carSpeed = baseCarSpeed * speedVariation;
                
                Position startPos;
                CarDirection direction;
                
                if (road.isHorizontal()) {
                    if (goingPositive) {
                        // East-bound on south side
                        direction = CarDirection.EAST;
                        startPos = new Position(-50 - random.nextInt(50), road.getY1() + laneWidth/2);
                    } else {
                        // West-bound on north side
                        direction = CarDirection.WEST;
                        startPos = new Position(canvasWidth + 50 + random.nextInt(50), road.getY1() - laneWidth/2);
                    }
                } else { // Vertical road
                    if (goingPositive) {
                        // South-bound on east side
                        direction = CarDirection.SOUTH;
                        startPos = new Position(road.getX1() + laneWidth/2, -50 - random.nextInt(50));
                    } else {
                        // North-bound on west side
                        direction = CarDirection.NORTH;
                        startPos = new Position(road.getX1() - laneWidth/2, canvasHeight + 50 + random.nextInt(50));
                    }
                }
                
                Car car = new Car(startPos, carSpeed, direction, null);
                car.setCurrentRoad(road);
                car.setIntersections(intersections);
                simulation.addCar(car);
                
                // If simulation is running, start the car thread
                if (simulationRunning) {
                    car.start();
                }
            }
        }
    }
    
    private void updateVehicleAwareness() {
        List<Car> cars = simulation.getCars();
        for (Car car : cars) {
            car.setNearbyVehicles(cars);
        }
    }
}

