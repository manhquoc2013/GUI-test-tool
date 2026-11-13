package com.studio.service;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Toast notification service — bottom-right overlay, capped & deduped so UI stays readable.
 */
public class ToastService {

    public enum ToastType { SUCCESS, INFO, WARN, ERROR }

    private static final int MAX_VISIBLE = 3;
    private static final Duration DEDUPE_WINDOW = Duration.seconds(1.2);

    private static VBox toastContainer;
    private static String lastMessage;
    private static long lastShownAtMs;

    public static void setToastContainer(VBox container) {
        toastContainer = container;
    }

    public static void show(ToastType type, String message) {
        if (toastContainer == null) return;
        final String text = withIcon(type, message);
        final Duration hold = holdDuration(type);

        Runnable showTask = () -> {
            long now = System.currentTimeMillis();
            if (text.equals(lastMessage) && now - lastShownAtMs < DEDUPE_WINDOW.toMillis()) {
                return;
            }
            lastMessage = text;
            lastShownAtMs = now;

            // Cap stack: drop oldest first
            while (toastContainer.getChildren().size() >= MAX_VISIBLE) {
                toastContainer.getChildren().remove(0);
            }

            Label toast = new Label(text);
            toast.setWrapText(true);
            toast.setMaxWidth(340);
            toast.getStyleClass().addAll("toast", "toast-" + type.name().toLowerCase());
            toast.setMouseTransparent(false);
            toast.setUserData(text);
            toastContainer.getChildren().add(toast);

            PauseTransition delay = new PauseTransition(hold);
            delay.setOnFinished(e -> toastContainer.getChildren().remove(toast));
            toast.setOnMouseClicked(e -> {
                delay.stop();
                toastContainer.getChildren().remove(toast);
            });
            delay.play();
        };

        if (Platform.isFxApplicationThread()) {
            showTask.run();
        } else {
            Platform.runLater(showTask);
        }
    }

    private static Duration holdDuration(ToastType type) {
        return switch (type) {
            case ERROR -> Duration.seconds(4.5);
            case WARN -> Duration.seconds(3.5);
            case SUCCESS -> Duration.seconds(2.8);
            case INFO -> Duration.seconds(2.2);
        };
    }

    /** Keep/add leading icon so toast content always has a visual cue. */
    static String withIcon(ToastType type, String message) {
        if (message == null || message.isBlank()) {
            return defaultIcon(type).trim();
        }
        String trimmed = message.stripLeading();
        int first = trimmed.codePointAt(0);
        if (!Character.isLetterOrDigit(first)) {
            return trimmed;
        }
        return defaultIcon(type) + trimmed;
    }

    private static String defaultIcon(ToastType type) {
        return switch (type) {
            case SUCCESS -> "✅ ";
            case INFO -> "ℹ️ ";
            case WARN -> "⚠️ ";
            case ERROR -> "❌ ";
        };
    }

    public static void success(String msg) { show(ToastType.SUCCESS, msg); }
    public static void info(String msg)     { show(ToastType.INFO, msg); }
    public static void warn(String msg)     { show(ToastType.WARN, msg); }
    public static void error(String msg)    { show(ToastType.ERROR, msg); }
}
