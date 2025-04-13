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

        FXMLLoader fxmlLoader = new FXMLLoader(TrafficApplication.class.getResource("traffic-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800); // Fixed size
        stage.setTitle("Traffic Simulation");
        stage.setScene(scene);

        // Disable resizing to enforce fixed size
        stage.setResizable(false);

        // Center the application window on the screen
        stage.centerOnScreen();

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

}