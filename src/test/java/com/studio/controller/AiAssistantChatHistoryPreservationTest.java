package com.studio.controller;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.store.AiChatHistoryStore;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AiAssistantChatHistoryPreservationTest {

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
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) throw ae;
            fail("FX task threw exception", error.get());
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        StudioPaths.resetForTesting();
    }

    @Test
    public void testChatHistoryPreservedOnReloadTargetsAndTabSwitch(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);

        // Prepare dummy project structure on disk: data/project/WebTests/LoginSuite
        Path projectDir = StudioPaths.getProjectDir();
        Path groupDir = projectDir.resolve("WebTests");
        Path suiteDir = groupDir.resolve("LoginSuite");
        Files.createDirectories(suiteDir);

        runFx(() -> {
            try {
                URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
                assertNotNull(resource);
                FXMLLoader loader = new FXMLLoader(resource);
                Node root = loader.load();
                assertNotNull(root);

                AiAssistantPageController controller = loader.getController();
                assertNotNull(controller);

                // Verify targets loaded
                controller.reloadTargets();
                assertEquals("WebTests", controller.getTargetGroupCombo().getValue());
                assertEquals("LoginSuite", controller.getTargetSuiteCombo().getValue());

                // Simulate chatting with AI: create/add messages to currentSession
                AiChatSession session = controller.getCurrentSession();
                assertNotNull(session);
                String originalSessionId = session.getId();

                AiChatMessage userMsg = new AiChatMessage("user", "Hãy tạo kịch bản test đăng nhập");
                AiChatMessage aiMsg = new AiChatMessage("assistant", "Đây là kịch bản test đăng nhập Playwright");
                session.addMessage(userMsg);
                session.addMessage(aiMsg);

                // Save session to disk
                AiChatHistoryStore store = new AiChatHistoryStore();
                store.saveSession(suiteDir, session);
                controller.refreshSessionsList(originalSessionId);

                assertEquals(2, controller.getCurrentSession().getMessages().size());
                assertEquals("user", controller.getCurrentSession().getMessages().get(0).getRole());
                assertEquals("assistant", controller.getCurrentSession().getMessages().get(1).getRole());

                // Now simulate switching tabs / navigating away and back:
                // ViewNavigator.onPageShown(Page.AI_ASSISTANT) calls ai.reloadTargets()
                controller.reloadTargets();

                // Assert that currentSession and its messages are 100% PRESERVED
                assertNotNull(controller.getCurrentSession(), "Current session must not be null");
                assertEquals(originalSessionId, controller.getCurrentSession().getId(), "Session ID must be preserved");
                assertEquals(2, controller.getCurrentSession().getMessages().size(), "Chat messages must NOT be wiped");
                assertEquals("Hãy tạo kịch bản test đăng nhập", controller.getCurrentSession().getMessages().get(0).getContent());
                assertEquals("Đây là kịch bản test đăng nhập Playwright", controller.getCurrentSession().getMessages().get(1).getContent());

                // Assert sessionsListView selected item is still this session
                if (controller.getSessionsListView() != null && controller.getSessionsListView().getSelectionModel().getSelectedItem() != null) {
                    assertEquals(originalSessionId, controller.getSessionsListView().getSelectionModel().getSelectedItem().getId());
                }

            } catch (IOException e) {
                fail("Failed with IOException: " + e.getMessage(), e);
            }
        });
    }

    @Test
    public void testGlobalChatHistoryPreservedWhenNoSuiteSelected(@TempDir Path emptyHome) throws Exception {
        StudioPaths.setPathsForTesting(emptyHome, null);

        runFx(() -> {
            try {
                URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();

                AiAssistantPageController controller = loader.getController();
                controller.reloadTargets();

                // Group & suite combos are empty (global workspace)
                assertNull(controller.getSelectedSuiteDir());

                AiChatSession session = controller.getCurrentSession();
                assertNotNull(session);
                String globalSessionId = session.getId();

                AiChatMessage userMsg = new AiChatMessage("user", "Chat global không chọn suite");
                session.addMessage(userMsg);

                AiChatHistoryStore store = new AiChatHistoryStore();
                store.saveSession(null, session);
                controller.refreshSessionsList(globalSessionId);

                // Tab switch: reload targets
                controller.reloadTargets();

                // Verify messages remain intact
                assertEquals(globalSessionId, controller.getCurrentSession().getId());
                assertEquals(1, controller.getCurrentSession().getMessages().size());
                assertEquals("Chat global không chọn suite", controller.getCurrentSession().getMessages().get(0).getContent());

            } catch (Exception e) {
                fail(e.getMessage(), e);
            }
        });
    }

    @Test
    public void testViewNavigatorOnPageShownDoesNotWipeChat(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);

        runFx(() -> {
            try {
                ViewNavigator.invalidate(ViewNavigator.Page.AI_ASSISTANT);
                ViewNavigator.invalidate(ViewNavigator.Page.EXPLORER);

                StackPane pane = new StackPane();
                TabManager tm = new TabManager(new javafx.scene.layout.HBox(), pane);
                ViewNavigator.init(pane, tm);

                ViewNavigator.navigateTo(ViewNavigator.Page.AI_ASSISTANT);
                AiAssistantPageController ai = ViewNavigator.getController(ViewNavigator.Page.AI_ASSISTANT, AiAssistantPageController.class);
                assertNotNull(ai);

                int initialSize = ai.getCurrentSession().getMessages().size();

                // Add message
                ai.getCurrentSession().addMessage(new AiChatMessage("user", "Tin nhắn trước khi chuyển tab"));
                assertEquals(initialSize + 1, ai.getCurrentSession().getMessages().size());

                // Save session so disk also has it
                new AiChatHistoryStore().saveSession(ai.getSelectedSuiteDir(), ai.getCurrentSession());

                // Switch to Explorer
                ViewNavigator.navigateTo(ViewNavigator.Page.EXPLORER);

                // Switch back to AI Assistant
                ViewNavigator.navigateTo(ViewNavigator.Page.AI_ASSISTANT);

                // Verify chat message is still there
                assertEquals(initialSize + 1, ai.getCurrentSession().getMessages().size());
                assertTrue(ai.getCurrentSession().getMessages().stream()
                        .anyMatch(m -> "Tin nhắn trước khi chuyển tab".equals(m.getContent())));

            } catch (Exception e) {
                fail(e.getMessage(), e);
            }
        });
    }
}
