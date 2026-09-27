package com.dumpports.app;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import com.dumpports.ui.MainWindowController;

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
            MainWindowController controller = loader.getController();
            
            primaryStage.setTitle("DumpPorts - Socket Statistics Visualizer");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> {
                if (controller != null) {
                    controller.shutdown();
                }
                onApplicationClose();
            });
            
            // Set window icon if available
            try {
                InputStream iconStream = getClass().getResourceAsStream("/images/icon.png");
                if (iconStream != null) {
                    primaryStage.getIcons().add(new Image(iconStream));
                } else {
                    logger.debug("Icon resource /images/icon.png not found; skipping window icon");
                }
            } catch (Exception e) {
                logger.warn("Could not load window icon", e);
            }
            
            primaryStage.show();
            logger.info("DumpPorts application started successfully");

            // Ask the user for root privileges once the window is visible.
            if (controller != null) {
                controller.promptElevationOnStartup();
            }
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
