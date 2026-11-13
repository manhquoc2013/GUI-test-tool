package com.studio.controller;

import com.studio.model.ChatDisplayMode;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the result-header layout contract.
 *
 * <p>The right-hand header is a fixed row of the page. When the whole answer was written into it, a
 * long turn wrapped that label into several thousand pixels: the content row grew past the window,
 * the composer and the quick suggestions fell below the fold and the transcript scrollbar no longer
 * had anything to scroll — the page looked frozen. The header must always show a bounded excerpt.</p>
 */
class AiAssistantLongResultLayoutTest {

    private static final double WINDOW_WIDTH = 1024;
    private static final double WINDOW_HEIGHT = 700;
    /** Room for a three-line header plus the panel title; a long answer must not exceed it. */
    private static final double HEADER_HEIGHT_BUDGET = 120;
    private static final double LAYOUT_TOLERANCE = 1.0;
    private static final int HUGE_ANSWER_CHARS = 200_000;

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
    void aHugeAnswerCannotPushTheComposerOrTheTranscriptOffTheWindow(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage();
            page.controller().showResultSummary(hugeAnswer());
            page.root().applyCss();
            page.root().layout();
            page.root().layout();

            Label summary = (Label) page.root().lookup("#resultSummaryLabel");
            assertNotNull(summary, "resultSummaryLabel must exist");
            assertTrue(summary.isWrapText(), "The header excerpt still wraps inside the panel");
            assertTrue(summary.getLayoutBounds().getHeight() <= HEADER_HEIGHT_BUDGET,
                    "A long answer grew the header to " + summary.getLayoutBounds().getHeight()
                            + "px; only a bounded excerpt may be rendered there");

            assertReachable(page.root(), "#sendBtn", "The composer must stay reachable");
            assertReachable(page.root(), ".ai-chat-scroll", "The transcript must stay reachable");
            assertReachable(page.root(), ".ai-composer-footer", "The send row must stay reachable");

            Node rightPanel = page.root().lookup(".ai-right-panel");
            assertNotNull(rightPanel, "ai-right-panel must exist");
            assertTrue(rightPanel.getLayoutBounds().getHeight() <= WINDOW_HEIGHT + LAYOUT_TOLERANCE,
                    "The result panel must not grow past the window (was "
                            + rightPanel.getLayoutBounds().getHeight() + "px)");
        });
    }

    @Test
    void theHeaderRendersAnExcerptTheControllerCannotBypass(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        runFx(() -> {
            Loaded page = loadPage();
            Label summary = (Label) page.root().lookup("#resultSummaryLabel");
            assertNotNull(summary, "resultSummaryLabel must exist");
            assertTrue(summary.textProperty().isBound(),
                    "The header label is bound to the bounded excerpt so no caller can write raw text");

            page.controller().showResultSummary(hugeAnswer());
            assertTrue(summary.getText().length() < HUGE_ANSWER_CHARS,
                    "The header must never hold the whole answer");
            assertTrue(summary.getText().endsWith(ChatDisplayMode.ELLIPSIS),
                    "A clipped excerpt must say so: " + summary.getText());
        });
    }

    @Test
    void excerptKeepsTheHeadOfTheAnswerAndDropsTheTail() {
        String excerpt = AiAssistantPageController.resultSummaryExcerpt(hugeAnswer());
        assertTrue(excerpt.startsWith("Điểm mạnh của nhóm chức năng 1"),
                "The excerpt keeps the opening of the answer: " + excerpt);
        assertTrue(excerpt.length() <= 200, "The excerpt must be bounded, was " + excerpt.length());
        assertTrue(excerpt.endsWith(ChatDisplayMode.ELLIPSIS), "The excerpt must advertise the cut");
    }

    @Test
    void aShortAnswerIsShownInFullAndABlankOneFallsBackToTheHint() {
        String shortAnswer = "Đã sinh 6 test cases cho luồng đăng nhập.";
        assertEquals(shortAnswer, AiAssistantPageController.resultSummaryExcerpt(shortAnswer));
        assertEquals(AiAssistantPageController.resultSummaryExcerpt(null),
                AiAssistantPageController.resultSummaryExcerpt("   \n  "),
                "Blank answers share the same placeholder text");
        assertFalse(AiAssistantPageController.resultSummaryExcerpt(null).isBlank());
    }

    /** Many paragraphs of prose: exactly what a finished orchestrated turn stores as its summary. */
    private static String hugeAnswer() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 400; i++) {
            lines.add("Điểm mạnh của nhóm chức năng " + i
                    + ": kiểm thử viên cần xác minh luồng đăng nhập, phân quyền, thông báo và dữ liệu biên.");
        }
        String answer = String.join("\n", lines);
        return answer.length() >= HUGE_ANSWER_CHARS
                ? answer
                : answer + "x".repeat(HUGE_ANSWER_CHARS - answer.length());
    }

    private static void assertReachable(Parent root, String selector, String message) {
        Node node = root.lookup(selector);
        assertNotNull(node, message + " (" + selector + " not found)");
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMaxY() <= WINDOW_HEIGHT + LAYOUT_TOLERANCE,
                message + ": " + selector + " ends at y=" + bounds.getMaxY()
                        + " but the window is only " + WINDOW_HEIGHT + "px tall");
        assertTrue(bounds.getMinY() >= -LAYOUT_TOLERANCE,
                message + ": " + selector + " starts above the window at y=" + bounds.getMinY());
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
