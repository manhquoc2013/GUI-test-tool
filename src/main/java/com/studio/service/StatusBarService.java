package com.studio.service;

import javafx.application.Platform;
import javafx.scene.control.Label;

/** Thin bridge so pages can update the shell status bar. */
public final class StatusBarService {

    private static Label statusLabel;

    private StatusBarService() {}

    public static void setStatusLabel(Label label) {
        statusLabel = label;
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
        set("● Sẵn sàng");
    }
}
