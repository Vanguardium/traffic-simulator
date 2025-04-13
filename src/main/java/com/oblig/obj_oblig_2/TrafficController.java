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
import java.util.Collections;
import javafx.scene.transform.Rotate;
import javafx.scene.control.Slider;
import javafx.scene.control.Label;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public class TrafficController {
    @FXML
    private Canvas trafficCanvas;

    @FXML
    private Slider speedSlider;

    @FXML
    private Label speedLabel;

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
    private int maxCars; // Maximum number of cars allowed

    private boolean autoSpawningEnabled = false; // Disabled by default

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
        maxCars = config.getMaxCars();

        // Create animation timer
        animationTimer = new AnimationTimer() {
            private int frameCount = 0;
            private long lastUpdateTime = 0;

            @Override
            public void handle(long now) {
                // Call updateTrafficLights on EVERY frame - the timing logic is inside the Intersection class
                updateTrafficLights();
                
                // Remove out-of-bounds cars
                removeOutOfBoundsCars();
                
                // Update cars with information about nearby vehicles
                updateVehicleAwareness();
                
                // Only spawn cars automatically if enabled
                if (autoSpawningEnabled) {
                    framesSinceLastSpawn++;
                    if (framesSinceLastSpawn >= currentSpawnInterval && simulation.getCars().size() < maxCars) {
                        spawnNewCars();
                        framesSinceLastSpawn = 0;
                        // Randomize the next spawn interval
                        currentSpawnInterval = minSpawnInterval + random.nextInt(maxSpawnInterval - minSpawnInterval);
                    }
                }

                // Resolve collisions
                resolveCollisions();

                // Redraw everything
                drawMap();
                drawCars();
                frameCount++;
            }
        };

        // Add slider listener to update the label and car speeds
        if (speedSlider != null) {
            // Use the existing config object instead of creating a new one
            if (config != null) {
                double initialSpeed = config.getCarSpeed();
                speedSlider.setValue(initialSpeed);
                speedLabel.setText(String.format("%.1f", initialSpeed));
            }
            
            speedSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                double speed = Math.round(newValue.doubleValue() * 10.0) / 10.0;
                speedLabel.setText(String.format("%.1f", speed));
                adjustSpeed();
            });
        }
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
            // Stop the animation timer
            animationTimer.stop();
            simulationRunning = false;
            
            // Interrupt all car threads to signal them to stop
            for (Car car : simulation.getCars()) {
                car.interrupt();
            }
            
            // Update simulation state
            simulation.stop();
        }
    }
    
    @FXML
    public void resetSimulation() {
        System.out.println("Resetting simulation...");
        
        // First stop the animation timer
        animationTimer.stop();
        simulationRunning = false;
        
        // Clear the canvas first to immediately remove visual traces of cars
        if (gc != null) {
            gc.clearRect(0, 0, trafficCanvas.getWidth(), trafficCanvas.getHeight());
        }
        
        // Save how many cars we need to clean up
        int carCount = simulation.getCars().size();
        System.out.println("Cleaning up " + carCount + " cars");
        
        // Make a copy of the cars list to avoid concurrent modification issues
        List<Car> carsToRemove = new ArrayList<>(simulation.getCars());
        
        // Properly clean up all car threads from the previous simulation
        for (Car car : carsToRemove) {
            try {
                car.interrupt();  // Signal the thread to stop
                car.join(500); // Wait longer - up to 500ms for the thread to terminate
                
                // Force removal from simulation
                simulation.getCars().remove(car);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for car thread to terminate");
            }
        }
        
        // Double-check that all cars were removed
        if (!simulation.getCars().isEmpty()) {
            System.out.println("WARNING: " + simulation.getCars().size() + " cars remained after cleanup, forcibly clearing");
            simulation.getCars().clear();  // Force clear any remaining cars
        }
        
        // Create a new simulation
        simulation = new Simulation();
        
        // Reset any other state
        framesSinceLastSpawn = 0;
        currentSpawnInterval = 100;
        
        // Setup the new simulation
        setupSimulation();
        drawMap();
        
        // Start the new simulation
        simulationRunning = true;
        animationTimer.start();
        
        System.out.println("Simulation reset complete");
    }

    @FXML
    public void addCar() {
        System.out.println("Add Car button clicked. Cars before: " + simulation.getCars().size());
        int beforeCount = simulation.getCars().size();
        spawnNewCars();
        int afterCount = simulation.getCars().size();
        System.out.println("Cars after: " + afterCount);
        
        // If simulation is running, start ALL newly added cars
        if (simulationRunning && afterCount > beforeCount) {
            List<Car> allCars = simulation.getCars();
            for (int i = beforeCount; i < afterCount; i++) {
                Car car = allCars.get(i);
                if (!car.isAlive()) {
                    car.start();
                }
            }
        }
        
        // Force redraw
        drawMap();
        drawCars();
    }

    @FXML
    public void removeCar() {
        List<Car> cars = simulation.getCars();
        System.out.println("Remove Car button clicked. Cars before: " + cars.size());
        
        if (!cars.isEmpty()) {
            Car carToRemove = cars.get(cars.size() - 1);
            carToRemove.interrupt();
            boolean removed = cars.remove(carToRemove);
            System.out.println("Car removed: " + removed + ", Cars after: " + cars.size());
            
            // Force redraw
            drawMap();
            drawCars();
        } else {
            System.out.println("No cars to remove.");
        }
    }

    @FXML
    public void adjustSpeed() {
        if (speedSlider != null) {
            double newSpeed = speedSlider.getValue();
            
            // Update the label if it wasn't updated by the listener
            if (speedLabel != null) {
                speedLabel.setText(String.format("%.1f", newSpeed));
            }
            
            // Update speed in ConfigLoader
            ConfigLoader config = ConfigLoader.getInstance();
            if (config != null) {
                config.setCurrentCarSpeed(newSpeed);
            }
            
            // Update all cars with the new speed
            for (Car car : simulation.getCars()) {
                // Apply some randomization to make it more natural (±10%)
                double randomFactor = 0.9 + (Math.random() * 0.2);
                car.setSpeed(newSpeed * randomFactor);
                
                // Also update the originalSpeed field if the car has one
                try {
                    // Using reflection to access and update the private field
                    java.lang.reflect.Field field = Car.class.getDeclaredField("originalSpeed");
                    field.setAccessible(true);
                    field.set(car, newSpeed * randomFactor);
                } catch (Exception e) {
                    // Field might not exist, ignore
                }
            }
        }
    }

    @FXML
    public void toggleAutoSpawning() {
        autoSpawningEnabled = !autoSpawningEnabled;
        System.out.println("Auto-spawning " + (autoSpawningEnabled ? "enabled" : "disabled"));
    }

    private void drawMap() {
        if (gc == null) return;
    
        // Draw background
        gc.setFill(Color.GREEN); // Grass color
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
        int carSize = car.getSize();
    
        // Save the current state
        gc.save();
        
        // Translate to car position
        gc.translate(pos.getX(), pos.getY());
        
        // Rotate based on car direction
        double rotationAngle = 0;
        switch (car.getDirection()) {
            case NORTH:
                rotationAngle = 0;
                break;
            case EAST:
                rotationAngle = 90;
                break;
            case SOUTH:
                rotationAngle = 180;
                break;
            case WEST:
                rotationAngle = 270;
                break;
        }
        
        gc.rotate(rotationAngle);
        
        // Draw car body with car's color instead of always red
        try {
            // Try to use the car's color if it has one
            java.lang.reflect.Method getColorMethod = Car.class.getMethod("getColor");
            Color carColor = (Color) getColorMethod.invoke(car);
            gc.setFill(carColor);
        } catch (Exception e) {
            // If the car has no color property, use one of several preset colors
            // based on the car's hashCode for consistency
            Color[] carColors = {
                Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, 
                Color.PURPLE, Color.BROWN, Color.DARKBLUE, Color.DARKGREEN
            };
            int colorIndex = Math.abs(car.hashCode()) % carColors.length;
            gc.setFill(carColors[colorIndex]);
        }
        
        gc.fillRect(-carSize / 2, -carSize / 2, carSize, carSize);
        
        // Draw a small indicator for the front
        gc.setFill(Color.BLACK);
        gc.fillRect(-carSize / 4, -carSize / 2, carSize / 2, carSize / 4);
        
        // Restore the original state
        gc.restore();
    }
    
    private void updateTrafficLights() {
        for (Intersection intersection : simulation.getIntersections()) {
            intersection.updateTrafficLights();
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

    // In the createCarsOnMultipleRoads() method, allow for more initial cars
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
            // You could increase this if you want more cars at startup
            // For example: int carsForThisRoad = 5 + random.nextInt(5);
            int carsForThisRoad = 1 + random.nextInt(maxCarsPerRoad);
            createCarsForDirection(road, carsForThisRoad, baseCarSpeed, canvasWidth, canvasHeight, roadWidth);
        }
    }

    private void createCarsForDirection(Road road, int carsPerRoad, double baseCarSpeed, double canvasWidth, double canvasHeight, int roadWidth) {
        int laneWidth = roadWidth / 2;
        ConfigLoader config = ConfigLoader.getInstance();
        // Increase minimum spacing significantly to avoid initial collisions
        double minSpacing = config.getMinCarDistance() * 5; // Much larger spacing
        
        // Create cars for both directions
        for (int dir = 0; dir < 2; dir++) {
            boolean goingPositive = (dir == 0); // Alternate direction
        
            for (int i = 0; i < carsPerRoad; i++) {
                Position startPos;
                CarDirection carDirection;
        
                // Use larger spacing multiplier for each successive car
                int spacing = (int) (minSpacing + (i * minSpacing)); // Progressive spacing
        
                // Randomize car speed (-15% to +15% of base speed)
                double speedVariation = 0.85 + (random.nextDouble() * 0.30);
                double carSpeed = baseCarSpeed * speedVariation;
        
                if (road.isHorizontal()) {
                    if (goingPositive) {
                        // East-bound - should be on the BOTTOM half of horizontal road
                        carDirection = CarDirection.EAST;
                        startPos = new Position(-150, road.getY1() + (double) laneWidth/2); // Fixed distance, not random
                    } else {
                        // West-bound - should be on the TOP half of horizontal road
                        carDirection = CarDirection.WEST;
                        startPos = new Position(canvasWidth + 150, road.getY1() - (double) laneWidth/2); // Fixed distance
                    }
                } else { // Vertical road
                    if (goingPositive) {
                        // South-bound - should be on the RIGHT half of vertical roadd
                        carDirection = CarDirection.SOUTH;
                        startPos = new Position(road.getX1() + (double) laneWidth/2, -150); // Fixed distance
                    } else {
                        // North-bound - should be on the LEFT half of vertical road
                        carDirection = CarDirection.NORTH;
                        startPos = new Position(road.getX1() - (double) laneWidth/2, canvasHeight + 150); // Fixed distance
                    }
                }
        
                Car car = new Car(startPos, carSpeed, carDirection, null);
                car.setSimulation(simulation); // Add this line to set simulation
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
        
        // Get the current number of cars
        int currentCarCount = simulation.getCars().size();

        // Calculate how many more cars we can add
        int availableSlots = maxCars - currentCarCount;
        if (availableSlots <= 0) return; // Can't add more cars

        // Add just 1-2 cars at a time to avoid overcrowding
        int newCarsToAdd = Math.min(2, availableSlots);
        
        List<Car> newlyAddedCars = new ArrayList<>();

        ConfigLoader config = ConfigLoader.getInstance();
        double baseCarSpeed = config.getCurrentCarSpeed();
        double canvasWidth = trafficCanvas.getWidth();
        double canvasHeight = trafficCanvas.getHeight();
        int roadWidth = config.getRoadWidth();
        int laneWidth = roadWidth / 2;
        
        // Select roads that don't already have too many cars
        List<Road> eligibleRoads = new ArrayList<>();
        for (Road road : roads) {
            int carsOnRoad = countCarsOnRoad(road);
            
            // Check for cars near the spawn points to avoid instant collisions
            boolean carNearEntryPoint = false;
            for (Car existingCar : simulation.getCars()) {
                // Check both horizontal and vertical entry points
                double distance = Double.MAX_VALUE;
                
                if (road.isHorizontal()) {
                    // Check east-bound entry point
                    double eastDist = Math.sqrt(
                        Math.pow(existingCar.getPosition().getX() - (-150), 2) +
                        Math.pow(existingCar.getPosition().getY() - (road.getY1() + laneWidth/2), 2));
                    
                    // Check west-bound entry point
                    double westDist = Math.sqrt(
                        Math.pow(existingCar.getPosition().getX() - (canvasWidth + 150), 2) +
                        Math.pow(existingCar.getPosition().getY() - (road.getY1() - laneWidth/2), 2));
                    
                    distance = Math.min(eastDist, westDist);
                } else {
                    // Check south-bound entry point
                    double southDist = Math.sqrt(
                        Math.pow(existingCar.getPosition().getX() - (road.getX1() + laneWidth/2), 2) +
                        Math.pow(existingCar.getPosition().getY() - (-150), 2));
                    
                    // Check north-bound entry point
                    double northDist = Math.sqrt(
                        Math.pow(existingCar.getPosition().getX() - (road.getX1() - laneWidth/2), 2) +
                        Math.pow(existingCar.getPosition().getY() - (canvasHeight + 150), 2));
                    
                    distance = Math.min(southDist, northDist);
                }
                
                if (distance < config.getMinCarDistance() * 5) {
                    carNearEntryPoint = true;
                    break;
                }
            }
            
            // Change the limit from 3 to 20 cars per road
            if (carsOnRoad < 20 && !carNearEntryPoint) {  // Changed from 3 to 20
                eligibleRoads.add(road);
            }
        }
        
        if (eligibleRoads.isEmpty()) {
            System.out.println("All roads are crowded, skipping spawn");
            return;
        }
        
        // Select roads randomly from eligible roads
        Collections.shuffle(eligibleRoads);
        for (int i = 0; i < Math.min(newCarsToAdd, eligibleRoads.size()); i++) {
            Road road = eligibleRoads.get(i);
            
            // Try both directions and use the one that's not blocked
            boolean eastWestBlocked = checkIfDirectionBlocked(road, true);
            boolean westEastBlocked = checkIfDirectionBlocked(road, false);

            // If both directions are blocked, skip this road
            if (eastWestBlocked && westEastBlocked) continue;

            // If one direction is blocked, use the other one
            boolean goingPositive = eastWestBlocked ? false : 
                                  (westEastBlocked ? true : random.nextBoolean());
            
            // Randomize car speed (85% to 115% of base speed)
            double speedVariation = 0.85 + (random.nextDouble() * 0.30);
            double carSpeed = baseCarSpeed * speedVariation;

            Position startPos;
            CarDirection direction;
            
            // Calculate proper lane offset - important for correct positioning
            int properLaneOffset = laneWidth / 2; // Center of lane
            
            // Very important - position cars correctly based on road orientation and direction
            if (road.isHorizontal()) {
                if (goingPositive) {
                    // East-bound - should be on the BOTTOM half of horizontal road
                    direction = CarDirection.EAST;
                    // Position car well off-screen to the left
                    startPos = new Position(-150, road.getY1() + properLaneOffset);
                } else {
                    // West-bound - should be on the TOP half of horizontal road
                    direction = CarDirection.WEST;
                    // Position car well off-screen to the right
                    startPos = new Position(canvasWidth + 150, road.getY1() - properLaneOffset);
                }
            } else { // Vertical road
                if (goingPositive) {
                    // South-bound - should be on the LEFT half of vertical road
                    direction = CarDirection.SOUTH;
                    // Position car well off-screen to the top
                    startPos = new Position(road.getX1() - properLaneOffset, -150);
                } else {
                    // North-bound - should be on the RIGHT half of vertical road
                    direction = CarDirection.NORTH;
                    // Position car well off-screen to the bottom
                    startPos = new Position(road.getX1() + properLaneOffset, canvasHeight + 150);
                }
            }

            // Check if there's already a car too close to this position
            boolean tooClose = false;
            for (Car existingCar : simulation.getCars()) {
                double distance = Math.sqrt(
                    Math.pow(existingCar.getPosition().getX() - startPos.getX(), 2) +
                    Math.pow(existingCar.getPosition().getY() - startPos.getY(), 2));
                
                if (distance < config.getMinCarDistance() * 3) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                // Create and add the car
                Car car = new Car(startPos, carSpeed, direction, null);
                car.setSimulation(simulation);
                car.setCurrentRoad(road);
                car.setIntersections(intersections);
                simulation.addCar(car);
                newlyAddedCars.add(car);
                System.out.println("Created car: " + direction + " at " + startPos.getX() + "," + startPos.getY());
            }
        }
        
        // Start the threads for newly added cars
        if (simulationRunning) {
            for (Car newCar : newlyAddedCars) {
                if (!newCar.isAlive()) {
                    newCar.start();
                    System.out.println("Started new car thread at position: " + 
                                      newCar.getPosition().getX() + "," + 
                                      newCar.getPosition().getY());
                }
            }
        }
    }

    // Helper method to count cars on a road
    private int countCarsOnRoad(Road road) {
        int count = 0;
        for (Car car : simulation.getCars()) {
            if (car.getCurrentRoad() == road) {
                count++;
            }
        }
        return count;
    }
    
    private void updateVehicleAwareness() {
        List<Car> cars = simulation.getCars();
        for (Car car : cars) {
            car.setNearbyVehicles(cars);
        }
    }
    
    private void removeOutOfBoundsCars() {
        List<Car> carsToRemove = new ArrayList<>();
        double canvasWidth = trafficCanvas.getWidth();
        double canvasHeight = trafficCanvas.getHeight();
        int margin = 200; // Increase margin even more to be very forgiving
        
        for (Car car : simulation.getCars()) {
            Position pos = car.getPosition();
            // Check if car is far outside the canvas bounds
            if (pos.getX() < -margin || pos.getX() > canvasWidth + margin || 
                pos.getY() < -margin || pos.getY() > canvasHeight + margin) {
                System.out.println("Removing out-of-bounds car at: " + pos.getX() + "," + pos.getY());
                carsToRemove.add(car);
            }
        }
        
        // Remove the out-of-bounds cars
        for (Car car : carsToRemove) {
            car.interrupt(); // Stop the car thread
            try {
                car.join(100); // Wait up to 100ms for the thread to terminate
            } catch (InterruptedException e) {
                // Ignore, we'll remove the car anyway
            }
            simulation.getCars().remove(car);
        }
    }

    // Add this method to implement the missing functionality
    private boolean checkIfDirectionBlocked(Road road, boolean goingPositive) {
        ConfigLoader config = ConfigLoader.getInstance();
        double canvasWidth = trafficCanvas.getWidth();
        double canvasHeight = trafficCanvas.getHeight();
        int roadWidth = config.getRoadWidth();
        int laneWidth = roadWidth / 2;
        double minSafeDistance = config.getMinCarDistance() * 5; // Use the same threshold as elsewhere
        
        // Calculate entry position based on direction
        Position entryPos;
        if (road.isHorizontal()) {
            if (goingPositive) {
                // East-bound entry
                entryPos = new Position(-150, road.getY1() + laneWidth/2);
            } else {
                // West-bound entry
                entryPos = new Position(canvasWidth + 150, road.getY1() - laneWidth/2);
            }
        } else { // Vertical road
            if (goingPositive) {
                // South-bound entry
                entryPos = new Position(road.getX1() + laneWidth/2, -150);
            } else {
                // North-bound entry
                entryPos = new Position(road.getX1() - laneWidth/2, canvasHeight + 150);
            }
        }
        
        // Check if any cars are too close to this entry point
        for (Car car : simulation.getCars()) {
            double distance = Math.sqrt(
                Math.pow(car.getPosition().getX() - entryPos.getX(), 2) +
                Math.pow(car.getPosition().getY() - entryPos.getY(), 2));
            
            if (distance < minSafeDistance) {
                return true; // Direction is blocked
            }
        }
        
        return false; // Direction is clear
    }

    // Add this to the animation timer handle method
    private void resolveCollisions() {
        List<Car> cars = simulation.getCars();
        Set<Car> processedCars = new HashSet<>();
        
        for (Car car1 : cars) {
            for (Car car2 : cars) {
                if (car1 == car2 || processedCars.contains(car2)) continue;
                
                Position pos1 = car1.getPosition();
                Position pos2 = car2.getPosition();
                
                // Calculate distance between cars
                double distance = Math.sqrt(
                    Math.pow(pos1.getX() - pos2.getX(), 2) + 
                    Math.pow(pos1.getY() - pos2.getY(), 2)
                );
                
                // If they're too close, gently push them apart
                if (distance < car1.getSize()) {
                    // Calculate push direction
                    double dx = pos1.getX() - pos2.getX();
                    double dy = pos1.getY() - pos2.getY();
                    
                    // Normalize
                    double len = Math.sqrt(dx*dx + dy*dy);
                    if (len > 0) {
                        dx /= len;
                        dy /= len;
                        
                        // Push cars apart more strongly
                        double pushStrength = 2.0; // Was 0.5
                        
                        Position newPos1 = new Position(
                            pos1.getX() + dx * pushStrength,
                            pos1.getY() + dy * pushStrength
                        );
                        
                        Position newPos2 = new Position(
                            pos2.getX() - dx * pushStrength,
                            pos2.getY() - dy * pushStrength
                        );
                        
                        // Update positions using reflection to access private positionLock
                        try {
                            java.lang.reflect.Method setXMethod = Position.class.getDeclaredMethod("setX", double.class);
                            java.lang.reflect.Method setYMethod = Position.class.getDeclaredMethod("setY", double.class);
                            
                            setXMethod.invoke(pos1, newPos1.getX());
                            setYMethod.invoke(pos1, newPos1.getY());
                            setXMethod.invoke(pos2, newPos2.getX());
                            setYMethod.invoke(pos2, newPos2.getY());
                            
                        } catch (Exception e) {
                            // Ignore reflection errors
                        }
                        
                        // Also reset both cars' speeds to avoid stuck situations
                        try {
                            java.lang.reflect.Field speedField = Car.class.getDeclaredField("speed");
                            java.lang.reflect.Field originalSpeedField = Car.class.getDeclaredField("originalSpeed");
                            speedField.setAccessible(true);
                            originalSpeedField.setAccessible(true);
                            
                            // Get original speeds
                            double origSpeed1 = (double)originalSpeedField.get(car1);
                            double origSpeed2 = (double)originalSpeedField.get(car2);
                            
                            // Set to 80% of original speed
                            speedField.set(car1, origSpeed1 * 0.8);
                            speedField.set(car2, origSpeed2 * 0.8);
                            
                        } catch (Exception e) {
                            // Ignore reflection errors
                        }
                    }
                }
            }
            processedCars.add(car1);
        }
    }
}

