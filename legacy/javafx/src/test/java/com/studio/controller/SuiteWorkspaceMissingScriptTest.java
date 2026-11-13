package com.studio.controller;

import com.studio.model.TestCase;
import com.studio.model.TestStep;
import com.studio.model.RunReport;
import com.studio.model.TestSuite;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.ui.VisualStepFlowPane;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A case may exist before its script does: the AI writes scenario cases from a specification and
 * generates their Java later. Opening such a case used to raise "Script file not found" as a red toast
 * and print the error into the editor, where it could then be saved as the case's script.
 *
 * <p>The empty state is a prompt on an empty buffer, not placeholder text: a {@code //} line in the
 * buffer parses as an authored NOTE card, so placeholder content both invented a step and became
 * savable script.</p>
 */
class SuiteWorkspaceMissingScriptTest {

    private static final String EMPTY_CASE = "TC01_Kich_Ban_Chua_Co_Script";
    private static final String STEPS_ONLY_CASE = "TC02_Kich_Ban_Co_Buoc";
    private static final String RECORDED_CASE = "TC03_Da_Co_Script";
    private static final String RECORDED_SCRIPT = """
            package com.test;

            import com.microsoft.playwright.*;

            public class TC03_Da_Co_Script {
                public static void main(String[] args) {
                    // recorded body
                }
            }
            """;

    @TempDir
    Path tempDir;

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
    void aCaseWithoutItsScriptOpensAsAnEmptyStateNotAsAnError() throws Exception {
        Path suiteDir = suiteWithCasesWithoutScripts();
        VBox toasts = new VBox();
        AtomicReference<MountedWorkspace> mountedRef = new AtomicReference<>();

        try {
            onFxAndWait(() -> {
                ToastService.setToastContainer(toasts);
                MountedWorkspace mounted = mountWorkspace();
                mountedRef.set(mounted);
                SuiteWorkspacePageController controller = mounted.controller();

                controller.loadSuite(new ProjectStore().loadAndMigrateSuite(suiteDir), suiteDir, null, false);
                loadCase(controller, EMPTY_CASE);

                TextArea editor = editor(controller);
                assertFalse(editor.getText().contains("Lỗi đọc script"),
                        "An absent script is not an error, yet the editor holds the error text");
                assertEquals("", editor.getText(),
                        "An empty case opens on an empty buffer; placeholder text would be savable script");
                assertTrue(editor.getPromptText() != null && editor.getPromptText().contains("Chưa có script"),
                        "The empty buffer must carry the hint as a prompt, got: " + editor.getPromptText());
                assertTrue(stepPane(controller).getAllSteps().isEmpty(),
                        "The hint must not be parsed into a phantom step card");
                assertTrue(errorToasts(toasts).isEmpty(),
                        "Opening a case that has no script yet must not raise an error toast: "
                                + errorToasts(toasts));

                controller.saveCode();
                assertTrue(new ProjectStore().findCaseScript(suiteDir, "tc-empty").isEmpty(),
                        "Saving an empty buffer must not create a script file");
            });
        } finally {
            onFxAndWait(() -> {
                ToastService.setToastContainer(null);
                MountedWorkspace mounted = mountedRef.get();
                if (mounted != null) {
                    mounted.stage().close();
                }
            });
        }
    }

    /**
     * A case generated from a specification carries steps but no Java. The builder is its only source
     * of code, so the buffer has to hold what a save writes — an empty buffer with a non-empty step
     * list could never be saved.
     */
    @Test
    void aCaseWithAuthoredStepsButNoScriptCanStillBeSavedFromTheBuilder() throws Exception {
        Path suiteDir = suiteWithCasesWithoutScripts();
        AtomicReference<MountedWorkspace> mountedRef = new AtomicReference<>();

        try {
            onFxAndWait(() -> {
                ToastService.setToastContainer(new VBox());
                MountedWorkspace mounted = mountWorkspace();
                mountedRef.set(mounted);
                SuiteWorkspacePageController controller = mounted.controller();

                controller.loadSuite(new ProjectStore().loadAndMigrateSuite(suiteDir), suiteDir, null, false);
                loadCase(controller, STEPS_ONLY_CASE);

                VisualStepFlowPane pane = stepPane(controller);
                List<TestStep> cards = pane.getAllSteps();
                assertEquals(2, cards.size(), "Both authored steps must keep their card");
                String buffer = editor(controller).getText();
                assertTrue(buffer.contains("click();"),
                        "The buffer must hold the script a save writes for these steps, got: " + buffer);
                assertTrue(buffer.contains("public static void main"),
                        "A step-only case still needs the class envelope a run compiles, got: " + buffer);

                controller.saveCode();
                Optional<String> saved = new ProjectStore().findCaseScript(suiteDir, "tc-steps");
                assertTrue(saved.isPresent(), "Saving the builder's own code must write the script file");
                assertTrue(saved.get().contains("click();"), "The saved script carries the step");
                assertTrue(saved.get().contains("public class"),
                        "The saved script is a compilable class, got: " + saved.get());
            });
        } finally {
            onFxAndWait(() -> {
                MountedWorkspace mounted = mountedRef.get();
                if (mounted != null) {
                    mounted.stage().close();
                }
            });
        }
    }

    @Test
    void aCaseWithAScriptOpensItsCodeAndShowsNoPrompt() throws Exception {
        Path suiteDir = suiteWithCasesWithoutScripts();
        AtomicReference<MountedWorkspace> mountedRef = new AtomicReference<>();

        try {
            onFxAndWait(() -> {
                ToastService.setToastContainer(new VBox());
                MountedWorkspace mounted = mountWorkspace();
                mountedRef.set(mounted);
                SuiteWorkspacePageController controller = mounted.controller();

                controller.loadSuite(new ProjectStore().loadAndMigrateSuite(suiteDir), suiteDir, null, false);
                loadCase(controller, RECORDED_CASE);

                TextArea editor = editor(controller);
                assertTrue(editor.getText().contains("recorded body"),
                        "A case with a script opens its code, got: " + editor.getText());
                assertNull(editor.getPromptText(), "A case with code has no empty-buffer hint");
                assertFalse(editor.getText().contains("Chưa có script"), editor.getText());
            });
        } finally {
            onFxAndWait(() -> {
                MountedWorkspace mounted = mountedRef.get();
                if (mounted != null) {
                    mounted.stage().close();
                }
            });
        }
    }

    @Test
    void aRunSentenceKeepsTheSkippedCount() {
        RunReport report = new RunReport();
        report.setPassed(0);
        report.setFailed(0);
        report.setSkipped(1);

        assertEquals("Kết quả: 0 đạt, 0 thất bại, 1 bỏ qua",
                SuiteWorkspacePageController.runSummary("Kết quả:", report),
                "A skipped case is invisible in '0 đạt, 0 thất bại' — the user would read it as a run");
    }

    private Path suiteWithCasesWithoutScripts() throws IOException {
        Path suiteDir = tempDir.resolve("QuanLyNguoiYeuThe").resolve("KiemThu_TaoHoSoNguoiYeuThe");
        Files.createDirectories(suiteDir);

        TestSuite suite = new TestSuite("KiemThu_TaoHoSoNguoiYeuThe", "Suite of the reported bug");

        // Scenario case: the AI named no script file because it wrote none.
        suite.getCases().add(new TestCase("tc-empty", EMPTY_CASE, "", "", "Ready"));

        // Scenario case with steps from a specification but still no Java.
        TestCase stepsOnly = new TestCase("tc-steps", STEPS_ONLY_CASE, "", "", "Ready");
        stepsOnly.setTestSteps(new java.util.ArrayList<>(List.of(
                new TestStep(1, "NAVIGATE", "https://example.com/form", "", "Mở form", ""),
                new TestStep(2, "CLICK", "#submit", "", "Bấm gửi", ""))));
        suite.getCases().add(stepsOnly);

        // Recorded case: its script exists on disk.
        suite.getCases().add(new TestCase("tc-recorded", RECORDED_CASE, "",
                RECORDED_CASE + ".java", "Ready"));

        new ProjectStore().saveSuite(suite, suiteDir);
        Files.writeString(suiteDir.resolve(RECORDED_CASE + ".java"), RECORDED_SCRIPT);
        return suiteDir;
    }

    private record MountedWorkspace(Stage stage, SuiteWorkspacePageController controller) {
    }

    private static MountedWorkspace mountWorkspace() throws IOException {
        URL fxml = SuiteWorkspaceMissingScriptTest.class.getResource("/fxml/suite_workspace_page.fxml");
        assertNotNull(fxml, "suite_workspace_page.fxml not found");
        FXMLLoader loader = new FXMLLoader(fxml);
        Parent root = loader.load();
        Stage stage = new Stage();
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(
                SuiteWorkspaceMissingScriptTest.class.getResource("/css/studio.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        return new MountedWorkspace(stage, loader.getController());
    }

    private static void loadCase(SuiteWorkspacePageController controller, String caseName) {
        try {
            Method loadCase = SuiteWorkspacePageController.class.getDeclaredMethod("loadCase", String.class);
            loadCase.setAccessible(true);
            loadCase.invoke(controller, caseName);
        } catch (ReflectiveOperationException e) {
            fail("Could not open case " + caseName + ": " + e.getCause(), e);
        }
    }

    private static TextArea editor(SuiteWorkspacePageController controller) {
        return (TextArea) field(controller, "codeEditor");
    }

    private static VisualStepFlowPane stepPane(SuiteWorkspacePageController controller) {
        return (VisualStepFlowPane) field(controller, "visualStepFlowPane");
    }

    private static Object field(Object target, String name) {
        try {
            Field field = SuiteWorkspacePageController.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            assertNotNull(value, "The workspace field '" + name + "' is not initialised");
            return value;
        } catch (ReflectiveOperationException e) {
            fail("The workspace field '" + name + "' disappeared: " + e.getMessage(), e);
            return null;
        }
    }

    private static List<String> errorToasts(VBox toasts) {
        return toasts.getChildren().stream()
                .filter(Node::isVisible)
                .filter(node -> node.getStyleClass().contains("toast-error"))
                .map(node -> node.getUserData() != null ? node.getUserData().toString() : node.toString())
                .toList();
    }

    private interface FxTask {
        void run() throws Exception;
    }

    private static void onFxAndWait(FxTask task) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for the FX thread");
        if (error.get() instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (error.get() != null) {
            fail("Workspace check threw", error.get());
        }
    }
}
