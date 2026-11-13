package com.studio.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the ComboBox chrome split between the control and its dropdown.
 *
 * <p>The sheet used to style {@code .combo-box .list-cell}, which matches every cell *inside* the
 * popup as well: each option inherited the field's 1px frame, 6px radius and padding, so the
 * dropdown rendered as a column of bordered boxes with a focus rail on the selected row. The frame
 * now belongs to the control and only the button cell ({@code > .list-cell}) inherits its geometry.</p>
 */
class ComboPopupStyleTest {

    private static final double EPSILON = 1.5;
    private static final double SCENE_WIDTH = 420;
    private static final double SCENE_HEIGHT = 280;

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

    @Test
    void popupRowsCarryNoFieldFrameInEitherTheme() throws Exception {
        for (String sheet : new String[]{"studio.css", "studio-light.css"}) {
            List<Region> popupCells = withCombo(sheet, ComboProbe::openPopupAndReadCells);
            assertFalse(popupCells.isEmpty(), "The " + sheet + " popup exposed no option rows");
            for (Region cell : popupCells) {
                assertEquals(0.0, borderWidth(cell),
                        "An option row in the " + sheet + " dropdown is painted with a field frame: "
                                + cell.getBorder());
            }
        }
    }

    @Test
    void theButtonCellDoesNotPaintASecondFrameInsideTheControl() throws Exception {
        for (String sheet : new String[]{"studio.css", "studio-light.css"}) {
            Region buttonCell = withCombo(sheet, ComboProbe::readButtonCell);
            assertEquals(0.0, borderWidth(buttonCell),
                    "The " + sheet + " button cell duplicates the ComboBox border");
        }
    }

    @Test
    void aComboLinesUpWithATextFieldInTheSameForm() throws Exception {
        for (String sheet : new String[]{"studio.css", "studio-light.css"}) {
            for (boolean inDialog : new boolean[]{false, true}) {
                double[] heights = withCombo(sheet, inDialog, probe -> new double[]{
                        probe.combo().getHeight(), probe.textField().getHeight()});
                assertEquals(heights[1], heights[0], EPSILON,
                        "In " + sheet + (inDialog ? " (dialog)" : "") + " a combo is " + heights[0]
                                + "px tall next to a " + heights[1]
                                + "px text field, so the form row looks broken");
            }
        }
    }

    /** The selected row must be a flat fill, without the list's 3px selection rail. */
    @Test
    void theSelectedRowIsAFlatFill() throws Exception {
        for (String sheet : new String[]{"studio.css", "studio-light.css"}) {
            Boolean flat = withCombo(sheet, probe -> {
                List<Region> cells = probe.openPopupAndReadCells();
                Region selected = null;
                for (Region cell : cells) {
                    if (cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("selected"))) {
                        selected = cell;
                    }
                }
                assertNotNull(selected, "The " + sheet + " popup selected none of its rows");
                boolean hasFill = selected.getBackground() != null
                        && !selected.getBackground().getFills().isEmpty();
                return hasFill && borderWidth(selected) == 0;
            });
            assertTrue(flat, "The selected option in " + sheet + " is not a flat, filled row");
        }
    }

    private static double borderWidth(Region region) {
        Border border = region.getBorder();
        if (border == null || border.getStrokes().isEmpty()) {
            return 0;
        }
        double total = 0;
        for (BorderStroke stroke : border.getStrokes()) {
            BorderWidths widths = stroke.getWidths();
            total += widths.getTop() + widths.getRight() + widths.getBottom() + widths.getLeft();
        }
        return total;
    }

    private <T> T withCombo(String stylesheet, ComboProbe.Read<T> read) throws Exception {
        return withCombo(stylesheet, false, read);
    }

    private <T> T withCombo(String stylesheet, boolean inDialog, ComboProbe.Read<T> read)
            throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                TextField field = new TextField("Trùng độ cao");
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll("Mặc định (Playwright Chromium)", "Chromium (Playwright)",
                        "Firefox (Playwright)");
                combo.setValue(combo.getItems().get(0));
                combo.setMaxWidth(Double.MAX_VALUE);
                field.setMaxWidth(Double.MAX_VALUE);

                VBox form = new VBox(10, field, combo);
                form.setPadding(new Insets(16));
                if (inDialog) {
                    form.getStyleClass().add("dialog-pane");
                }
                Scene scene = new Scene(form, SCENE_WIDTH, SCENE_HEIGHT);
                URL css = getClass().getResource("/css/" + stylesheet);
                assertNotNull(css, "stylesheet not found: " + stylesheet);
                scene.getStylesheets().add(css.toExternalForm());
                stage.setScene(scene);
                stage.show();
                form.applyCss();
                layoutTree(form);
                form.applyCss();
                layoutTree(form);

                ComboProbe probe = new ComboProbe(combo, field);
                result.set(read.read(probe));
                closePopups();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out measuring the ComboBox");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError assertion) {
                throw assertion;
            }
            fail("ComboBox probe threw for " + stylesheet, error.get());
        }
        return result.get();
    }

    /** Closes every open popup so the next probe starts on a clean toolkit. */
    private static void closePopups() {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window instanceof PopupWindow popup && popup.isShowing()) {
                popup.hide();
            }
        }
    }

    private record ComboProbe(ComboBox<String> combo, TextField textField) {

        @FunctionalInterface
        private interface Read<T> {
            T read(ComboProbe probe);
        }

        Region readButtonCell() {
            for (Node child : combo.getChildrenUnmodifiable()) {
                if (child instanceof Region region && child.getStyleClass().contains("list-cell")) {
                    return region;
                }
            }
            throw new AssertionError("The ComboBox exposes no button cell");
        }

        List<Region> openPopupAndReadCells() {
            combo.show();
            // The popup builds its virtual flow on the next pulse.
            layoutTree(combo.getScene().getRoot());
            Parent popupRoot = findPopupRoot();
            popupRoot.applyCss();
            layoutTree(popupRoot);
            List<Region> cells = new ArrayList<>();
            for (Node node : popupRoot.lookupAll(".list-cell")) {
                if (node instanceof Region cell && cell.isVisible()) {
                    cells.add(cell);
                }
            }
            return cells;
        }

        /** @return the content root of the combo's popup window, never the owning stage. */
        private Parent findPopupRoot() {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (!(window instanceof PopupWindow popup) || !popup.isShowing()) {
                    continue;
                }
                Parent root = popup.getScene() == null ? null : popup.getScene().getRoot();
                if (root != null && !root.lookupAll(".combo-box-popup").isEmpty()) {
                    return root;
                }
            }
            throw new AssertionError("The ComboBox popup did not open");
        }
    }

    private static void layoutTree(Node node) {
        if (node instanceof Parent parent) {
            parent.layout();
            for (Node child : parent.getChildrenUnmodifiable()) {
                layoutTree(child);
            }
        }
    }
}
