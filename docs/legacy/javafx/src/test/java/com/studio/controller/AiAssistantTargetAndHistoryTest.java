package com.studio.controller;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.store.AiChatHistoryStore;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Regression tests for the conversation lifecycle: the save target starts blank, is locked once the
 * conversation has a prompt, and changing it never swaps the open transcript or drops the history.
 */
public class AiAssistantTargetAndHistoryTest {

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

    private static AiAssistantPageController loadController() throws IOException {
        URL resource = AiAssistantTargetAndHistoryTest.class.getResource("/fxml/ai_assistant_page.fxml");
        assertNotNull(resource);
        FXMLLoader loader = new FXMLLoader(resource);
        Node root = loader.load();
        assertNotNull(root);
        return loader.getController();
    }

    private static String valueOrEmpty(javafx.scene.control.ComboBox<String> combo) {
        return combo.getValue() == null ? "" : combo.getValue();
    }

    private static void prepareProject() throws IOException {
        Files.createDirectories(StudioPaths.getProjectDir().resolve("WebTests").resolve("LoginSuite"));
    }

    @Test
    public void testDefaultTargetIsBlank(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        prepareProject();

        runFx(() -> {
            try {
                AiAssistantPageController controller = loadController();
                assertEquals("", valueOrEmpty(controller.getTargetGroupCombo()),
                        "Đích lưu phải mặc định rỗng, không tự chọn nhóm đầu tiên");
                assertEquals("", valueOrEmpty(controller.getTargetSuiteCombo()));
                assertNull(controller.getSelectedSuiteDir());
                assertFalse(controller.getTargetGroupCombo().isDisabled());
                assertFalse(controller.getTargetSuiteCombo().isDisabled());
            } catch (IOException e) {
                fail(e.getMessage(), e);
            }
        });
    }

    @Test
    public void testChoosingTargetNeverReplacesTheOpenConversation(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        prepareProject();

        runFx(() -> {
            try {
                AiAssistantPageController controller = loadController();

                AiChatSession session = controller.getCurrentSession();
                session.setTitle("Câu hỏi đang chạy");
                session.addMessage(new AiChatMessage("user", "Tin nhắn đang được tác nhân xử lý"));
                new AiChatHistoryStore().saveSession(session);
                controller.refreshSessionsList(session.getId());
                String openSessionId = controller.getCurrentSession().getId();

                // The user picks a save target while the conversation is open/running.
                controller.getTargetGroupCombo().setValue("WebTests");
                controller.getTargetSuiteCombo().setValue("LoginSuite");

                assertEquals(openSessionId, controller.getCurrentSession().getId(),
                        "Chọn đích lưu không được thay thế cuộc trò chuyện đang mở");
                assertEquals(1, controller.getCurrentSession().getMessages().size(),
                        "Lịch sử của cuộc trò chuyện đang mở phải còn nguyên");
                assertTrue(controller.getSessionsListView().getItems().stream()
                                .anyMatch(meta -> openSessionId.equals(meta.getId())),
                        "Cuộc trò chuyện vẫn phải nằm trong lịch sử sau khi chọn đích lưu");
            } catch (IOException e) {
                fail(e.getMessage(), e);
            }
        });
    }

    @Test
    public void testTargetLocksAfterFirstPromptAndClearsOnNewChat(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        prepareProject();

        runFx(() -> {
            try {
                AiAssistantPageController controller = loadController();
                controller.getTargetGroupCombo().setValue("WebTests");
                controller.getTargetSuiteCombo().setValue("LoginSuite");

                // A prompt has been sent in this conversation.
                controller.getCurrentSession().addMessage(new AiChatMessage("user", "Đã gửi yêu cầu"));
                controller.reloadTargets();

                assertTrue(controller.getTargetGroupCombo().isDisabled(),
                        "Đích lưu phải bị khóa sau khi đã nhắn");
                assertTrue(controller.getTargetSuiteCombo().isDisabled());
                assertEquals("WebTests", controller.getTargetGroupCombo().getValue(),
                        "Giá trị đích lưu đã chọn phải được giữ nguyên");

                controller.createNewChat();
                assertFalse(controller.getTargetGroupCombo().isDisabled(),
                        "Cuộc trò chuyện mới phải cho chọn lại đích lưu");
                assertEquals("", valueOrEmpty(controller.getTargetGroupCombo()));
                assertEquals("", valueOrEmpty(controller.getTargetSuiteCombo()));
            } catch (IOException e) {
                fail(e.getMessage(), e);
            }
        });
    }

    @Test
    public void testHistoryToggleLabelMirrorsBackgroundActivity() {
        assertEquals("Lịch sử", AiAssistantPageController.buildHistoryToggleLabel(false, 0, 0));
        assertEquals("Thu gọn", AiAssistantPageController.buildHistoryToggleLabel(true, 0, 0));
        assertEquals("Lịch sử ⏳2", AiAssistantPageController.buildHistoryToggleLabel(false, 2, 0));
        assertEquals("Lịch sử ●3", AiAssistantPageController.buildHistoryToggleLabel(false, 0, 3));
        assertEquals("Thu gọn ⏳1 ●2", AiAssistantPageController.buildHistoryToggleLabel(true, 1, 2));
    }

    @Test
    public void testHistoryToggleShowsActivityWithoutOpeningTheRail(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);
        prepareProject();

        runFx(() -> {
            try {
                AiAssistantPageController controller = loadController();
                assertNotNull(controller.getHistoryToggleBtn());
                assertEquals("Lịch sử", controller.getHistoryToggleBtn().getText(),
                        "Thanh lịch sử mặc định thu gọn, nên nút phải ở trạng thái mở");

                controller.toggleHistorySidebar();
                assertEquals("Thu gọn", controller.getHistoryToggleBtn().getText());

                controller.toggleHistorySidebar();
                assertEquals("Lịch sử", controller.getHistoryToggleBtn().getText());
            } catch (IOException e) {
                fail(e.getMessage(), e);
            }
        });
    }

    @Test
    public void testHistorySearchMatchesTitleAndMessageText(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);

        runFx(() -> {
            try {
                AiAssistantPageController controller = loadController();
                AiChatHistoryStore store = new AiChatHistoryStore();

                AiChatSession payment = new AiChatSession("global");
                payment.setTitle("Luồng thanh toán");
                payment.addMessage(new AiChatMessage("user", "kiểm thử hoàn tiền thẻ tín dụng"));
                store.saveSession(payment);

                AiChatSession login = new AiChatSession("global");
                login.setTitle("Luồng đăng nhập");
                login.addMessage(new AiChatMessage("user", "xác thực otp qua sms"));
                store.saveSession(login);

                controller.refreshSessionsList(payment.getId());

                // Search by message text, not only by conversation title.
                controller.getSessionSearchField().setText("hoàn tiền");
                assertEquals(1, controller.getSessionsListView().getItems().size());
                assertEquals(payment.getId(), controller.getSessionsListView().getItems().get(0).getId());

                controller.getSessionSearchField().setText("đăng nhập");
                assertEquals(1, controller.getSessionsListView().getItems().size());
                assertEquals(login.getId(), controller.getSessionsListView().getItems().get(0).getId());

                controller.getSessionSearchField().setText("");
                assertTrue(controller.getSessionsListView().getItems().size() >= 2);
            } catch (IOException e) {
                fail(e.getMessage(), e);
            }
        });
    }
}
