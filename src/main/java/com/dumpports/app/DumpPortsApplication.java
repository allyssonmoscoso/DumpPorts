package com.dumpports.app;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Main entry point for the DumpPorts application.
 * This class initializes and starts the JavaFX application.
 */
public class DumpPortsApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(DumpPortsApplication.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainWindow.fxml"));
            Scene scene = new Scene(loader.load(), 1200, 800);
            
            primaryStage.setTitle("DumpPorts - Socket Statistics Visualizer");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> onApplicationClose());
            
            // Set window icon if available
            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.warn("Icon file not found", e);
            }
            
            primaryStage.show();
            logger.info("DumpPorts application started successfully");
        } catch (IOException e) {
            logger.error("Failed to load main window FXML", e);
            System.exit(1);
        }
    }

    private void onApplicationClose() {
        logger.info("DumpPorts application closing");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
