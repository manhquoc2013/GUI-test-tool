package com.studio;

import com.studio.util.PlaywrightUtil;
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

        // Configure Playwright browsers directory for bundled portable execution
        java.nio.file.Path bundled = PlaywrightUtil.resolveBrowsersPath();
        if (bundled != null && java.nio.file.Files.isDirectory(bundled)) {
            System.setProperty("playwright.browsers.path", bundled.toAbsolutePath().toString());
        }

        // Asynchronously synchronize workspace and reports into embedded H2 database cache
        Thread syncThread = new Thread(() -> {
            try {
                new com.studio.db.LocalDataIndexService().syncAllFromDisk();
            } catch (Exception ignored) {}
        }, "H2-Startup-Sync");
        syncThread.setDaemon(true);
        syncThread.start();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_shell.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());
        com.studio.service.ThemeService.init(scene);

        primaryStage.setTitle("Playwright Automation Studio");
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
