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

            // 6. Test Undo restores message content & attached files into input area
            com.studio.model.AiChatMessage userMsg = new com.studio.model.AiChatMessage(
                    "user", "[Fuzzing Bảo Mật]\nKiểm thử SQL Injection vào ô tìm kiếm\n\nĐã đính kèm: spec.docx ");
            userMsg.setRawPrompt("Kiểm thử SQL Injection vào ô tìm kiếm");
            userMsg.setSkill("SECURITY_FUZZING");
            com.studio.model.AiChatMessage.AttachedDocSnapshot docSnap = new com.studio.model.AiChatMessage.AttachedDocSnapshot(
                    "spec.docx",
                    "/tmp/spec.docx",
                    false,
                    "# Test Specifications\nDoc content",
                    "text/markdown",
                    1024L,
                    "/tmp/spec.md"
            );
            userMsg.setAttachedDocsSnapshot(java.util.List.of(docSnap));
            userMsg.setAttachedFiles(java.util.List.of("spec.docx"));

            com.studio.model.AiChatMessage assistantMsg = new com.studio.model.AiChatMessage(
                    "assistant", "Đã phân tích tài liệu và sinh kịch bản kiểm thử.");

            controller.getCurrentSession().addMessage(userMsg);
            controller.getCurrentSession().addMessage(assistantMsg);
            assertEquals(2, controller.getCurrentSession().getMessages().size());

            // Clear prompt area and simulate user having typed something else
            promptArea.setText("temporary draft text");

            // Act: an assistant message is not an undoable target on its own
            controller.handleUndoMessage(assistantMsg);

            // Assert: nothing is removed and no prompt is pulled back into the composer
            assertEquals(2, controller.getCurrentSession().getMessages().size(),
                    "An assistant message must not be undone independently");
            assertEquals("temporary draft text", promptArea.getText(),
                    "Rejected assistant undo must not touch the composer");

            // Act: undo the user prompt - the assistant reply it produced goes with it
            controller.handleUndoMessage(userMsg);

            // Assert: messages removed from session, prompt and files restored to input area
            assertEquals(0, controller.getCurrentSession().getMessages().size(), "Both messages should be removed on undo");
            assertEquals("Kiểm thử SQL Injection vào ô tìm kiếm", promptArea.getText(),
                    "Undone prompt text should be restored into promptInputArea");
            assertEquals(1, controller.getAttachedDocuments().size(),
                    "Undone attached files should be restored into attachedDocuments");
            assertEquals("spec.docx", controller.getAttachedDocuments().get(0).getFileName());
            assertEquals("# Test Specifications\nDoc content", controller.getAttachedDocuments().get(0).getContent());

            // 7. Test Undo on a legacy user message (no rawPrompt)
            com.studio.model.AiChatMessage legacyUserMsg = new com.studio.model.AiChatMessage(
                    "user", "Kiểm thử responsive mobile layout\n\nĐã đính kèm: layout.png ");
            controller.getCurrentSession().addMessage(legacyUserMsg);
            controller.handleUndoMessage(legacyUserMsg);
            assertEquals("Kiểm thử responsive mobile layout", promptArea.getText(),
                    "Legacy user message prompt should be parsed and restored");

            // Safely shutdown DB inside test scope
            DatabaseManager.getInstance().shutdown();
        });
    }
}
