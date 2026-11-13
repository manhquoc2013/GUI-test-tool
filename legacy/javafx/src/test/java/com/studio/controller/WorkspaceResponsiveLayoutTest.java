package com.studio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the workspace layout at the sizes the builder is actually used at.
 *
 * <p>At a ~300px editor every header label used to collapse to "No-C…" / "Java C…" / "Chia …",
 * which left three indistinguishable buttons, and the Asserts pane squeezed the editor into a
 * column too narrow to author a step in. The header now prioritises: icons first, caption second,
 * title last; and the Asserts pane leaves the split when there is no room for both.</p>
 */
class WorkspaceResponsiveLayoutTest {

    private static final double NARROW_SCENE = 520;
    private static final double WIDE_SCENE = 1500;
    private static final String CSS_TOGGLE_ACTIVE = "active";

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized by another test class.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void aNarrowWorkspaceKeepsIconButtonsAndLiftsTheAssertsPanelOut() throws Exception {
        withWorkspace(NARROW_SCENE, controller -> {
            assertFalse(controller.isAssertsPanelShown(),
                    "At " + NARROW_SCENE + "px the Asserts pane must not squeeze the editor");
            for (Button button : viewModeButtons(controller)) {
                assertEquals("", button.getText(),
                        "A narrow header shows icons only, not clipped labels");
                assertNotNull(button.getAccessibleText(), "An icon-only button must stay identifiable");
            }
            Label caption = caption(controller);
            assertFalse(caption.isVisible(), "The line-count caption cannot fit at this width");
        });
    }

    @Test
    void aWideWorkspaceShowsLabelsAndTheAssertsPanel() throws Exception {
        withWorkspace(WIDE_SCENE, controller -> {
            assertTrue(controller.isAssertsPanelShown(), "There is room for both panes here");
            List<Button> buttons = viewModeButtons(controller);
            assertEquals("No-Code", buttons.get(0).getText());
            assertEquals("Java Code", buttons.get(1).getText());
            assertEquals("Chia đôi", buttons.get(2).getText());
            Label caption = caption(controller);
            assertTrue(caption.isVisible(), "The caption fits whole at " + WIDE_SCENE + "px");
            assertTrue(caption.getWidth() >= caption.prefWidth(-1) - 0.5,
                    "The caption is clipped to " + caption.getWidth() + "px");
        });
    }

    @Test
    void theHeaderToggleBringsTheAssertsPanelBackOnDemand() throws Exception {
        withWorkspace(NARROW_SCENE, controller -> {
            Button toggle = toggleButton(controller);
            assertFalse(toggle.getStyleClass().contains(CSS_TOGGLE_ACTIVE),
                    "The toggle must mirror the panel's hidden state");

            toggle.fire();

            assertTrue(controller.isAssertsPanelShown(), "The toggle must restore the Asserts pane");
            assertTrue(toggle.getStyleClass().contains(CSS_TOGGLE_ACTIVE));

            toggle.fire();
            assertFalse(controller.isAssertsPanelShown());
            assertFalse(toggle.getStyleClass().contains(CSS_TOGGLE_ACTIVE));
        });
    }

    @Test
    void wideningTheWorkspaceRestoresTheAssertsPanelAutomatically() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            SuiteWorkspacePageController controller;
            try {
                controller = open(stage, NARROW_SCENE);
                assertFalse(controller.isAssertsPanelShown(),
                        "The panel starts out of the split at " + NARROW_SCENE + "px");
            } catch (Throwable t) {
                error.set(t);
                stage.close();
                done.countDown();
                return;
            }
            // A window resize reaches the scene on the next pulse, so assert on the following turn.
            stage.setWidth(WIDE_SCENE);
            Platform.runLater(() -> {
                try {
                    settle(stage.getScene().getRoot());
                    assertTrue(controller.isAssertsPanelShown(),
                            "Resizing past the threshold must give the Asserts pane back");
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    stage.close();
                    done.countDown();
                }
            });
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out resizing the workspace");
        rethrow(error.get());
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    private interface ControllerAssertions {
        void accept(SuiteWorkspacePageController controller);
    }

    private void withWorkspace(double sceneWidth, ControllerAssertions assertions) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                SuiteWorkspacePageController controller = open(stage, sceneWidth);
                assertions.accept(controller);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out laying out the workspace");
        rethrow(error.get());
    }

    private static SuiteWorkspacePageController open(Stage stage, double sceneWidth) throws Exception {
        URL fxml = WorkspaceResponsiveLayoutTest.class.getResource("/fxml/suite_workspace_page.fxml");
        assertNotNull(fxml, "suite_workspace_page.fxml not found");
        FXMLLoader loader = new FXMLLoader(fxml);
        Parent root = loader.load();
        SuiteWorkspacePageController controller = loader.getController();
        Scene scene = new Scene(root, sceneWidth, 800);
        URL css = WorkspaceResponsiveLayoutTest.class.getResource("/css/studio.css");
        assertNotNull(css, "studio.css not found");
        scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.show();
        settle(root);

        Method setup = SuiteWorkspacePageController.class.getDeclaredMethod("setupDualViewInCodePanel");
        setup.setAccessible(true);
        setup.invoke(controller);
        Method showWorkspace = SuiteWorkspacePageController.class.getDeclaredMethod("showCaseWorkspaceMode");
        showWorkspace.setAccessible(true);
        showWorkspace.invoke(controller);

        settle(root);
        return controller;
    }

    private static List<Button> viewModeButtons(SuiteWorkspacePageController controller) {
        HBox switcher = (HBox) ((Button) field(controller, "viewNoCodeBtn")).getParent();
        assertNotNull(switcher, "The editor header lost its view switcher");
        return switcher.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private static Label caption(SuiteWorkspacePageController controller) {
        Label caption = (Label) field(controller, "codeStatsLabel");
        assertNotNull(caption, "The editor header lost its caption");
        return caption;
    }

    private static Button toggleButton(SuiteWorkspacePageController controller) {
        return (Button) field(controller, "assertsToggleBtn");
    }

    private static Object field(SuiteWorkspacePageController controller, String name) {
        try {
            Field field = SuiteWorkspacePageController.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(controller);
        } catch (ReflectiveOperationException e) {
            fail("Could not read " + name + ": " + e.getMessage(), e);
            return null;
        }
    }

    private static void settle(Parent root) {
        root.applyCss();
        layoutTree(root);
        root.applyCss();
        layoutTree(root);
    }

    private static void layoutTree(Node node) {
        if (node instanceof Parent parent) {
            parent.layout();
            for (Node child : parent.getChildrenUnmodifiable()) {
                layoutTree(child);
            }
        }
    }

    private static void rethrow(Throwable error) throws Exception {
        if (error == null) {
            return;
        }
        if (error instanceof AssertionError assertion) {
            throw assertion;
        }
        fail("Workspace responsive check threw", error);
    }
}
