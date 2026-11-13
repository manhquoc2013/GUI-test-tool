package com.studio.controller;

import com.studio.ui.AiCopilotDrawer;
import com.studio.ui.PlaywrightCodeArea;
import com.studio.ui.VisualStepFlowPane;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the workspace editor's three view modes.
 *
 * <p>A JavaFX node can only have one parent: the original implementation registered the step flow
 * and the code area as direct children of the container AND as items of the split pane, so Split
 * view turned into an empty/stacked pane. These tests pin the reparenting contract.</p>
 */
class SuiteWorkspaceDualViewTest {

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
    void switchingViewModesReparentsEditorsIntoExactlyOneContainer() throws Exception {
        withController(controller -> {
            try {
                Method setup = SuiteWorkspacePageController.class
                        .getDeclaredMethod("setupDualViewInCodePanel");
                setup.setAccessible(true);
                setup.invoke(controller);

                StackPane editorContainer = (StackPane) field(controller, "editorContainer");
                SplitPane dualSplitPane = (SplitPane) field(controller, "dualSplitPane");
                VisualStepFlowPane stepPane = (VisualStepFlowPane) field(controller, "visualStepFlowPane");
                PlaywrightCodeArea codeArea = (PlaywrightCodeArea) field(controller, "playwrightCodeArea");

                // Mode 0 (No-Code) is the default.
                assertEquals(1, editorContainer.getChildren().size());
                assertSame(stepPane, editorContainer.getChildren().get(0));

                switchMode(controller, 1);
                assertEquals(1, editorContainer.getChildren().size());
                assertSame(codeArea, editorContainer.getChildren().get(0));

                switchMode(controller, 2);
                assertEquals(1, editorContainer.getChildren().size(),
                        "Split view must add ONE split pane, not re-parent editors into the stack");
                assertInstanceOf(SplitPane.class, editorContainer.getChildren().get(0));
                assertEquals(2, dualSplitPane.getItems().size());
                assertSame(stepPane, dualSplitPane.getItems().get(0));
                assertSame(codeArea, dualSplitPane.getItems().get(1));
                assertFalse(stepPane.getParent() == editorContainer,
                        "The step flow must be owned by the split pane in Split view");
                assertFalse(codeArea.getParent() == editorContainer,
                        "The code area must be owned by the split pane in Split view");

                switchMode(controller, 0);
                assertEquals(1, editorContainer.getChildren().size());
                assertSame(stepPane, editorContainer.getChildren().get(0));
            } catch (ReflectiveOperationException e) {
                fail("Reflection failure: " + e.getMessage(), e);
            }
        });
    }

    @Test
    void copilotDrawerDocksAndReleasesWorkspaceSpace() throws Exception {
        withController(controller -> {
            try {
                Method setup = SuiteWorkspacePageController.class
                        .getDeclaredMethod("setupDualViewInCodePanel");
                setup.setAccessible(true);
                setup.invoke(controller);

                BorderPane workspaceLayer = (BorderPane) field(controller, "workspaceLayer");
                assertNotNull(workspaceLayer, "workspaceLayer must exist in the FXML");
                AiCopilotDrawer drawer = (AiCopilotDrawer) field(controller, "aiCopilotDrawer");
                assertNotNull(drawer, "The AI Copilot drawer must be created with the workspace");
                assertNull(workspaceLayer.getRight(), "A closed drawer must not occupy the right slot");
                assertFalse(drawer.isVisible(), "The drawer starts closed");

                drawer.showDrawer();
                assertSame(drawer, workspaceLayer.getRight(),
                        "An open drawer must be docked into the BorderPane right slot");
                assertTrue(drawer.isVisible());

                drawer.hideDrawer();
                assertNull(workspaceLayer.getRight(),
                        "Closing the drawer must release the layout space");
            } catch (ReflectiveOperationException e) {
                fail("Reflection failure: " + e.getMessage(), e);
            }
        });
    }

