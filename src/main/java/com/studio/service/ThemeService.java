package com.studio.service;

import com.studio.store.SettingsStore;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages runtime theme switching between Dark (Tokyo Night) and Light (VS Code / GitHub Light) themes.
 */
public final class ThemeService {

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    private static final String LIGHT_CSS_PATH = "/css/studio-light.css";

    private static String currentTheme = THEME_DARK;
    private static Scene primaryScene;
    private static final SettingsStore settingsStore = new SettingsStore();
    private static final List<Consumer<String>> themeChangeListeners = new ArrayList<>();

    private ThemeService() {}

    /**
     * Initialize theme from saved settings.
     */
    public static void init(Scene scene) {
        primaryScene = scene;
        try {
            SettingsStore.Settings s = settingsStore.load();
            currentTheme = s.getTheme();
        } catch (IOException e) {
            currentTheme = THEME_DARK;
        }
        applyCurrentTheme(scene);
        notifyListeners();
    }

    public static String getCurrentTheme() {
        return currentTheme;
    }

    public static boolean isDark() {
        return !THEME_LIGHT.equalsIgnoreCase(currentTheme);
    }

    public static void addListener(Consumer<String> listener) {
        if (listener != null && !themeChangeListeners.contains(listener)) {
            themeChangeListeners.add(listener);
        }
    }

    public static void removeListener(Consumer<String> listener) {
        themeChangeListeners.remove(listener);
    }

    /**
     * Toggle between dark and light themes.
     */
    public static String toggleTheme() {
        String newTheme = isDark() ? THEME_LIGHT : THEME_DARK;
        setTheme(newTheme);
        return newTheme;
    }

    /**
     * Switch theme and persist to settings.
     */
    public static void setTheme(String theme) {
        currentTheme = THEME_LIGHT.equalsIgnoreCase(theme) ? THEME_LIGHT : THEME_DARK;
        if (primaryScene != null) {
            applyCurrentTheme(primaryScene);
        }
        // Save preference asynchronously
        Thread t = new Thread(() -> {
            try {
                SettingsStore.Settings s = settingsStore.load();
                s.setTheme(currentTheme);
                settingsStore.save(s);
            } catch (IOException ignored) {}
        }, "Save-Theme-Worker");
        t.setDaemon(true);
        t.start();

        // Notify UI listeners
        notifyListeners();
    }

    private static void notifyListeners() {
        Runnable task = () -> {
            for (Consumer<String> listener : themeChangeListeners) {
                try {
                    listener.accept(currentTheme);
                } catch (Exception ignored) {}
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /**
     * Apply the active theme stylesheet to any scene (e.g. dialogs, popups).
     */
    public static void applyThemeToScene(Scene scene) {
        if (scene == null) return;
        applyCurrentTheme(scene);
    }

    /**
     * Apply active theme to a Dialog.
     */
    public static void applyThemeToDialog(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) return;
        DialogPane pane = dialog.getDialogPane();
        applyCurrentTheme(pane.getStylesheets());
        Scene scene = pane.getScene();
        if (scene != null) {
            applyCurrentTheme(scene);
        } else {
            dialog.setOnShowing(ev -> {
                if (dialog.getDialogPane().getScene() != null) {
                    applyCurrentTheme(dialog.getDialogPane().getScene());
                }
            });
        }
    }

    private static void applyCurrentTheme(Scene scene) {
        if (scene == null) return;
        applyCurrentTheme(scene.getStylesheets());
    }

    private static void applyCurrentTheme(List<String> stylesheets) {
        if (stylesheets == null) return;
        URL lightUrl = ThemeService.class.getResource(LIGHT_CSS_PATH);
        if (lightUrl == null) return;
        String lightForm = lightUrl.toExternalForm();

        if (THEME_LIGHT.equalsIgnoreCase(currentTheme)) {
            if (!stylesheets.contains(lightForm)) {
                stylesheets.add(lightForm);
            }
        } else {
            stylesheets.remove(lightForm);
        }
    }
}

