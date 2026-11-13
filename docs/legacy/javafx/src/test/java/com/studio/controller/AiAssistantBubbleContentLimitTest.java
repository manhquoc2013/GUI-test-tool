package com.studio.controller;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.model.ChatDisplayMode;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

/**
 * Guards the bounded-inline-content contract of a chat bubble.
 *
 * <p>Rendering a 25k-character answer inside the scrollable transcript costs ~40ms per layout pass
 * and grows with the whole history, so the bubble shows a bounded head and offers the untouched
 * original in the viewer. "Chi tiết" and "Xem thêm" may never inflate a giant label inline.</p>
 */
class AiAssistantBubbleContentLimitTest {

    /** Mirrors {@code AiAssistantPageController.BUBBLE_INLINE_CHAR_LIMIT}. */
    private static final int INLINE_LIMIT = 4_000;
    private static final int HUGE_CONTENT_CHARS = 20_000;
    private static final double WINDOW_WIDTH = 1024;
    private static final double WINDOW_HEIGHT = 700;

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

    @AfterEach
    void tearDown() {
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
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void detailModeNeverRendersAHugeAnswerInline(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage(ChatDisplayMode.DETAIL);
            page.session().addMessage(new AiChatMessage("assistant", hugeAnswer()));
            page.controller().renderChatHistory();
            page.root().applyCss();
            page.root().layout();

            Node bubble = lastBubble(page.controller());
            Label content = (Label) bubble.lookup(".ai-chat-bubble-content");
            assertNotNull(content, "A bubble must render its content label");
            assertTrue(content.getText().length() <= INLINE_LIMIT + 1,
                    "Inline text must stay bounded, was " + content.getText().length() + " chars");
            assertTrue(content.getText().endsWith(ChatDisplayMode.ELLIPSIS),
                    "A clipped bubble must advertise the cut");
            assertNotNull(buttonWithText(bubble, "Xem toàn bộ"),
                    "The full answer must stay one click away");
            assertNull(buttonWithText(bubble, "Xem thêm"),
                    "A huge answer is not expanded inside the transcript");
        });
    }

    @Test
    void summaryModeAlsoSendsAHugeAnswerToTheViewer(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage(ChatDisplayMode.SUMMARY);
            page.session().addMessage(new AiChatMessage("assistant", hugeAnswer()));
            page.controller().renderChatHistory();
            page.root().applyCss();
            page.root().layout();

            Node bubble = lastBubble(page.controller());
            Label content = (Label) bubble.lookup(".ai-chat-bubble-content");
            assertNotNull(content);
            assertTrue(content.getText().length() <= INLINE_LIMIT + 1,
                    "Even an excerpt stays bounded, was " + content.getText().length());
            assertNotNull(buttonWithText(bubble, "Xem toàn bộ"),
                    "The stored content is larger than the inline budget, so the viewer is offered");
        });
    }

    @Test
    void aShortAnswerStillExpandsInlineWithoutTheViewer(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage(ChatDisplayMode.COMPACT);
            page.session().addMessage(new AiChatMessage("assistant", "Đã sinh 6 test cases cho luồng đăng nhập."));
            page.controller().renderChatHistory();
            page.root().applyCss();
            page.root().layout();

            Node bubble = lastBubble(page.controller());
            Label content = (Label) bubble.lookup(".ai-chat-bubble-content");
            assertNotNull(content);
            assertEquals("Đã sinh 6 test cases cho luồng đăng nhập.", content.getText());
            assertNull(buttonWithText(bubble, "Xem toàn bộ"),
                    "A short answer needs no viewer");
        });
    }

    private static String hugeAnswer() {
        StringBuilder answer = new StringBuilder(HUGE_CONTENT_CHARS + 128);
        int line = 1;
        while (answer.length() < HUGE_CONTENT_CHARS) {
            answer.append("Điểm mạnh của nhóm chức năng ").append(line++)
                    .append(": kiểm thử viên cần xác minh luồng đăng nhập, phân quyền và dữ liệu biên.")
                    .append('\n');
        }
        return answer.toString();
    }

    private static Node lastBubble(AiAssistantPageController controller) {
        java.util.List<Node> children = controller.getChatMessagesBox().getChildren();
        assertFalse(children.isEmpty(), "Expected at least one transcript row");
        Node wrapper = children.get(children.size() - 1);
        Node bubble = wrapper.lookup(".ai-chat-bubble");
        assertNotNull(bubble, "The last transcript row must hold a chat bubble");
        return bubble;
    }

    private static Button buttonWithText(Node scope, String text) {
        for (Node node : scope.lookupAll(".button")) {
            if (node instanceof Button button && text.equals(button.getText())) {
                return button;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Loaded loadPage(ChatDisplayMode mode) {
        try {
            URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
            assertNotNull(resource, "ai_assistant_page.fxml resource not found");
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            AiAssistantPageController controller = loader.getController();
            assertNotNull(controller, "AiAssistantPageController must be created");
            new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            root.applyCss();
            root.layout();

            ComboBox<ChatDisplayMode> modeCombo =
                    (ComboBox<ChatDisplayMode>) root.lookup("#chatDisplayModeCombo");
            assertNotNull(modeCombo, "The density selector must exist");
            modeCombo.setValue(mode);

            return new Loaded(root, controller, controller.getCurrentSession());
        } catch (Exception e) {
            fail("Failed to load ai_assistant_page.fxml: " + e.getMessage(), e);
            return null;
        }
    }

    private record Loaded(Parent root, AiAssistantPageController controller, AiChatSession session) {
    }
}
