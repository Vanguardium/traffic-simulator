package com.oblig.obj_oblig_2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class TrafficApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException{
        FXMLLoader fxmlLoader = new FXMLLoader(TrafficApplication.class.getResource("traffic-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        stage.setTitle("Traffic Simulation");
        stage.setScene(scene);
        stage.show();
    }

    //Main method to launch the application
    public static void main(String[] args) {
        launch();
    }

    //Method to initialize the simulation
    public void initSimulation() {
        // Initialize the simulation components here
        // For example, create intersections, roads, and cars
        // Set up the initial state of the simulation
    }

    //Method to start the simulation
    public void startSimulation() {
        // Start the simulation loop here
        // Update the state of the simulation at regular intervals
        // Handle user interactions and events
    }

    //Method to stop the simulation
    public void stopSimulation() {
        // Stop the simulation loop here
        // Clean up resources and save the state if necessary
    }
}

