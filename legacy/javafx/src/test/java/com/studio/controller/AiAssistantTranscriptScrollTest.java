package com.studio.controller;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the transcript viewport contract.
 *
 * <p>A rebuilt transcript has to open on the newest message: the position was written before the skin
 * had measured the fresh bubbles, the write was clamped back to the top, and the reader was left on
 * the oldest page. Paging back must then keep the message under the cursor in place instead of
 * jumping, and the wheel must ease from wherever the viewport actually is after such a clamp.</p>
 */
class AiAssistantTranscriptScrollTest {

    private static final int PAGE_SIZE = 40;
    private static final int TOTAL_MESSAGES = 120;
    private static final double WINDOW_WIDTH = 1024;
    private static final double WINDOW_HEIGHT = 700;
    private static final double POSITION_EPSILON = 1e-6;
    /** Mirrors the controller's pin threshold; a reader this far from the bottom is reading history. */
    private static final double CHAT_BOTTOM_PIN_TOLERANCE = 0.02;

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

    @Test
    void aRebuiltTranscriptOpensOnTheNewestMessage(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        Page page = openPageWithHistory(tempHome);
        try {
            ScrollPane pane = page.chatScroll();
            assertTrue(pane.getViewportBounds().getHeight() > 0, "The stage must be shown for a real viewport");
            assertEquals(1.0, pane.getVvalue(), POSITION_EPSILON,
                    "A rebuilt transcript must open at the newest message, not at the oldest page");
            assertTrue(page.controller().isChatPinnedToBottom(),
                    "The transcript has to count as pinned so the next stream keeps following it");
        } finally {
            page.close();
        }
    }

    @Test
    void aNewMessageIsFollowedWhileTheReaderIsAtTheBottom(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        Page page = openPageWithHistory(tempHome);
        try {
            ScrollPane pane = page.chatScroll();
            assertEquals(1.0, pane.getVvalue(), POSITION_EPSILON, "Precondition: settled at the bottom");

            AiChatSession session = page.controller().getCurrentSession();
            StringBuilder answer = new StringBuilder();
            for (int line = 0; line < 60; line++) {
                answer.append("Dòng thứ ").append(line).append(" của câu trả lời dài.\n");
            }
            // A big final answer: appending it drops the ratio far below the pin threshold even though
            // the reader never moved, which is exactly what used to stop the follow for good.
            runFx(() -> {
                session.addMessage(new AiChatMessage("assistant", answer.toString()));
                page.controller().appendMissingTranscriptBubbles();
            });
            pump(30);

            assertEquals(1.0, pane.getVvalue(), 0.005,
                    "A long answer appended while the reader is at the bottom must still be followed");
        } finally {
            page.close();
        }
    }

    @Test
    void aNewMessageIsNotFollowedAfterTheReaderScrolledUp(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        Page page = openPageWithHistory(tempHome);
        try {
            ScrollPane pane = page.chatScroll();
            for (int i = 0; i < 3; i++) {
                fireWheelUp(pane);
                pump(10);
            }
            double readingAt = pane.getVvalue();
            assertTrue(readingAt < 1.0 - 0.05, "Precondition: the reader moved away from the bottom");
            Node anchor = firstBubble(page.root());
            double anchorY = anchor.localToScene(anchor.getBoundsInLocal()).getMinY();

            AiChatSession session = page.controller().getCurrentSession();
            runFx(() -> {
                session.addMessage(new AiChatMessage("assistant", "Câu trả lời mới.\n".repeat(40)));
                page.controller().appendMissingTranscriptBubbles();
            });
            pump(20);

            assertTrue(pane.getVvalue() < 1.0 - CHAT_BOTTOM_PIN_TOLERANCE,
                    "While reading an earlier message, the transcript must not be dragged to the newest one");
            double anchorYAfter = anchor.localToScene(anchor.getBoundsInLocal()).getMinY();
            assertEquals(anchorY, anchorYAfter, 2.0,
                    "The message being read must stay where it was when content is appended below it");
        } finally {
            page.close();
        }
    }

    @Test
    void returningToTheBottomResumesFollowingNewMessages(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        Page page = openPageWithHistory(tempHome);
        try {
            ScrollPane pane = page.chatScroll();
            for (int i = 0; i < 3; i++) {
                fireWheelUp(pane);
                pump(10);
            }
            assertTrue(pane.getVvalue() < 1.0 - CHAT_BOTTOM_PIN_TOLERANCE,
                    "Precondition: the reader left the bottom");
            for (int i = 0; i < 5; i++) {
                fireWheelDown(pane);
                pump(10);
            }
            assertEquals(1.0, pane.getVvalue(), 0.005, "Precondition: the reader scrolled back to the bottom");

            AiChatSession session = page.controller().getCurrentSession();
            runFx(() -> {
                session.addMessage(new AiChatMessage("assistant", "Câu trả lời mới.\n".repeat(40)));
                page.controller().appendMissingTranscriptBubbles();
            });
            pump(30);

            assertEquals(1.0, pane.getVvalue(), 0.005,
                    "Once the reader is back at the bottom, new messages must be followed again");
        } finally {
            page.close();
        }
    }

    @Test
    void theWheelEasesUpwardsFromTheBottom(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        Page page = openPageWithHistory(tempHome);
        try {
            ScrollPane pane = page.chatScroll();
            double start = pane.getVvalue();
            fireWheelUp(pane);
            pump(12);
            double afterFirst = pane.getVvalue();
            assertTrue(afterFirst < start,
                    "One wheel-up must move the transcript towards the older messages (was " + afterFirst + ")");
            assertTrue(afterFirst > 0, "One notch must not teleport to the very top");

            fireWheelUp(pane);
            pump(12);
            assertTrue(pane.getVvalue() < afterFirst, "A second notch keeps moving up");
        } finally {
            page.close();
        }
    }

