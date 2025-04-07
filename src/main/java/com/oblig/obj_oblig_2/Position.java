package com.oblig.obj_oblig_2;

public class Position {
    private double x;
    private double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public boolean equals(Position other) {
        return Math.abs(this.x - other.x) < 0.001 &&
                Math.abs(this.y - other.y) < 0.001;
    }
}