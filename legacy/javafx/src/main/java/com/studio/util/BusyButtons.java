package com.studio.util;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.Node;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs background work while showing a small spinner on the primary action button.
 */
public final class BusyButtons {

    private static final AtomicBoolean GLOBAL_BUSY = new AtomicBoolean(false);

    private BusyButtons() {}

    /** True while any BusyButtons job is running. */
    public static boolean isBusy() {
        return GLOBAL_BUSY.get();
    }

    public static <T> void runAsync(
            Button primary,
            String busyText,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Exception> onError,
            Button... alsoDisable) {
        runAsync(primary, busyText, work, onSuccess, onError, null, alsoDisable);
    }

    public static <T> void runAsync(
            Button primary,
            String busyText,
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Exception> onError,
            Runnable afterRestore,
            Button... alsoDisable) {
        Objects.requireNonNull(work, "work");

        if (!GLOBAL_BUSY.compareAndSet(false, true)) {
            return; // already busy
        }

        final String originalText = primary != null ? primary.getText() : null;
        final Node originalGraphic = primary != null ? primary.getGraphic() : null;
        if (primary != null) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(14, 14);
            spinner.getStyleClass().add("btn-spinner");
            primary.setGraphic(spinner);
            if (busyText != null && !busyText.isBlank()) {
                primary.setText(busyText);
            }
            primary.setDisable(true);
        }
        for (Button b : alsoDisable) {
            if (b != null) b.setDisable(true);
        }

        Thread worker = new Thread(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> {
                    restore(primary, originalText, originalGraphic, alsoDisable, afterRestore);
                    if (onSuccess != null) onSuccess.accept(result);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    restore(primary, originalText, originalGraphic, alsoDisable, afterRestore);
                    if (onError != null) onError.accept(ex);
                });
            }
        }, "busy-button");
        worker.setDaemon(true);
        worker.start();
    }

    private static void restore(Button primary, String text, Node graphic,
                                Button[] alsoDisable, Runnable afterRestore) {
        if (primary != null) {
            primary.setText(text);
            primary.setGraphic(graphic);
            primary.setDisable(false);
        }
        for (Button b : alsoDisable) {
            if (b != null) b.setDisable(false);
        }
        GLOBAL_BUSY.set(false);
        if (afterRestore != null) {
            afterRestore.run();
        }
    }
}