    @Test
    void pagingBackKeepsTheMessageTheReaderIsLookingAt(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        Page page = openPageWithHistory(tempHome);
        try {
            ScrollPane pane = page.chatScroll();
            runFx(() -> pane.setVvalue(0));
            pump(2);

            Button loadEarlier = (Button) page.root().lookup(".ai-transcript-earlier .button");
            assertNotNull(loadEarlier, "A windowed transcript must offer the previous page");
            Node anchor = firstBubble(page.root());
            Bounds before = anchor.localToScene(anchor.getBoundsInLocal());
            double contentHeightBefore = pane.getContent().getLayoutBounds().getHeight();

            runFx(loadEarlier::fire);
            pump(3);

            assertEquals(PAGE_SIZE * 2, page.root().lookupAll(".ai-chat-bubble").size(),
                    "One click renders exactly one more page");
            double contentHeightAfter = pane.getContent().getLayoutBounds().getHeight();
            assertTrue(contentHeightAfter > contentHeightBefore, "The page was prepended above the viewport");

            double added = contentHeightAfter - contentHeightBefore;
            double viewport = pane.getViewportBounds().getHeight();
            double expected = added / (contentHeightAfter - viewport);
            assertEquals(expected, pane.getVvalue(), 0.01,
                    "The scroll position must grow by exactly the inserted height, otherwise the reader "
                            + "sees a different message after paging back");

            Bounds after = anchor.localToScene(anchor.getBoundsInLocal());
            assertEquals(before.getMinY(), after.getMinY(), 2.0,
                    "The message the reader was on must stay where it was on screen");
        } finally {
            page.close();
        }
    }

    @Test
    void aScanFromAnotherPageIsNeverReused() {
        String dom = "[{\"tag\":\"button\",\"text\":\"Đăng nhập\"}]";
        assertTrue(AiAssistantPageController.domReusableFor(dom, "https://a.com/login", "https://a.com/login"),
                "Same page, same scan");
        assertTrue(AiAssistantPageController.domReusableFor(dom, "https://a.com/login/", "https://a.com/login"),
                "A trailing slash is the same page");
        assertFalse(AiAssistantPageController.domReusableFor(dom, "https://a.com/login", "https://b.com/login"),
                "Another host must be scanned again, not served the previous selectors");
        assertFalse(AiAssistantPageController.domReusableFor(dom, null, "https://a.com/login"),
                "A scan with no recorded source cannot be trusted for a named target");
        assertTrue(AiAssistantPageController.domReusableFor(dom, "https://a.com/login", null),
                "A follow-up turn without a URL keeps using the conversation's page");
        assertFalse(AiAssistantPageController.domReusableFor("[]", "https://a.com", "https://a.com"),
                "An empty scan is not a usable DOM");
        assertFalse(AiAssistantPageController.domReusableFor(null, "https://a.com", "https://a.com"));
    }

    private Page openPageWithHistory(Path tempHome) throws InterruptedException {
        AtomicReference<Page> loaded = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
                assertNotNull(resource, "ai_assistant_page.fxml resource not found");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                AiAssistantPageController controller = loader.getController();
                AiChatSession session = controller.getCurrentSession();
                for (int i = 0; i < TOTAL_MESSAGES; i++) {
                    session.addMessage(new AiChatMessage(i % 2 == 0 ? "user" : "assistant",
                            "Tin nhắn số " + i + " — nội dung đủ dài để chiếm vài dòng trong khung chat."));
                }
                controller.renderChatHistory();
                root.applyCss();
                root.layout();

                ScrollPane pane = (ScrollPane) root.lookup("#chatScroll");
                assertNotNull(pane, "chatScroll must exist");
                loaded.set(new Page(root, controller, pane, stage));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out loading the assistant page");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("Failed to open the assistant page", error.get());
        }
        // Let the deferred bottom settle run, exactly like the real app's next pulse.
        pump(3);
        return loaded.get();
    }

    private static void fireWheelUp(ScrollPane pane) throws InterruptedException {
        fireWheel(pane, 120);
    }

    private static void fireWheelDown(ScrollPane pane) throws InterruptedException {
        fireWheel(pane, -120);
    }

    /** Fires one wheel notch on a bubble, i.e. the way a real gesture enters the transcript. */
    private static void fireWheel(ScrollPane pane, double deltaY) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Node target = firstBubble(pane.getContent());
                ScrollEvent event = new ScrollEvent(ScrollEvent.SCROLL, 200, 200, 200, 200,
                        false, false, false, false, false, false,
                        0, deltaY, 0, deltaY,
                        ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                        ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
                Event.fireEvent(target, event);
            } finally {
                latch.countDown();
            }
        });
        latch.await(10, TimeUnit.SECONDS);
    }

    private static Node firstBubble(Node content) {
        Node bubble = content.lookup(".ai-chat-bubble");
        assertNotNull(bubble, "Expected at least one rendered bubble");
        return bubble;
    }

    private static void pump(int frames) throws InterruptedException {
        for (int i = 0; i < frames; i++) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
            Thread.sleep(20);
        }
    }

    /** Runs FX-thread work and rethrows its failure on the test thread. */
    private static void runFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for the FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("FX task failed", error.get());
        }
    }

    private record Page(Parent root, AiAssistantPageController controller, ScrollPane chatScroll, Stage stage) {
        /** Closing a window is FX-thread work; calling it from the test thread would throw. */
        void close() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    stage.close();
                } finally {
                    latch.countDown();
                }
            });
            latch.await(10, TimeUnit.SECONDS);
        }
    }
}
