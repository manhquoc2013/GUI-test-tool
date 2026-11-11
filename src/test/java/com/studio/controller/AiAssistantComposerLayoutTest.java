package com.studio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the composer layout contract: the toolbar is a single non-wrapping row that never pushes
 * the action buttons off-screen, and the running state is a dedicated strip instead of a relabelled
 * or resized Send button. Both are what stopped the page from re-flowing while an answer streams.
 */
class AiAssistantComposerLayoutTest {

    /** Below the app's minimum window width; the toolbar must still keep its buttons reachable. */
    private static final double[] PAGE_WIDTHS = { 1180, 980, 860, 760, 700 };

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized by another test class.
        }
        Platform.setImplicitExit(false);
    }

    private void runFx(Runnable r) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void testToolbarIsASingleNonWrappingRow() throws Exception {
        runFx(() -> {
            Parent root = loadPage(1180);
            Node toolbar = root.lookup(".page-toolbar");
            assertNotNull(toolbar, "The AI page must expose a page-toolbar");
            assertInstanceOf(HBox.class, toolbar,
                    "A FlowPane toolbar re-wrapped its buttons when a badge changed width");
        });
    }

    @Test
    void testToolbarKeepsOneRowAndItsActionsOnScreenAtEveryWidth() throws Exception {
        runFx(() -> {
            Parent full = loadPage(PAGE_WIDTHS[0]);
            HBox toolbarFull = (HBox) full.lookup(".page-toolbar");
            HBox actions = (HBox) full.lookup(".ai-toolbar-actions");
            assertNotNull(toolbarFull, "page-toolbar must be addressable");
            assertNotNull(actions, "The action group must be addressable");
            double fullWidth = actions.getLayoutBounds().getWidth();
            double singleRowHeight = toolbarFull.getLayoutBounds().getHeight();

            for (double width : PAGE_WIDTHS) {
                Parent root = loadPage(width);
                HBox toolbar = (HBox) root.lookup(".page-toolbar");
                HBox actionsAtWidth = (HBox) root.lookup(".ai-toolbar-actions");
                assertNotNull(toolbar, "page-toolbar missing at width " + width);
                assertNotNull(actionsAtWidth, "ai-toolbar-actions missing at width " + width);

                // A wrapped FlowPane row doubles the toolbar height; centered children of different
                // heights keep the same height even though their layoutY differs by a few pixels.
                assertEquals(singleRowHeight, toolbar.getLayoutBounds().getHeight(), 0.5,
                        "The toolbar wrapped onto a second row at width " + width);

                List<Node> children = toolbar.getChildren();
                assertFalse(children.isEmpty(), "The toolbar must not be empty");
                Node last = children.get(children.size() - 1);
                double lastRight = last.getLayoutX() + last.getLayoutBounds().getWidth();
                assertTrue(lastRight <= width + 0.5,
                        "The last toolbar child overflows the page at width " + width
                                + " (right edge " + lastRight + ")");
                // Shrinking is absorbed by the title and the badges, never by the action buttons:
                // they keep their full size so "Chat mới / Cấu hình AI / Xóa chat" stay clickable.
                assertEquals(fullWidth, actionsAtWidth.getLayoutBounds().getWidth(), 0.5,
                        "The action buttons must not shrink at width " + width);
            }
        });
    }

    @Test
    void testRunningStateLivesInItsOwnStrip() throws Exception {
        runFx(() -> {
            Parent root = loadPage(1180);
            Node strip = root.lookup("#runStatusStrip");
            Node status = root.lookup("#runStatusLabel");
            Button stop = (Button) root.lookup("#stopRunBtn");
            ProgressIndicator spinner = (ProgressIndicator) root.lookup("#generationProgress");
            Node footer = root.lookup(".ai-composer-footer");
            Button send = (Button) root.lookup("#sendBtn");

            assertNotNull(strip, "runStatusStrip must exist");
            assertNotNull(status, "runStatusLabel must exist");
            assertNotNull(stop, "stopRunBtn must exist");
            assertNotNull(spinner, "The run spinner belongs to the status strip");
            assertNotNull(footer, "ai-composer-footer must exist");
            assertNotNull(send, "sendBtn must exist");

            assertFalse(strip.isVisible(), "The run strip is hidden until a turn starts");
            assertFalse(strip.isManaged(), "A hidden run strip must not take space");
            assertNull(footer.lookup("#stopRunBtn"),
                    "The stop button must live in the run strip, not in the wrapping footer");

            // The Send button keeps its label and its width for the whole turn: only its disabled
            // state changes, so the footer never re-flows.
            assertEquals("Gửi (Enter)", send.getText());
            assertTrue(send.getMinWidth() >= 120,
                    "Send must keep a stable minimum width while a turn is running");
        });
    }

    @Test
    void testShowingTheRunStripDoesNotResizeTheComposerButtons() throws Exception {
        runFx(() -> {
            Parent root = loadPage(1180);
            Button send = (Button) root.lookup("#sendBtn");
            Button attach = (Button) root.lookup("#attachFileBtn");
            Node strip = root.lookup("#runStatusStrip");
            Node promptInput = root.lookup("#promptInputArea");
            Node footer = root.lookup(".ai-composer-footer");
            assertNotNull(send, "sendBtn must exist");
            assertNotNull(attach, "attachFileBtn must exist");
            assertNotNull(strip, "runStatusStrip must exist");
            assertNotNull(promptInput, "promptInputArea must exist");
            assertNotNull(footer, "ai-composer-footer must exist");

            double sendWidth = send.getLayoutBounds().getWidth();
            double sendHeight = send.getLayoutBounds().getHeight();
            double attachWidth = attach.getLayoutBounds().getWidth();

            // Simulate a turn starting: the strip becomes visible and the composer re-lays out.
            strip.setManaged(true);
            strip.setVisible(true);
            root.applyCss();
            root.layout();

            assertEquals(sendWidth, send.getLayoutBounds().getWidth(), 0.5,
                    "Send must not resize when the running strip appears");
            assertEquals(sendHeight, send.getLayoutBounds().getHeight(), 0.5,
                    "Send must not change height when the running strip appears");
            assertEquals(attachWidth, attach.getLayoutBounds().getWidth(), 0.5,
                    "The attach button must not resize when the running strip appears");

            // Same coordinate space: layoutBounds are node-local, so compare scene positions.
            Bounds stripBounds = strip.localToScene(strip.getBoundsInLocal());
            Bounds inputBounds = promptInput.localToScene(promptInput.getBoundsInLocal());
            assertTrue(stripBounds.getMaxY() <= inputBounds.getMinY() + 0.5,
                    "The run strip belongs above the prompt input, not inside the footer row");
            assertTrue(stripBounds.getWidth() > 0, "The visible strip must span the composer width");
        });
    }

    private Parent loadPage(double width) {
        try {
            URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
            assertNotNull(resource, "ai_assistant_page.fxml resource not found");
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            assertNotNull(loader.getController(), "AiAssistantPageController must be created");
            // A Control exposes its children through its skin, so the SplitPane's content is only
            // reachable by lookup once the page lives in a scene and its CSS pass created the skins.
            new Scene(root, width, 760);
            root.applyCss();
            root.layout();
            return root;
        } catch (Exception e) {
            fail("Failed to load ai_assistant_page.fxml: " + e.getMessage(), e);
            return null;
        }
    }
}
