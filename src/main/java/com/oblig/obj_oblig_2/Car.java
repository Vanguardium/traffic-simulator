package com.oblig.obj_oblig_2;

import javafx.scene.control.skin.TextInputControlSkin;

//Represents a car
public class Car {
    private Position position;
    private double speed;
    private CarDirection direction;
    private Road currentRoad;

    public Car () {
        this.position = new Position();
        this.speed = 0;
        this.direction = CarDirection.NORTH;
    }
    public Car (Position position, double speed, CarDirection direction){
        this.position = position;
        this.speed = speed;
        this.direction = direction;
    }

    public void move() {
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

    public Position getPosition(){
        return position;
    }
    public void setPosition(Position position){
        this.position = position;
    }
    public double getSpeed(){
        return speed;
    }
    public void setSpeed(){
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
