package com.studio.service;

import javafx.application.Platform;
import javafx.scene.control.Label;

/** Thin bridge so pages can update the shell status bar. */
public final class StatusBarService {

    private static Label statusLabel;
    private static Label engineLabel;
    private static Label healedCountLabel;
    private static Label testStatsLabel;

    private static int healedCount = 0;

    private StatusBarService() {}

    public static void setStatusLabel(Label label) {
        statusLabel = label;
    }

    public static void setEngineLabel(Label label) {
        engineLabel = label;
    }

    public static void setHealedCountLabel(Label label) {
        healedCountLabel = label;
    }

    public static void setTestStatsLabel(Label label) {
        testStatsLabel = label;
    }

    public static void set(String message) {
        if (statusLabel == null || message == null) return;
        Runnable task = () -> statusLabel.setText(message);
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public static void ready() {
        set("Sẵn sàng");
    }

    public static void setEngineInfo(String engine) {
        if (engineLabel == null || engine == null) return;
        Runnable task = () -> engineLabel.setText(engine);
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public static void incrementHealed() {
        healedCount++;
        final int count = healedCount;
        if (healedCountLabel == null) return;
        Runnable task = () -> healedCountLabel.setText(count + " healed");
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public static void setTestStats(String stats) {
        if (testStatsLabel == null) return;
        Runnable task = () -> testStatsLabel.setText(stats != null ? stats : "");
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }
}
