package com.oblig.obj_oblig_2;

import java.util.ArrayList;
import java.util.List;

//Controls the simulation
public class Simulation {
    private List<Intersection> intersections;
    private List<Road> roads;
    private List<Car> cars;
    private boolean isRunning;

    public Simulation() {
        this.intersections = new ArrayList<>();
        this.roads = new ArrayList<>();
        this.cars = new ArrayList<>();
        this.isRunning = false;
    }

    public void start() {
        isRunning = true;
    }

    public void stop() {
        isRunning = false;
    }

    public void update() {
        // Update each intersection independently
        for (Intersection intersection : intersections) {
            intersection.updateTrafficLights();
        }

        // Update cars
        for (Car car : cars) {
            car.move();
        }
    }

    public void addIntersection(Intersection intersection) {
        intersections.add(intersection);
    }

    public void addRoad(Road road) {
        roads.add(road);
    }

    public void addCar(Car car) {
        cars.add(car);
    }

    public List<Intersection> getIntersections() {
        return intersections;
    }

    public List<Road> getRoads() {
        return roads;
    }

    public List<Car> getCars() {
        return cars;
    }

    private void createRoadsFromIntersections(double canvasWidth, double canvasHeight) {
        for (Intersection intersection : this.intersections) {
            Position pos = intersection.getPosition();

            // Create independent roads for each intersection
            Road horizontalRoad = new Road(0, pos.getY(), canvasWidth, pos.getY());
            Road verticalRoad = new Road(pos.getX(), 0, pos.getX(), canvasHeight);

            this.addRoad(horizontalRoad);
            this.addRoad(verticalRoad);
        }
    }
    
    public void initialize() {
        // Initialize intersections, roads, and cars here
        // Example:
        Intersection intersection = new Intersection(400, 300);
        intersection.setupTrafficLights();
        this.addIntersection(intersection);

        // Create roads from intersections
        this.createRoadsFromIntersections(1200, 800); // Replace with actual canvas dimensions
    }
}
