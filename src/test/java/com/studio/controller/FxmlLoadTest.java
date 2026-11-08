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
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for FX thread");
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

    @Test
    void testReportDetailPageDrawerHeightBoundToDetailLayer() throws Exception {
        runFx(() -> {
            try {
                URL resource = getClass().getResource("/fxml/report_detail_page.fxml");
                assertNotNull(resource);
                FXMLLoader loader = new FXMLLoader(resource);
                Node root = loader.load();
                assertNotNull(root);
                ReportDetailPageController controller = loader.getController();
                assertNotNull(controller);

                javafx.scene.layout.StackPane detailLayer = (javafx.scene.layout.StackPane) root.lookup("#detailLayer");
                javafx.scene.layout.VBox caseDetailSidebar = (javafx.scene.layout.VBox) root.lookup("#caseDetailSidebar");
                assertNotNull(detailLayer, "detailLayer must be present");
                assertNotNull(caseDetailSidebar, "caseDetailSidebar must be present");

                assertTrue(caseDetailSidebar.prefHeightProperty().isBound(),
                        "caseDetailSidebar prefHeight must be bound to prevent drawer collapse");
                assertTrue(caseDetailSidebar.maxHeightProperty().isBound(),
                        "caseDetailSidebar maxHeight must be bound to detailLayer");
            } catch (Exception e) {
                fail("Failed to verify report detail drawer: " + e.getMessage(), e);
            }
        });
    }

    @Test
    void testDialogCssBorderColorPaintResolution() throws Exception {
        runFx(() -> {
            try {
                javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
                javafx.scene.Scene scene = new javafx.scene.Scene(root);
                scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());

                // Test 1: VBox with -fx-border-color: -color-border
                javafx.scene.layout.VBox box1 = new javafx.scene.layout.VBox();
                box1.setStyle("-fx-border-color: -color-border; -fx-background-color: -color-surface;");
                root.getChildren().add(box1);

                // Test 2: VBox with -fx-border-color: -color-primary
                javafx.scene.layout.VBox box2 = new javafx.scene.layout.VBox();
                box2.setStyle("-fx-border-color: -color-primary; -fx-background-color: -color-surface;");
                root.getChildren().add(box2);

                // Apply CSS and verify calculation resolves paint without ClassCastException
                root.applyCss();
                box1.applyCss();
                box2.applyCss();

                assertNotNull(box1.getBorder(), "box1 border should be parsed");
                assertNotNull(box2.getBorder(), "box2 border should be parsed");

                // Switch to light theme and verify
                scene.getStylesheets().clear();
                scene.getStylesheets().add(getClass().getResource("/css/studio-light.css").toExternalForm());
                root.applyCss();
                box1.applyCss();
                box2.applyCss();

                assertNotNull(box1.getBorder(), "box1 border in light theme should be parsed");
                assertNotNull(box2.getBorder(), "box2 border in light theme should be parsed");
            } catch (Exception e) {
                fail("CSS calculation threw exception: " + e.getMessage(), e);
            }
        });
    }
}

