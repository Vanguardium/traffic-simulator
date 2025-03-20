package com.oblig.obj_oblig_2;

//Represents a traffic light
public class TrafficLight extends Thread {
    private LightState state;
    private Position position;
    private int redTime = 20;
    private int yellowTime = 5;
    private int greenTime = 20;
    private int timeInState = 0;

    public TrafficLight() {
        this.state = LightState.RED;
        this.position = new Position(0, 0);
    }

    public TrafficLight(Position position) {
        this.state = LightState.RED;
        this.position = position;
    }

    public void update(){
        timeInState++;

        switch(state){
            case RED:
                if (timeInState >= redTime){
                    state = LightState.GREEN;
                    timeInState = 0;
                }
                break;
            case YELLOW:
                if (timeInState >= yellowTime){
                    state = LightState.RED;
                    timeInState = 0;
                }
                break;
            case GREEN:
                if (timeInState >= greenTime){
                    state = LightState.YELLOW;
                    timeInState = 0;
                }
                break;
        }
    }

    public boolean checkCollision(Car car){
        // Check if the car is close to the traffic light
        return this.position.equals(car.getPosition());
    }

    public void setState(LightState newState){
        this.state = newState;
        this.timeInState = 0;
    }

    public LightState getLightState(){
        return state;
    }
    public Position getPosition(){
        return position;
    }
    public void setPosition(Position position){
        this.position = position;
    }

}
