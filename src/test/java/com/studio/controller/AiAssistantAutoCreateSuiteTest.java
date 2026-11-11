package com.studio.controller;

import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.store.ProjectStore;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AiAssistantAutoCreateSuiteTest {

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

    @Test
    public void testAutoCreateGroupAndSuiteWhenNoneExists(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);

        runFx(() -> {
            try {
                // Initialize ViewNavigator
                StackPane contentArea = new StackPane();
                TabManager tm = new TabManager(new javafx.scene.layout.HBox(), contentArea);
                ViewNavigator.init(contentArea, tm);

                URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
                assertNotNull(resource);
                FXMLLoader loader = new FXMLLoader(resource);
                Node root = loader.load();
                assertNotNull(root);

                AiAssistantPageController controller = loader.getController();
                assertNotNull(controller);

                // Initial state: empty project, no groups or suites, and no auto-selected save target
                assertTrue(controller.getTargetGroupCombo().getValue() == null
                        || controller.getTargetGroupCombo().getValue().isBlank());
                assertTrue(controller.getTargetSuiteCombo().getValue() == null
                        || controller.getTargetSuiteCombo().getValue().isBlank());

                // Prepare simulated AI generated cases
                AiGeneratedCase case1 = new AiGeneratedCase();
                case1.setName("LoginWithValidCredentials");
                case1.setDescription("Kiểm tra đăng nhập thành công với email và mật khẩu đúng");
                case1.setScriptCode("page.navigate(\"https://example.com/login\");\npage.fill(\"#username\", \"admin\");\npage.click(\"#login\");");
                Assertion assert1 = new Assertion();
                assert1.setType("PAGE_TITLE_CONTAINS");
                assert1.setValue("Dashboard");
                case1.setAssertions(List.of(assert1));

                controller.getCurrentGeneratedCases().add(case1);

                // Set a meaningful topic for session
                controller.getCurrentSession().setTitle("Đăng nhập Hệ thống Quản trị");

                // Execute apply without having any prior group or suite
                controller.applyCasesToSelectedSuite();

                // Verification 1: Group and Suite were automatically named accurately after the login function
                assertEquals("XacThuc_TaiKhoan", controller.getTargetGroupCombo().getValue());
                assertEquals("KiemThu_DangNhapHeThong", controller.getTargetSuiteCombo().getValue());

                // Verification 2: Check filesystem creation
                Path projectDir = StudioPaths.getProjectDir();
                Path groupDir = projectDir.resolve("XacThuc_TaiKhoan");
                assertTrue(Files.exists(groupDir), "Group directory XacThuc_TaiKhoan must be created");
                assertTrue(Files.exists(groupDir.resolve("group.json")), "group.json must exist");

                Path suiteDir = groupDir.resolve("KiemThu_DangNhapHeThong");
                assertTrue(Files.exists(suiteDir), "Suite directory must be created");
                assertTrue(Files.exists(suiteDir.resolve("suite.json")), "suite.json must exist");

                // Verification 3: Test case file was written
                Path caseJava = suiteDir.resolve("LoginWithValidCredentials.java");
                assertTrue(Files.exists(caseJava), "LoginWithValidCredentials.java must be created");
                String code = Files.readString(caseJava);
                assertTrue(code.contains("page.navigate(\"https://example.com/login\");"));

                // Verification 4: suite.json has case entry
                String suiteJsonContent = Files.readString(suiteDir.resolve("suite.json"));
                assertTrue(suiteJsonContent.contains("LoginWithValidCredentials"));

            } catch (Exception e) {
                fail("Failed: " + e.getMessage(), e);
            }
        });
    }

    @Test
    public void testAutoCreateSuiteUnderExistingGroup(@TempDir Path tempHome) throws Exception {
        StudioPaths.setPathsForTesting(tempHome, null);

        // Pre-create an existing group
        Path projectDir = StudioPaths.getProjectDir();
        Path groupDir = projectDir.resolve("ExistingGroup");
        Files.createDirectories(groupDir);
        Files.writeString(groupDir.resolve("group.json"), "{\"name\":\"ExistingGroup\",\"type\":\"group\"}");

        runFx(() -> {
            try {
                StackPane contentArea = new StackPane();
                TabManager tm = new TabManager(new javafx.scene.layout.HBox(), contentArea);
                ViewNavigator.init(contentArea, tm);

                URL resource = getClass().getResource("/fxml/ai_assistant_page.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();

                AiAssistantPageController controller = loader.getController();
                controller.reloadTargets();

                // Select the existing group, but suite is empty
                controller.getTargetGroupCombo().setValue("ExistingGroup");
                controller.getTargetSuiteCombo().setValue(null);

                AiGeneratedCase c = new AiGeneratedCase();
                c.setName("TC01_GioHang_ThemSanPham");
                c.setDescription("Kiểm thử thêm sản phẩm vào giỏ hàng");
                c.setScriptCode("page.navigate(\"https://example.com\");");
                controller.getCurrentGeneratedCases().add(c);

                controller.getCurrentSession().setTitle("Cuộc trò chuyện mới"); // default title

                controller.applyCasesToSelectedSuite();

                // Group should remain ExistingGroup, Suite should be named after the test case feature
                assertEquals("ExistingGroup", controller.getTargetGroupCombo().getValue());
                assertEquals("KiemThu_GioHang", controller.getTargetSuiteCombo().getValue());

                Path suiteDir = groupDir.resolve("KiemThu_GioHang");
                assertTrue(Files.exists(suiteDir));
                assertTrue(Files.exists(suiteDir.resolve("suite.json")));
                assertTrue(Files.exists(suiteDir.resolve("TC01_GioHang_ThemSanPham.java")));

            } catch (Exception e) {
                fail(e.getMessage(), e);
            }
        });
    }
}
