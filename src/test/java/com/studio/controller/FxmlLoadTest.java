package com.studio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FxmlLoadTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
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
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) throw ae;
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void testSuiteWorkspacePageLoadsSuccessfully() throws Exception {
        runFx(() -> {
            try {
                URL resource = getClass().getResource("/fxml/suite_workspace_page.fxml");
                assertNotNull(resource, "suite_workspace_page.fxml resource not found");
                FXMLLoader loader = new FXMLLoader(resource);
                Node root = loader.load();
                assertNotNull(root, "suite_workspace_page root node should not be null");
                assertNotNull(loader.getController(), "SuiteWorkspacePageController should be created");
            } catch (Exception e) {
                fail("Failed to load suite_workspace_page.fxml: " + e.getMessage(), e);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/fxml/main_shell.fxml",
            "/fxml/explorer_page.fxml",
            "/fxml/suite_workspace_page.fxml",
            "/fxml/reports_page.fxml",
            "/fxml/report_detail_page.fxml",
            "/fxml/ai_assistant_page.fxml",
            "/fxml/assert_builder_popup.fxml"
    })
    void testAllFxmlPagesLoadWithoutException(String fxmlPath) throws Exception {
        runFx(() -> {
            try {
                URL resource = getClass().getResource(fxmlPath);
                assertNotNull(resource, "FXML resource not found: " + fxmlPath);
                FXMLLoader loader = new FXMLLoader(resource);
                Node root = loader.load();
                assertNotNull(root, "Loaded root node must not be null for " + fxmlPath);
            } catch (Exception e) {
                fail("Failed to load " + fxmlPath + ": " + e.getMessage(), e);
            }
        });
    }

    @Test
    void testMainShellDoesNotHardcodeParentStylesheet() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fxml/main_shell.fxml")) {
            assertNotNull(is, "main_shell.fxml not found");
            String content = new String(is.readAllBytes());
            assertFalse(content.contains("stylesheets="),
                    "main_shell.fxml should not have hardcoded stylesheets on BorderPane to allow theme switching via Scene stylesheets");
        }
    }
}
