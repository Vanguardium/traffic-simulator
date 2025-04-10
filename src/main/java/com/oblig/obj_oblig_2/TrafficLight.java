package com.oblig.obj_oblig_2;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class TrafficLight {
    public enum Direction {NORTH, EAST, SOUTH, WEST}
    public enum LightState {RED, GREEN, YELLOW}

    private Position position;
    private Direction direction;
    private LightState state;
    private final int SIZE;

    public TrafficLight(Position intersectionPos, Direction direction) {
        this.direction = direction;
        ConfigLoader config = ConfigLoader.getInstance();
        this.SIZE = config.getTrafficLightSize();

        // Position the traffic light based on the intersection position and direction
        double x = intersectionPos.getX();
        double y = intersectionPos.getY();
        int offset = config.getTrafficLightOffset(); // Get from config

        switch (direction) {
            case NORTH:
                this.position = new Position(x, y - offset);
                break;
            case EAST:
                this.position = new Position(x + offset, y);
                break;
            case SOUTH:
                this.position = new Position(x, y + offset);
                break;
            case WEST:
                this.position = new Position(x - offset, y);
                break;
        }

        this.state = LightState.RED;
    }

    public LightState getLightState() {
        return state;
    }

    public void setState(LightState state) {
        this.state = state;
    }

    public Position getPosition() {
        return position;
    }

    public Direction getDirection() {
        return direction;
    }

    public void draw(GraphicsContext gc) {
        Color lightColor;
        switch (state) {
            case GREEN:
                lightColor = Color.GREEN;
                break;
            case YELLOW:
                lightColor = Color.YELLOW;
                break;
            default:
                lightColor = Color.RED;
        }

        gc.setFill(lightColor);
        gc.fillOval(position.getX() - SIZE/2, position.getY() - SIZE/2, SIZE, SIZE);
        gc.setStroke(Color.BLACK);
        gc.strokeOval(position.getX() - SIZE/2, position.getY() - SIZE/2, SIZE, SIZE);
    }
}