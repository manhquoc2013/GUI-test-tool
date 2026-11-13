package com.studio.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UiIconsTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already started
        }
    }

    @Test
    void testIconCreation() {
        Node icon = UiIcons.icon(UiIcons.RECORD, 16);
        assertNotNull(icon);
        assertTrue(icon.lookupAll(".ui-svg-icon").size() + icon.getStyleClass().size() > 0,
                "The icon must opt into CSS tinting instead of a hard-coded colour");
    }

    @Test
    void testSetGraphicOnButton() {
        Button btn = new Button("Ghi hình");
        UiIcons.setGraphic(btn, UiIcons.RECORD, 14);
        assertNotNull(btn.getGraphic());
    }

    @Test
    void themedGraphicCarriesExactlyOneColourClass() {
        Button btn = new Button();
        UiIcons.setThemedGraphic(btn, UiIcons.PLAY, 14, UiIcons.STYLE_SUCCESS);
        assertTrue(btn.getStyleClass().contains(UiIcons.STYLE_SUCCESS));
        assertNotNull(btn.getGraphic());

        UiIcons.setThemedGraphic(btn, UiIcons.TRASH, 14, UiIcons.STYLE_DANGER);
        assertTrue(btn.getStyleClass().contains(UiIcons.STYLE_DANGER));
        assertFalse(btn.getStyleClass().contains(UiIcons.STYLE_SUCCESS),
                "Restyling must drop the previous colour class instead of stacking both");
    }

    @Test
    void themedGraphicTakesItsFillFromTheStylesheet() throws Exception {
        runFx(() -> {
            Button danger = new Button();
            UiIcons.setThemedGraphic(danger, UiIcons.TRASH, 16, UiIcons.STYLE_DANGER);
            Button plain = new Button();
            UiIcons.setGraphic(plain, UiIcons.TRASH, 16);

            Pane root = new Pane(danger, plain);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());
            root.applyCss();
            root.layout();

            Color dangerFill = iconFill(danger);
            Color defaultFill = iconFill(plain);
            assertNotNull(dangerFill, "The icon must receive a fill from the stylesheet");
            assertEquals(Color.web("#f7768e"), dangerFill,
                    "STYLE_DANGER must resolve the -color-danger-emphasis token, not stay the default ink");
            assertNotEquals(defaultFill, dangerFill,
                    "A themed icon must differ from an untinted one, otherwise the style class did nothing");
        });
    }

    /** @return the resolved fill of the SVG path inside a control's graphic, or null when untinted. */
    private static Color iconFill(Button button) {
        Node graphic = button.getGraphic();
        Node svg = graphic instanceof Parent parent ? parent.lookup(".ui-svg-icon") : graphic;
        return svg instanceof SVGPath path && path.getFill() instanceof Color color ? color : null;
    }

    private static void runFx(Runnable task) throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "Timed out waiting for the FX thread");
        if (failure.get() != null) {
            if (failure.get() instanceof AssertionError error) {
                throw error;
            }
            fail("FX task threw", failure.get());
        }
    }

    @Test
    void testConstantPathsNotNullOrEmpty() {
        assertNotNull(UiIcons.RECORD);
        assertFalse(UiIcons.RECORD.isBlank());
        assertNotNull(UiIcons.PLAY);
        assertNotNull(UiIcons.SAVE);
        assertNotNull(UiIcons.AI_SPARKLES);
        assertNotNull(UiIcons.SUN);
        assertNotNull(UiIcons.MOON);
        assertNotNull(UiIcons.COPY);
        assertNotNull(UiIcons.UNDO);
        assertFalse(UiIcons.UNDO.isBlank());
        assertNotNull(UiIcons.SETTINGS);
        assertNotNull(UiIcons.MENU);
        assertNotNull(UiIcons.FOLDER);
        assertNotNull(UiIcons.FILE);
        assertNotNull(UiIcons.CLIP);
    }
}
