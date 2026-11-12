package com.studio.controller;

import com.studio.store.SettingsStore;
import com.studio.util.StudioPaths;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the settings form geometry.
 *
 * <p>The form is a two-column {@code GridPane}: field labels on the left, controls plus hints on the
 * right. The hints used to be non-wrapping labels, so column 1 demanded far more width than the
 * dialog had; {@code GridPane} paid for that by squeezing column 0 down to its floor and every label
 * degraded to a single "…" glyph. The form now owns explicit column constraints and every hint wraps,
 * so the label column always keeps its natural width.</p>
 */
class SettingsDialogLayoutTest {

    private static final double EPSILON = 1.5;
    /** A label narrower than this cannot show its text and would render as "…". */
    private static final double STUB_LABEL_WIDTH = 40;
    /** Wrapped text occupies at least this many line boxes when the hint is long. */
    private static final double MULTI_LINE_HEIGHT_RATIO = 1.6;
    private static final long DIALOG_SETTLE_MS = 700;

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized by another test class.
        }
        Platform.setImplicitExit(false);
    }

    @AfterEach
    void tearDown() {
        StudioPaths.resetForTesting();
    }

    @Test
    void fieldLabelsNeverCollapseToAnEllipsis(@TempDir Path tempHome) throws Exception {
        FormSnapshot snapshot = measure("AI", tempHome);

        assertFalse(snapshot.fieldLabels().isEmpty(), "The AI tab must expose field labels");
        for (Label label : snapshot.fieldLabels()) {
            assertTrue(label.getWidth() >= label.prefWidth(-1),
                    "Label \"" + label.getText() + "\" is " + label.getWidth()
                            + "px wide but needs " + label.prefWidth(-1)
                            + "px — the label column was squeezed again");
            if (label.prefWidth(-1) > STUB_LABEL_WIDTH) {
                assertTrue(label.getWidth() > STUB_LABEL_WIDTH,
                        "Label \"" + label.getText() + "\" collapsed to an ellipsis stub");
            }
        }
    }

    @Test
    void theLabelColumnKeepsItsNaturalWidth(@TempDir Path tempHome) throws Exception {
        FormSnapshot snapshot = measure("AI", tempHome);

        assertTrue(snapshot.labelColumnWidth() > STUB_LABEL_WIDTH,
                "The label column collapsed to " + snapshot.labelColumnWidth() + "px");
        assertTrue(snapshot.fieldColumnWidth() > snapshot.labelColumnWidth(),
                "The field column must own the remaining width: fields were " + snapshot.fieldColumnWidth()
                        + "px, labels " + snapshot.labelColumnWidth() + "px");
    }

    @Test
    void longHintsWrapInsteadOfClippingTheirText(@TempDir Path tempHome) throws Exception {
        FormSnapshot snapshot = measure("AI", tempHome);

        assertFalse(snapshot.hints().isEmpty(), "The AI tab must expose hint labels");
        Label tallestWrappedHint = null;
        for (Label hint : snapshot.hints()) {
            if (hint.isWrapText() && hint.getHeight() > hint.getFont().getSize() * MULTI_LINE_HEIGHT_RATIO) {
                tallestWrappedHint = hint;
            }
            assertTrue(hint.isWrapText(), "Hint \"" + shortText(hint) + "\" does not wrap");
            assertTrue(hint.getWidth() <= snapshot.fieldColumnWidth() + EPSILON,
                    "Hint \"" + shortText(hint) + "\" overflows its column");
        }
        assertTrue(tallestWrappedHint != null,
                "At least one long hint must occupy multiple lines instead of running off the dialog");
    }

    /** Opens the dialog (it blocks in its own nested loop), measures, then closes every window. */
    private static FormSnapshot measure(String tab, Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<FormSnapshot> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            PauseTransition settle = new PauseTransition(Duration.millis(DIALOG_SETTLE_MS));
            settle.setOnFinished(e -> {
                try {
                    result.set(readForm());
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    for (Window window : List.copyOf(Window.getWindows())) {
                        window.hide();
                    }
                    done.countDown();
                }
            });
            settle.play();
            try {
                AiSettingsDialog.show(new SettingsStore(), tab);
            } catch (Throwable t) {
                error.set(t);
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "The settings dialog did not settle");
        if (error.get() != null) {
            fail("Measuring the settings dialog threw", error.get());
        }
        return result.get();
    }

    private static FormSnapshot readForm() {
        Window dialogWindow = null;
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null) {
                dialogWindow = window;
            }
        }
        if (dialogWindow == null) {
            throw new IllegalStateException("The settings dialog is not on screen");
        }
        Node root = dialogWindow.getScene().getRoot();

        List<Label> fieldLabels = new ArrayList<>();
        List<Label> hints = new ArrayList<>();
        GridPane grid = null;
        for (Node node : root.lookupAll(".field-label")) {
            if (node instanceof Label label) {
                fieldLabels.add(label);
                if (grid == null && label.getParent() instanceof GridPane parent) {
                    grid = parent;
                }
            }
        }
        for (Node node : root.lookupAll(".settings-hint-label")) {
            if (node instanceof Label label) {
                hints.add(label);
            }
        }
        if (grid == null) {
            throw new IllegalStateException("The settings form grid was not found");
        }
        double labelColumn = 0;
        double fieldColumn = 0;
        for (Node child : grid.getChildren()) {
            Integer column = GridPane.getColumnIndex(child);
            if (column == null) {
                continue;
            }
            if (column == 0 && child instanceof Label label && fieldLabels.contains(label)) {
                labelColumn = Math.max(labelColumn, label.getWidth());
            } else if (column == 1 && child instanceof Region region) {
                fieldColumn = Math.max(fieldColumn, region.getWidth());
            }
        }
        assertTrue(labelColumn > 0 && fieldColumn > 0,
                "Both form columns must be laid out, got " + labelColumn + " / " + fieldColumn);
        return new FormSnapshot(fieldLabels, hints, labelColumn, fieldColumn);
    }

    private static String shortText(Label label) {
        String text = label.getText();
        return text == null || text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }

    private record FormSnapshot(List<Label> fieldLabels, List<Label> hints,
            double labelColumnWidth, double fieldColumnWidth) {
    }
}
