package com.studio.controller;

import com.studio.db.DatabaseManager;
import com.studio.service.ResourceReleaseService;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AiAssistantChatKeyBindingsTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
        Platform.setImplicitExit(false);
    }

    @AfterEach
    void tearDown() {
        try {
            DatabaseManager.getInstance().shutdown();
        } catch (Exception ignored) {}
        ResourceReleaseService.resetForTesting();
        StudioPaths.resetForTesting();
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
            if (error.get() instanceof AssertionError ae) throw ae;
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    public void testChatInputKeyBindings(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);

        runFx(() -> {
            StackPane contentArea = new StackPane();
            TabManager tm = new TabManager(new HBox(), contentArea);
            ViewNavigator.init(contentArea, tm);

            URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
            assertNotNull(resource);
            FXMLLoader loader = new FXMLLoader(resource);
            try {
                Node root = loader.load();
                assertNotNull(root);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            AiAssistantPageController controller = loader.getController();
            assertNotNull(controller);

            TextArea promptArea = controller.getPromptInputArea();
            assertNotNull(promptArea, "promptInputArea must be injected and accessible");
            assertNotNull(controller.getSendBtn(), "sendBtn must be injected and accessible");

            // 1. Shift+Enter: inserts newline at cursor
            promptArea.setText("Line 1");
            promptArea.positionCaret(promptArea.getText().length());

            KeyEvent shiftEnterEvent = new KeyEvent(
                    KeyEvent.KEY_PRESSED,
                    "\r", "\r",
                    KeyCode.ENTER,
                    true,  // shiftDown
                    false, // controlDown
                    false, // altDown
                    false  // metaDown
            );
            promptArea.fireEvent(shiftEnterEvent);
            assertEquals("Line 1\n", promptArea.getText(), "Shift+Enter should insert newline into text area");

            // 2. Alt+Enter: also inserts newline
            KeyEvent altEnterEvent = new KeyEvent(
                    KeyEvent.KEY_PRESSED,
                    "\r", "\r",
                    KeyCode.ENTER,
                    false, // shiftDown
                    false, // controlDown
                    true,  // altDown
                    false  // metaDown
            );
            promptArea.fireEvent(altEnterEvent);
            assertEquals("Line 1\n\n", promptArea.getText(), "Alt+Enter should also insert newline");

            // 3. Plain Enter with empty text: triggers sendPrompt, shows prompt warning, and consumes event without newline
            promptArea.clear();
            KeyEvent enterEvent = new KeyEvent(
                    KeyEvent.KEY_PRESSED,
                    "\r", "\r",
                    KeyCode.ENTER,
                    false, // shiftDown
                    false, // controlDown
                    false, // altDown
                    false  // metaDown
            );
            promptArea.fireEvent(enterEvent);
            assertEquals("", promptArea.getText(), "Plain Enter should not insert a newline into empty prompt");

            // 4. Ctrl+Enter: also triggers sendPrompt and consumes event without newline
            KeyEvent ctrlEnterEvent = new KeyEvent(
                    KeyEvent.KEY_PRESSED,
                    "\r", "\r",
                    KeyCode.ENTER,
                    false, // shiftDown
                    true,  // controlDown
                    false, // altDown
                    false  // metaDown
            );
            promptArea.fireEvent(ctrlEnterEvent);
            assertEquals("", promptArea.getText(), "Ctrl+Enter should not insert newline into empty prompt");

            // 5. Plain Enter when sendBtn is disabled: does not throw, does not insert newline
            controller.getSendBtn().setDisable(true);
            KeyEvent disabledEnterEvent = new KeyEvent(
                    KeyEvent.KEY_PRESSED,
                    "\r", "\r",
                    KeyCode.ENTER,
                    false, // shiftDown
                    false, // controlDown
                    false, // altDown
                    false  // metaDown
            );
            promptArea.fireEvent(disabledEnterEvent);
            assertEquals("", promptArea.getText());
            controller.getSendBtn().setDisable(false);

            // Safely shutdown DB inside test scope
            DatabaseManager.getInstance().shutdown();
        });
    }
}
