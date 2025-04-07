package com.oblig.obj_oblig_2;

import javafx.scene.control.skin.TextInputControlSkin;

//Represents a car
public class Car extends Thread{
    private Position position;
    private double speed;
    private CarDirection direction;
    private Road currentRoad;
    private TrafficLight trafficLight;

    public Car () {
        this.position = new Position(0,0);
        this.speed = 0;
        this.direction = CarDirection.NORTH;
    }
    public Car (Position position, double speed, CarDirection direction, TrafficLight trafficLight) {
        this.position = position;
        this.speed = speed;
        this.direction = direction;
        this.trafficLight = trafficLight;
    }

    @Override
    public void run(){
        while(!Thread.interrupted()){
            move();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void move() {
        if(shouldStopForTrafficLight()){
            return;
        }

        // Move the car in the direction it is facing
        switch (direction) {
            case NORTH:
                position.setY(position.getY() + speed);
                break;
            case SOUTH:
                position.setY(position.getY() - speed);
                break;
            case EAST:
                position.setX(position.getX() + speed);
                break;
            case WEST:
                position.setX(position.getX() - speed);
                break;
        }
    }

    private boolean shouldStopForTrafficLight(){
        if(trafficLight == null){
            return false;
        }
        LightState lightState = trafficLight.getLightState();
        return lightState == LightState.RED || lightState == LightState.GREEN;
    }

    public boolean checkCollision (TrafficLight trafficLight) {
        // Check if the car is close to the traffic light
        return this.position.equals(trafficLight.getPosition());
    }

    public double getX(){
        return position.getX();
    }
    public double getY(){
        return position.getY();
    }
    public void setX(double x){
        position.setX(x);
    }
    public void setY(double y){
        position.setY(y);
    }
    public Position getPosition(){
        return position;
    }
    public void setPosition(Position position){
        this.position = position;
    }
    public double getSpeed(){
        return speed;
    }
    public void setSpeed(double speed){
        this.speed = speed;
    }
    public CarDirection getDirection(){
        return direction;
    }
    public void setDirection(CarDirection direction){
        this.direction = direction;
    }
    public Road getCurrentRoad(){
        return currentRoad;
    }
    public void setCurrentRoad(Road currentRoad){
        this.currentRoad = currentRoad;
    }
}