    private void switchMode(SuiteWorkspacePageController controller, int mode) {
        try {
            Method switchMode = SuiteWorkspacePageController.class
                    .getDeclaredMethod("switchWorkspaceViewMode", int.class);
            switchMode.setAccessible(true);
            switchMode.invoke(controller, mode);
        } catch (ReflectiveOperationException e) {
            fail("Could not switch view mode: " + e.getMessage(), e);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /**
     * The editor header must stay readable when the code panel is narrow: the view-mode switcher
     * holds its labels ("No-Code", "Java Code", "Chia đôi" — never "No-C…"), and the secondary
     * line-count caption steps aside instead of being cut mid-word.
     */
    @Test
    void theViewSwitcherKeepsItsLabelsAndTheCaptionStepsAside() throws Exception {
        // 620px leaves room for the three view-mode labels but not for the line-count caption.
        assertHeaderAt(620, false);
        assertHeaderAt(1500, true);
    }

    private void assertHeaderAt(double sceneWidth, boolean captionExpected) throws Exception {
        withController(sceneWidth, controller -> {
            try {
                Method setup = SuiteWorkspacePageController.class
                        .getDeclaredMethod("setupDualViewInCodePanel");
                setup.setAccessible(true);
                setup.invoke(controller);
                // The split pane starts hidden on the suite-info placeholder; the header only has a
                // width once a case is opened.
                Method showWorkspace = SuiteWorkspacePageController.class
                        .getDeclaredMethod("showCaseWorkspaceMode");
                showWorkspace.setAccessible(true);
                showWorkspace.invoke(controller);

                VBox codePanel = (VBox) field(controller, "codePanel");
                Parent root = codePanel.getScene().getRoot();
                root.applyCss();
                layoutTree(root);
                root.applyCss();
                layoutTree(root);

                HBox switcher = (HBox) codePanel.lookup(".view-mode-switcher");
                assertNotNull(switcher, "The editor header lost its view switcher");
                for (Node node : switcher.getChildren()) {
                    Region button = (Region) node;
                    assertTrue(button.getWidth() >= button.prefWidth(-1) - 0.5,
                            "At " + codePanel.getWidth() + "px the view-mode button is squeezed to "
                                    + button.getWidth() + "px, below its " + button.prefWidth(-1)
                                    + "px label");
                }
                Label caption = (Label) codePanel.lookup(".editor-stats-label");
                assertNotNull(caption, "The editor header lost its caption");
                assertEquals(captionExpected, caption.isVisible(),
                        "At a " + codePanel.getWidth() + "px code panel the caption should be "
                                + (captionExpected ? "readable" : "hidden"));
            } catch (ReflectiveOperationException e) {
                fail("Reflection failure: " + e.getMessage(), e);
            }
        });
    }

    private interface ControllerAssertions {
        void accept(SuiteWorkspacePageController controller);
    }

    private void withController(ControllerAssertions assertions) throws Exception {
        withController(1200, assertions);
    }

    private void withController(double sceneWidth, ControllerAssertions assertions) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                URL fxml = getClass().getResource("/fxml/suite_workspace_page.fxml");
                assertNotNull(fxml, "suite_workspace_page.fxml not found");
                FXMLLoader loader = new FXMLLoader(fxml);
                Parent root = loader.load();
                SuiteWorkspacePageController controller = loader.getController();
                Scene scene = new Scene(root, sceneWidth, 800);
                scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                layoutTree(root);

                assertions.accept(controller);

                root.applyCss();
                layoutTree(root);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                stage.close();
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out laying out the workspace");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) {
                throw ae;
            }
            fail("Workspace dual-view check threw", error.get());
        }
    }

    private static void layoutTree(Node node) {
        if (node instanceof Parent parent) {
            parent.layout();
            for (Node child : parent.getChildrenUnmodifiable()) {
                layoutTree(child);
            }
        }
    }
}
