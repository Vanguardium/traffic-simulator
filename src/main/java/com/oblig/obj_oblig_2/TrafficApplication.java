package com.oblig.obj_oblig_2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class TrafficApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        try {
            ConfigLoader.initialize("src/main/resources/com/oblig/obj_oblig_2/default-map.json");
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }

        ConfigLoader config = ConfigLoader.getInstance();

        FXMLLoader fxmlLoader = new FXMLLoader(TrafficApplication.class.getResource("traffic-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), config.getWindowWidth(), config.getWindowHeight());
        stage.setTitle("Traffic Simulation");
        stage.setScene(scene);
        stage.show();
    }

    //Main method to launch the application
    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                ConfigLoader.initialize(args[0]);
                System.out.println("Loaded map: " + args[0]);
            } catch (IOException e) {
                System.err.println("Failed to load map: " + e.getMessage());
            }
        }
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

//can you pull out all numbers and put them in to a config file
//and use them in the code instead of hardcoded numbers
//is it possible to just place the intersection to make the roads follow from that as they need to be horizontal or vertical