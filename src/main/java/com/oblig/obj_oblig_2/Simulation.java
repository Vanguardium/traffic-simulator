package com.oblig.obj_oblig_2;

import java.util.ArrayList;
import java.util.List;

//Controls the simulation
public class Simulation {
    private List<Intersection> intersections;
    private List<Road> roads;
    private List<Car> cars;
    private int simSpeed;
    private boolean isRunning;

    public Simulation() {
        this.intersections = new ArrayList<>();
        this.roads = new ArrayList<>();
        this.cars = new ArrayList<>();
        this.simSpeed = 1;
        this.isRunning = false;
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            for (Car car : cars) {
                if (!car.isAlive()) {
                    car.start();
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
        for (Car car : cars) {
            car.interrupt();
        }
    }

    public void reset() {
        stop();
        // Reset traffic lights
        for (Intersection intersection : intersections) {
            intersection.resetTrafficLights();
        }
        // Reset cars to their starting positions
        cars.clear();
    }

    public void addCar(Car car) {
        cars.add(car);
        
        // Make sure new car is aware of other cars
        car.setNearbyVehicles(cars);
    }

    public void addIntersection(Intersection intersection) {
        intersections.add(intersection);
    }

    public void addRoad(Road road) {
        roads.add(road);
    }

    public List<Car> getCars() {
        return cars;
    }

    public List<Intersection> getIntersections() {
        return intersections;
    }

    public List<Road> getRoads() {
        return roads;
    }

    public int getSimSpeed() {
        return simSpeed;
    }

    public void setSimSpeed(int simSpeed) {
        this.simSpeed = simSpeed;
    }
}
