package com.oblig.obj_oblig_2;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

public class TrafficController {
    @FXML
    private Canvas canvas;

    @FXML
    public void initialize() {
        // Important: The canvas may be null if FXML binding fails
        if (canvas != null) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            // Initialize drawing context
        } else {
            System.err.println("Canvas is null in initialize()");
        }
    }
}