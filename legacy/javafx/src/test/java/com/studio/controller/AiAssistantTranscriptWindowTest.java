package com.studio.controller;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
 * Guards the windowed transcript contract.
 *
 * <p>The chat panel is not virtualized: every bubble is a real node, and one layout pass re-measures
 * all of them. A long history therefore used to turn a single streamed line into a ~60ms stall. Only
 * the newest page may live in the scene graph, and reaching the top must reveal the previous page
 * without dropping the conversation.
 */
class AiAssistantTranscriptWindowTest {

    /** Mirrors {@code AiAssistantPageController.TRANSCRIPT_PAGE_SIZE}; the contract is one page. */
    private static final int PAGE_SIZE = 40;
    private static final int TOTAL_MESSAGES = 120;
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
    void onlyTheNewestPageIsInTheSceneGraphAndOlderPagesLoadOnDemand(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage();
            AiChatSession session = page.controller().getCurrentSession();
            for (int i = 0; i < TOTAL_MESSAGES; i++) {
                session.addMessage(new AiChatMessage(i % 2 == 0 ? "user" : "assistant",
                        "Tin nhắn số " + i + " — nội dung ngắn."));
            }

            page.controller().renderChatHistory();
            page.root().applyCss();
            page.root().layout();

            assertEquals(PAGE_SIZE, bubbleCount(page.root()),
                    "Only one page of bubbles may be attached");

            Node bar = page.root().lookup(".ai-transcript-earlier");
            assertNotNull(bar, "The hidden history must be discoverable above the transcript");
            Button loadEarlier = firstButton(bar);
            assertTrue(loadEarlier.getText().contains(String.valueOf(TOTAL_MESSAGES - PAGE_SIZE)),
                    "The row must say how many messages are hidden: " + loadEarlier.getText());

            loadEarlier.fire();
            page.root().applyCss();
            page.root().layout();
            assertEquals(PAGE_SIZE * 2, bubbleCount(page.root()),
                    "One click renders exactly one more page");
            assertNotNull(page.root().lookup(".ai-transcript-earlier"),
                    "A second page is still hidden");

            firstButton(page.root().lookup(".ai-transcript-earlier")).fire();
            page.root().applyCss();
            page.root().layout();
            assertEquals(TOTAL_MESSAGES, bubbleCount(page.root()),
                    "The last page brings the whole conversation back");
            assertNull(page.root().lookup(".ai-transcript-earlier"),
                    "Nothing is hidden any more, so the row must be gone");
        });
    }

    @Test
    void aShortConversationShowsNoLoadEarlierRow(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage();
            AiChatSession session = page.controller().getCurrentSession();
            for (int i = 0; i < 3; i++) {
                session.addMessage(new AiChatMessage("user", "Câu hỏi " + i));
            }

            page.controller().renderChatHistory();
            page.root().applyCss();
            page.root().layout();

            assertEquals(3, bubbleCount(page.root()));
            assertNull(page.root().lookup(".ai-transcript-earlier"));
        });
    }

    private static int bubbleCount(Parent root) {
        return root.lookupAll(".ai-chat-bubble").size();
    }

    private static Button firstButton(Node parent) {
        Node button = parent.lookup(".button");
        assertNotNull(button, "Expected a button inside " + parent);
        assertInstanceOf(Button.class, button);
        return (Button) button;
    }

    private Loaded loadPage() {
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
            return new Loaded(root, controller);
        } catch (Exception e) {
            fail("Failed to load ai_assistant_page.fxml: " + e.getMessage(), e);
            return null;
        }
    }

    private record Loaded(Parent root, AiAssistantPageController controller) {
    }
}
