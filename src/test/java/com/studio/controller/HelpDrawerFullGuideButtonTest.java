package com.studio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T5: the help drawer must lead with a working entry point to the full user guide.
 * The button is only inspected (never fired) so no dialog is opened during the test run.
 */
class HelpDrawerFullGuideButtonTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
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
        assertTrue(latch.await(20, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) throw ae;
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void helpDrawerStartsWithFullGuideButton() throws Exception {
        runFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_shell.fxml"));
            Parent root;
            try {
                root = loader.load();
            } catch (java.io.IOException e) {
                fail("main_shell.fxml failed to load", e);
                return;
            }
            Object controller = loader.getController();
            assertTrue(controller instanceof MainShellController,
                    "controller is not MainShellController: " + controller);

            // The drawer body is a private @FXML field; reading it via reflection keeps the
            // test independent of the node id while still proving what the user sees.
            VBox body = readHelpContent((MainShellController) controller);
            assertNotNull(body, "#helpContent was not injected into MainShellController");
            assertFalse(body.getChildren().isEmpty(), "help drawer has no content");

            Node first = body.getChildren().get(0);
            assertTrue(first instanceof Button, "first help entry is not a button: " + first);
            Button fullGuide = (Button) first;
            assertEquals("📖 Mở hướng dẫn đầy đủ (USER_GUIDE.md)", fullGuide.getText());
            assertNotNull(fullGuide.getOnAction(), "full-guide button has no action wired");
            assertTrue(fullGuide.getStyleClass().contains("btn-primary"),
                    "full-guide entry should be styled as the primary action");

            // The quick-reference summary must survive below the new entry point.
            assertTrue(body.getChildren().size() > 3,
                    "quick reference content was removed from the help drawer");
        });
    }

    private static VBox readHelpContent(MainShellController controller) {
        try {
            var field = MainShellController.class.getDeclaredField("helpContent");
            field.setAccessible(true);
            Object value = field.get(controller);
            return value instanceof VBox v ? v : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
