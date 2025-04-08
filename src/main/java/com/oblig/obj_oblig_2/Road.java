package com.oblig.obj_oblig_2;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Road {
    private double x1, y1, x2, y2;
    private final int WIDTH = 40;

    public Road(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public void draw(GraphicsContext gc) {
        gc.setFill(Color.GRAY);

        // Determine if horizontal or vertical road
        if (y1 == y2) {
            // Horizontal road
            gc.fillRect(x1, y1 - WIDTH/2, x2 - x1, WIDTH);

            // Draw road markings
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            double dashLength = 20;
            double gapLength = 20;

            for (double x = x1; x < x2; x += dashLength + gapLength) {
                gc.strokeLine(x, y1, Math.min(x + dashLength, x2), y1);
            }
        } else {
            // Vertical road
            gc.fillRect(x1 - WIDTH/2, y1, WIDTH, y2 - y1);

            // Draw road markings
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            double dashLength = 20;
            double gapLength = 20;

            for (double y = y1; y < y2; y += dashLength + gapLength) {
                gc.strokeLine(x1, y, x1, Math.min(y + dashLength, y2));
            }
        }
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    public boolean isHorizontal() {
        return y1 == y2;
    }

    public boolean isVertical() {
        return x1 == x2;
    }
}
