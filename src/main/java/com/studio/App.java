package com.studio;

import com.studio.util.StudioPaths;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        StudioPaths.ensureDirectories();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_shell.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());

        primaryStage.setTitle("🎭 Playwright Automation Studio");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
