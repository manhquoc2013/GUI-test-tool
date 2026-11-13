package com.studio.controller;

import com.studio.model.*;
import com.studio.service.AiLoggingService;
import com.studio.service.I18nService;
import com.studio.service.ResourceReleaseService;
import com.studio.service.RunProgressService;
import com.studio.service.StatusBarService;
import com.studio.service.TestCaseExcelExportService;
import com.studio.service.TestRunnerService;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.util.AssertTypeDisplay;
import com.studio.util.BusyButtons;
import com.studio.util.DialogTheme;
import com.studio.util.PlaywrightUtil;
import com.studio.util.RunTargetResolve;
import com.studio.util.StatusDisplay;
import com.studio.util.StudioPaths;
import com.studio.util.UiIcons;
import com.studio.util.UiMotion;
import com.studio.util.UiTooltips;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import com.studio.ui.AiCopilotDrawer;
import com.studio.ui.PlaywrightCodeArea;
import com.studio.ui.StepScriptBidirectionalSync;
import com.studio.ui.VisualStepFlowPane;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for suite_workspace_page.fxml.
 */
public class SuiteWorkspacePageController {

    @FXML private Label breadcrumbLabel;
    @FXML private Label selectedCaseHint;
    @FXML private TextField startUrlField;
    @FXML private CheckBox stopOnFirstFailureCheck;
    @FXML private CheckBox shareContextCheck;
    @FXML private Button backBtn;
    @FXML private Button addCaseBtn;
    @FXML private Button recordBtn;
    @FXML private Button runCaseBtn;
    @FXML private Button runSuiteBtn;
    @FXML private Button saveBtn;
    @FXML private Button exportExcelBtn;
    @FXML private Button deleteCaseBtn;
    @FXML private Button deleteSuiteBtn;
    @FXML private Button toggleSplitBtn;
    @FXML private Button clearTerminalBtn;
    @FXML private Button hideTerminalBtn;
    @FXML private Button aiAssistBtn;
    @FXML private Button addAssertBtn;
    @FXML private TextArea codeEditor;
    @FXML private TextArea terminalOutput;
    @FXML private ListView<String> caseListView;
    @FXML private TextField caseSearchField;
    @FXML private ListView<Assertion> assertListView;
    @FXML private ComboBox<String> assertTypeFilter;
    @FXML private TextField assertSearchField;
    @FXML private VBox terminalPanel;
    @FXML private TabPane workspaceTabs;
    @FXML private Tab codeTab;
    @FXML private SplitPane workspaceSplitPane;
    @FXML private BorderPane workspaceLayer;
    @FXML private Button copilotBtn;
    @FXML private VBox codePanel;
    @FXML private VBox assertsPanel;
    @FXML private Label codeDirtyLabel;
    @FXML private Label codeStatsLabel;

    private VisualStepFlowPane visualStepFlowPane;
    private PlaywrightCodeArea playwrightCodeArea;
    private StackPane editorContainer;
    private SplitPane dualSplitPane;
    private AiCopilotDrawer aiCopilotDrawer;
    private Button viewNoCodeBtn;
    private Button viewJavaBtn;
    private Button viewSplitBtn;
    private Button assertsToggleBtn;
    /** True once the header's roomy width was measured with the view-mode labels attached. */
    private boolean headerMeasured;
    private double headerLabelsWidth;
    private boolean compactHeader;
    /** Divider to restore when the Asserts panel rejoins the split. */
    private double lastAssertsDivider = 0.6;
    private boolean suppressDualSync;
    /** Envelope (package/import/class/main) + parsed body of the script currently in the editor. */
    private StepScriptBidirectionalSync.Document scriptDocument;
    /** Script last produced from the step list; a different editor text means the user typed code. */
    private String lastGeneratedScript = "";
    private PauseTransition codeSyncDebounce;
    private boolean saveIconReady;
    private static final int CODE_REPARSE_DELAY_MS = 250;

    @FXML private VBox suiteInfoPanel;
    @FXML private Label suiteInfoTitle;
    @FXML private Label suiteInfoStatus;
    @FXML private Label suiteInfoStats;
    @FXML private Label suiteInfoDesc;
    @FXML private ComboBox<String> suiteStatusCombo;

    private TestSuite suite;
    private Path suitePath;
    private TestCase selectedCase;
    /** Harness log lines waiting for the next FX pulse; keeps one CSS pass for a whole burst. */
    private final java.util.Queue<String> pendingTerminalLines = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean terminalFlushQueued = new AtomicBoolean(false);
    /** Wrapped text budget of the terminal; older lines are dropped beyond it. */
    private static final int TERMINAL_MAX_CHARS = 120_000;
    private final ProjectStore projectStore = new ProjectStore();
    private final SettingsStore settingsStore = new SettingsStore();
    private final ReportStore reportStore = new ReportStore();
    private boolean controlsWired;
    private String lastSavedCode = "";
    private boolean suppressCaseSelection;
    private boolean suppressTabChange;
    private boolean suppressDirtyTracking;
    private boolean suppressUrlTracking;
    /** Case name for context menu (right-click); does not change ListView selection. */
    private String contextCaseName;

    private static final java.util.concurrent.atomic.AtomicReference<Process> activeCodegenProcess = new java.util.concurrent.atomic.AtomicReference<>();
    /** Registered once per controller so it can be unregistered when the tab closes. */
    private Runnable runProgressListener;

    static {
        ResourceReleaseService.registerCleanupTask("codegen-abort", SuiteWorkspacePageController::abortActiveCodegen);
    }

    public static void abortActiveCodegen() {
        Process p = activeCodegenProcess.getAndSet(null);
        if (p != null && p.isAlive()) {
            try {
                p.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Unregisters the run-progress listener. Called when the workspace tab closes so a discarded
     * controller is not retained (and refreshed) for the rest of the session.
     */
    public void detachRunProgressListener() {
        Runnable listener = runProgressListener;
        if (listener != null) {
            RunProgressService.removeListener(listener);
            runProgressListener = null;
        }
    }

    public void loadSuite(TestSuite suite, Path suitePath) {
        loadSuite(suite, suitePath, null, false);
    }

    public void loadSuite(TestSuite suite, Path suitePath, String caseToSelect) {
        loadSuite(suite, suitePath, caseToSelect, false);
    }

    public void loadSuite(TestSuite suite, Path suitePath, String caseToSelect, boolean startRecord) {
        this.suitePath = suitePath;
        try {
            this.suite = projectStore.loadAndMigrateSuite(suitePath);
        } catch (IOException e) {
            this.suite = suite;
            if (this.suite != null) {
                this.suite.migrateLegacyAssertionsToCases();
            }
        }
        this.selectedCase = null;

        String projectDirStr = StudioPaths.getProjectDir().toString();
        String relPath = suitePath.toString().startsWith(projectDirStr)
                ? suitePath.toString().substring(projectDirStr.length())
                : suitePath.toString();
        if (relPath.startsWith("/") || relPath.startsWith("\\")) {
            relPath = relPath.substring(1);
        }
        breadcrumbLabel.setText("Khám phá / " + relPath.replace('\\', '/'));
        MainShellController shell = MainShellController.getInstance();
        if (shell != null) {
            String name = this.suite != null && this.suite.getName() != null
                    ? this.suite.getName()
                    : suitePath.getFileName().toString();
            shell.setActiveWorkspace(name, suitePath);
        }
        updateSelectedCaseHint();
        if (codeEditor != null) {
            codeEditor.setEditable(true);
            setEditorText("", true);
        }
        setupCommonControls();
        if (caseToSelect != null && !caseToSelect.isBlank()
                && caseListView.getItems().contains(caseToSelect)) {
            Platform.runLater(() -> {
                suppressCaseSelection = true;
                caseListView.getSelectionModel().select(caseToSelect);
                suppressCaseSelection = false;
                loadCase(caseToSelect);
                updateCaseActionButtons();
                if (workspaceTabs != null && codeTab != null) {
                    workspaceTabs.getSelectionModel().select(codeTab);
                }
                if (startRecord) {
                    Platform.runLater(this::startRecord);
                }
            });
        } else {
            showSuiteInfoMode();
            if (startRecord) {
                ToastService.warn("Không chọn được case để ghi hình");
            }
        }
    }

    private void setupCommonControls() {
        refreshCaseList(null);

        refreshAssertsList();

        SettingsStore.Settings settings;
        try {
            settings = new SettingsStore().load();
        } catch (IOException e) {
            settings = new SettingsStore.Settings();
        }
        stopOnFirstFailureCheck.setSelected(settings.isStopOnFirstFailureDefault());
        shareContextCheck.setSelected(settings.isShareContextDefault());

        if (!controlsWired) {
            setupCaseListCells();
            if (caseSearchField != null) {
                caseSearchField.textProperty().addListener((obs, o, n) -> refreshCaseList(null));
            }
            if (assertSearchField != null) {
                assertSearchField.textProperty().addListener((obs, o, n) -> refreshAssertsList());
            }
            runProgressListener = () -> {
                if (caseListView != null) {
                    caseListView.refresh();
                }
                refreshLiveStepTrace();
            };
            RunProgressService.addListener(runProgressListener);
            caseListView.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldVal, newVal) -> {
                        if (suppressCaseSelection) return;
                        if (oldVal != null && !Objects.equals(oldVal, newVal) && !tryLeaveUnsavedCode(false)) {
                            suppressCaseSelection = true;
                            caseListView.getSelectionModel().select(oldVal);
                            suppressCaseSelection = false;
                            return;
                        }
                        persistSelectedCaseUrl();
                        if (newVal != null) {
                            loadCase(newVal);
                        } else {
                            selectedCase = null;
                            setEditorText("", true);
                            clearUrlField();
                            updateSelectedCaseHint();
                            showSuiteInfoMode();
                        }
                        updateCaseActionButtons();
                    });
            if (workspaceTabs != null) {
                workspaceTabs.getSelectionModel().selectedItemProperty().addListener(
                        (obs, oldTab, newTab) -> {
                            if (suppressTabChange || oldTab == null || newTab == null) return;
                            if (oldTab == codeTab && newTab != codeTab && !tryLeaveUnsavedCode(true)) {
                                suppressTabChange = true;
                                workspaceTabs.getSelectionModel().select(oldTab);
                                suppressTabChange = false;
                            }
                        });
            }
            assertTypeFilter.getItems().setAll(StatusDisplay.ALL_LABEL);
            assertTypeFilter.getItems().addAll(AssertTypeDisplay.FILTER_KEYS);
            AssertTypeDisplay.wireFilterCombo(assertTypeFilter);
            assertTypeFilter.setValue(StatusDisplay.ALL_LABEL);
            assertTypeFilter.setOnAction(e -> refreshAssertsList());
            if (backBtn != null) {
                backBtn.setOnAction(e -> goBack());
                UiIcons.setGraphic(backBtn, UiIcons.BACK, 12);
                com.studio.util.UiTooltips.install(backBtn, "Quay lại trang Khám phá");
            }
            if (addCaseBtn != null) {
                addCaseBtn.setOnAction(e -> addNewCase());
                UiIcons.setGraphic(addCaseBtn, UiIcons.PLUS, 12);
                com.studio.util.UiTooltips.install(addCaseBtn, "Thêm Case mới vào Suite");
            }
            if (recordBtn != null) {
                recordBtn.setOnAction(e -> startRecord());
                UiIcons.setThemedGraphic(recordBtn, UiIcons.RECORD, TOOLBAR_ICON_SIZE, UiIcons.STYLE_DANGER);
                com.studio.util.UiTooltips.install(recordBtn, "Ghi hình thao tác trình duyệt qua Playwright Codegen");
            }
            if (runCaseBtn != null) {
                runCaseBtn.setOnAction(e -> runCase());
                UiIcons.setThemedGraphic(runCaseBtn, UiIcons.PLAY, TOOLBAR_ICON_SIZE, UiIcons.STYLE_SUCCESS);
                com.studio.util.UiTooltips.install(runCaseBtn, "Chạy riêng test case đang chọn");
            }
            if (runSuiteBtn != null) {
                runSuiteBtn.setOnAction(e -> runSuite());
                UiIcons.setThemedGraphic(runSuiteBtn, UiIcons.RUN_ALL, TOOLBAR_ICON_SIZE, UiIcons.STYLE_SUCCESS);
                com.studio.util.UiTooltips.install(runSuiteBtn, "Chạy toàn bộ các test case trong Suite");
            }
            if (aiAssistBtn != null) {
                UiIcons.setThemedGraphic(aiAssistBtn, UiIcons.AI_SPARKLES, TOOLBAR_ICON_SIZE, UiIcons.STYLE_ACCENT);
                com.studio.util.UiTooltips.install(aiAssistBtn, "AI Hỗ trợ — Sinh kịch bản và Assertions tự động");
            }
            if (saveBtn != null) {
                saveBtn.setOnAction(e -> saveCode());
                UiIcons.setGraphic(saveBtn, UiIcons.SAVE, 12);
                com.studio.util.UiTooltips.install(saveBtn, "Lưu kịch bản Playwright (Ctrl+S)");
            }
            if (exportExcelBtn != null) {
                exportExcelBtn.setOnAction(e -> exportSuiteExcel());
                UiIcons.setThemedGraphic(exportExcelBtn, UiIcons.EXCEL, TOOLBAR_ICON_SIZE, UiIcons.STYLE_SUCCESS);
                com.studio.util.UiTooltips.install(exportExcelBtn, "Xuất toàn bộ kịch bản kiểm thử ra file Excel chuẩn QA");
            }
            if (deleteCaseBtn != null) {
                deleteCaseBtn.setOnAction(e -> deleteSelectedCase());
                UiIcons.setThemedGraphic(deleteCaseBtn, UiIcons.TRASH, TOOLBAR_ICON_SIZE, UiIcons.STYLE_DANGER);
                com.studio.util.UiTooltips.install(deleteCaseBtn, "Xóa case đang chọn");
            }
            if (deleteSuiteBtn != null) {
                deleteSuiteBtn.setOnAction(e -> deleteCurrentSuite());
                UiIcons.setThemedGraphic(deleteSuiteBtn, UiIcons.TRASH, TOOLBAR_ICON_SIZE, UiIcons.STYLE_DANGER);
                com.studio.util.UiTooltips.install(deleteSuiteBtn, "Xóa toàn bộ Suite này");
            }
            if (toggleSplitBtn != null) {
                toggleSplitBtn.setOnAction(e -> toggleSplitLayout());
                UiIcons.setGraphic(toggleSplitBtn, UiIcons.SPLIT, 12);
                com.studio.util.UiTooltips.install(toggleSplitBtn, isSplitLayout ? "Thu gọn Asserts để mở rộng Code" : "Hiện Asserts song song với Code");
            }
            if (clearTerminalBtn != null) {
                clearTerminalBtn.setOnAction(e -> clearTerminal());
                UiIcons.setGraphic(clearTerminalBtn, UiIcons.TRASH, 12);
                com.studio.util.UiTooltips.install(clearTerminalBtn, "Xóa toàn bộ log terminal");
            }
            if (hideTerminalBtn != null) {
                hideTerminalBtn.setOnAction(e -> hideTerminal());
                UiIcons.setGraphic(hideTerminalBtn, UiIcons.CLOSE, 12);
                com.studio.util.UiTooltips.install(hideTerminalBtn, "Thu gọn panel terminal");
            }
            if (addAssertBtn != null) {
                addAssertBtn.setOnAction(e -> openAssertBuilder());
                UiIcons.setGraphic(addAssertBtn, UiIcons.PLUS, 12);
                com.studio.util.UiTooltips.install(addAssertBtn, "Quản lý danh mục assertions");
            }
            if (startUrlField != null) {
                com.studio.util.UiTooltips.install(startUrlField, "URL trang web bắt đầu dùng khi Ghi hình hoặc Chạy test");
            }
            if (stopOnFirstFailureCheck != null) {
                com.studio.util.UiTooltips.install(stopOnFirstFailureCheck, "Dừng cả Suite ngay khi gặp một case thất bại");
            }
            if (shareContextCheck != null) {
                com.studio.util.UiTooltips.install(shareContextCheck, "Dùng chung BrowserContext giữa các case trong Suite");
            }
            if (caseSearchField != null) {
                com.studio.util.UiTooltips.install(caseSearchField, "Tìm kiếm test case theo tên");
            }
            if (assertSearchField != null) {
                com.studio.util.UiTooltips.install(assertSearchField, "Tìm kiếm assertion theo loại hoặc giá trị");
            }
            if (assertTypeFilter != null) {
                com.studio.util.UiTooltips.install(assertTypeFilter, "Lọc assertions theo loại");
            }
            setupAssertListCells();
            if (codeEditor != null) {
                codeEditor.setEditable(true);
                codeEditor.textProperty().addListener((obs, o, n) -> {
                    if (!suppressDirtyTracking) {
                        updateSaveButtonState();
                    }
                });
            }
            if (startUrlField != null) {
                startUrlField.textProperty().addListener((obs, o, n) -> {
                    updateCaseActionButtons();
                    if (suppressUrlTracking || selectedCase == null) return;
                    selectedCase.setStartUrl(n != null ? n.trim() : "");
                });
                startUrlField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (wasFocused && !isFocused) {
                        persistSelectedCaseUrl();
                    }
                });
            }
            if (suiteStatusCombo != null) {
                suiteStatusCombo.getItems().setAll("ACTIVE", "DRAFT", "INACTIVE");
                suiteStatusCombo.setCellFactory(cb -> statusComboCell());
                suiteStatusCombo.setButtonCell(statusComboCell());
                suiteStatusCombo.setOnAction(e -> onSuiteStatusChosen());
            }
            setupDualViewInCodePanel();
            controlsWired = true;
        }
        updateCaseActionButtons();
        updateSaveButtonState();
    }

    private static final int VIEW_NO_CODE = 0;
    private static final int VIEW_CODE = 1;
    private static final int VIEW_SPLIT = 2;
    private static final int VIEW_MODE_ICON_SIZE = 12;
    private static final int TOOLBAR_ICON_SIZE = 12;
    /** {@code editor-header-bar} padding, left + right (see {@code studio.css}). */
    private static final double HEADER_HORIZONTAL_INSETS = 20;
    /** Gaps around the caption: title↔caption and caption↔switcher. */
    private static final double HEADER_GAPS = 2;
    /** Width the title must keep before the view switcher drops its labels instead of the title. */
    private static final double HEADER_TITLE_MIN_WIDTH = 96;
    /** Below this the switcher preference cannot be a measured, styled label row. */
    private static final double HEADER_MEASURABLE_TOOLS_WIDTH = 120;
    /** Below this the Asserts panel is lifted out of the split so the editor stays usable. */
    private static final double ASSERTS_HIDE_BELOW_WIDTH = 700;
    /** Above this the Asserts panel comes back on its own (hysteresis avoids flapping). */
    private static final double ASSERTS_SHOW_ABOVE_WIDTH = 800;
    private static final double ASSERTS_DEFAULT_DIVIDER = 0.6;
    /** Marks the header toggle that mirrors the Asserts panel's visibility. */
    private static final String CSS_TOGGLE_ACTIVE = "active";
    private void setupDualViewInCodePanel() {
        if (codePanel == null || visualStepFlowPane != null) return;

        visualStepFlowPane = new VisualStepFlowPane();
        playwrightCodeArea = new PlaywrightCodeArea();
        VBox.setVgrow(playwrightCodeArea, Priority.ALWAYS);
        VBox.setVgrow(visualStepFlowPane, Priority.ALWAYS);

        dualSplitPane = new SplitPane();
        dualSplitPane.getStyleClass().add("workspace-dual-split");
        dualSplitPane.setDividerPositions(0.5);
        VBox.setVgrow(dualSplitPane, Priority.ALWAYS);

        editorContainer = new StackPane();
        editorContainer.getStyleClass().add("workspace-editor-container");
        VBox.setVgrow(editorContainer, Priority.ALWAYS);

        // Keep codeEditor in hierarchy for backwards compatibility and test bindings
        if (codeEditor != null) {
            codeEditor.setVisible(false);
            codeEditor.setManaged(false);
        }

        // Header view mode switcher: No-Code, Java Code, Split
        HBox viewSwitcher = new HBox(4);
        viewSwitcher.setAlignment(Pos.CENTER_RIGHT);
        viewSwitcher.getStyleClass().add("view-mode-switcher");

        viewNoCodeBtn = createViewModeButton("No-Code", UiIcons.GRID, "Xem và sắp xếp các bước trực quan");
        viewJavaBtn = createViewModeButton("Java Code", UiIcons.CODE, "Xem mã Playwright Java");
        viewSplitBtn = createViewModeButton("Chia đôi", UiIcons.SPLIT, "Xem song song No-Code và mã Java");

        viewNoCodeBtn.setOnAction(e -> switchWorkspaceViewMode(VIEW_NO_CODE));
        viewJavaBtn.setOnAction(e -> switchWorkspaceViewMode(VIEW_CODE));
        viewSplitBtn.setOnAction(e -> switchWorkspaceViewMode(VIEW_SPLIT));

        viewSwitcher.getChildren().addAll(viewNoCodeBtn, viewJavaBtn, viewSplitBtn);

        // The Asserts panel toggle rides with the view switcher: when the workspace is too narrow
        // for both panes the split drops Asserts, and this is the way back.
        HBox headerTools = new HBox(4);
        headerTools.setAlignment(Pos.CENTER_RIGHT);
        headerTools.getStyleClass().add("editor-header-tools");
        assertsToggleBtn = createAssertsToggleButton();
        headerTools.getChildren().addAll(assertsToggleBtn, viewSwitcher);
        updateAssertsToggleState();

        if (!codePanel.getChildren().isEmpty() && codePanel.getChildren().get(0) instanceof HBox headerBar) {
            headerBar.getChildren().add(headerTools);
            installHeaderCompaction(headerBar, headerTools);
        }
        installResponsiveAsserts();

        codePanel.getChildren().add(editorContainer);

        // Synchronize No-Code steps change -> script updates (disabled steps are skipped).
        // The script is rendered through the parsed document so a recorded Java class keeps its
        // package/import/main envelope instead of being replaced by bare statements.
        visualStepFlowPane.setOnStepsChanged(() -> {
            if (suppressDualSync || suppressDirtyTracking) return;
            String script = renderScriptFromSteps();
            lastGeneratedScript = script;
            suppressDualSync = true;
            try {
                if (codeEditor != null) {
                    codeEditor.setText(script);
                }
                if (playwrightCodeArea != null && !script.equals(playwrightCodeArea.getText())) {
                    playwrightCodeArea.replaceTextPreservingView(script);
                }
            } finally {
                suppressDualSync = false;
            }
            updateSaveButtonState();
        });

        // Synchronize PlaywrightCodeArea text change -> codeEditor, then re-parse after a pause.
        // Re-parsing on every keystroke would rebuild every step card many times per second.
        codeSyncDebounce = new PauseTransition(Duration.millis(CODE_REPARSE_DELAY_MS));
        codeSyncDebounce.setOnFinished(event -> syncStepsFromCode(false));
        playwrightCodeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (suppressDualSync || suppressDirtyTracking) return;
            suppressDualSync = true;
            try {
                if (codeEditor != null) {
                    codeEditor.setText(newText != null ? newText : "");
                }
            } finally {
                suppressDualSync = false;
            }
            updateSaveButtonState();
            if (aiCopilotDrawer != null && aiCopilotDrawer.isVisible()) {
                aiCopilotDrawer.refreshContext();
            }
            codeSyncDebounce.playFromStart();
        });

        visualStepFlowPane.setOnStepSelected(step -> {
            if (aiCopilotDrawer != null && aiCopilotDrawer.isVisible()) {
                aiCopilotDrawer.refreshContext();
            }
        });

        setupAiCopilot();
        switchWorkspaceViewMode(VIEW_NO_CODE); // Default to No-Code First!
    }

    private Button createViewModeButton(String text, String iconPath, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().addAll("btn-segmented", "small");
        // A segmented control whose labels collapse to "No-C…" / "Chia…" is unusable: the switcher
        // keeps its natural width and the header gives up its secondary text instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setAccessibleText(text);
        UiIcons.setGraphic(button, iconPath, VIEW_MODE_ICON_SIZE);
        UiTooltips.install(button, tooltip);
        return button;
    }

    /**
     * Keeps the editor header readable in a narrow code panel. Priority order is fixed:
     * <ol>
     *   <li>the view switcher keeps its icons, dropping only its labels;</li>
     *   <li>the line-count caption steps aside;</li>
     *   <li>the case title is the only text allowed to ellipsize.</li>
     * </ol>
     * Without this the header used to cut "No-C…", "Java C…" and "Chia …" mid-word, leaving three
     * unidentifiable buttons.
     */
    private void installHeaderCompaction(HBox headerBar, HBox tools) {
        if (codeStatsLabel == null) {
            return;
        }
        tools.setMinWidth(Region.USE_PREF_SIZE);
        codeStatsLabel.setMinWidth(0);
        headerBar.widthProperty().addListener((obs, oldWidth, newWidth) -> refreshHeaderChrome(headerBar, tools));
        // The first layout pass happens before the buttons know their styled font, so the switcher
        // still reports an icon-sized preference. Re-measure once its layout bounds become real.
        tools.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (!headerMeasured) {
                refreshHeaderChrome(headerBar, tools);
            }
        });
        refreshHeaderChrome(headerBar, tools);
    }

    /** Applies the compact/roomy header state for the current header width. */
    private void refreshHeaderChrome(HBox headerBar, HBox tools) {
        double width = headerBar.getWidth();
        if (width <= 0) {
            return;
        }
        // Measured once, while the labels are still on the buttons: after the first compaction the
        // pref width shrinks and can no longer be used as the "roomy" baseline.
        if (!headerMeasured) {
            double measured = tools.prefWidth(-1);
            if (measured < HEADER_MEASURABLE_TOOLS_WIDTH) {
                // A styled switcher is always wider than this: the CSS pass has not run yet, so keep
                // the labels and wait for the next measurement instead of guessing "compact".
                return;
            }
            headerLabelsWidth = measured;
            headerMeasured = true;
        }
        boolean compact = width < headerLabelsWidth + HEADER_TITLE_MIN_WIDTH + HEADER_HORIZONTAL_INSETS;
        if (compact != compactHeader) {
            compactHeader = compact;
            applyCompactSwitcher(compact);
        }
        boolean showCaption = !compact
                && width >= HEADER_HORIZONTAL_INSETS + codeStatsLabel.prefWidth(-1) + tools.prefWidth(-1)
                + headerBar.getSpacing() * HEADER_GAPS + HEADER_TITLE_MIN_WIDTH;
        codeStatsLabel.setVisible(showCaption);
        codeStatsLabel.setManaged(showCaption);
    }

    /** Icon-only mode keeps every button identifiable through its tooltip and accessible text. */
    private void applyCompactSwitcher(boolean compact) {
        for (Button button : new Button[]{viewNoCodeBtn, viewJavaBtn, viewSplitBtn}) {
            if (button == null) {
                continue;
            }
            button.setText(compact ? "" : button.getAccessibleText());
        }
    }

    private Button createAssertsToggleButton() {
        Button button = new Button();
        button.getStyleClass().addAll("flat", "editor-header-toggle");
        button.setAccessibleText("Ẩn hoặc hiện danh mục Asserts");
        UiTooltips.install(button, "Ẩn / hiện danh mục Asserts");
        UiIcons.setGraphic(button, UiIcons.CHECK, VIEW_MODE_ICON_SIZE);
        button.setOnAction(e -> toggleAssertsPanel());
        return button;
    }

    /**
     * Drops the Asserts panel out of the split when the workspace cannot hold both panes, and puts
     * it back when there is room again. The split keeps a usable width for either side instead of
     * squeezing both into unreadable columns.
     */
    private void installResponsiveAsserts() {
        if (workspaceSplitPane == null) {
            return;
        }
        workspaceSplitPane.widthProperty().addListener((obs, oldWidth, newWidth) ->
                syncAssertsPanelVisibility(newWidth.doubleValue()));
        syncAssertsPanelVisibility(workspaceSplitPane.getWidth());
    }

    private void syncAssertsPanelVisibility(double width) {
        if (width <= 0) {
            return;
        }
        boolean shown = isAssertsPanelShown();
        if (shown && width < ASSERTS_HIDE_BELOW_WIDTH) {
            setAssertsPanelVisible(false);
        } else if (!shown && width > ASSERTS_SHOW_ABOVE_WIDTH) {
            setAssertsPanelVisible(true);
        }
    }

    /** @return true when the Asserts panel currently occupies a split slot. */
    public boolean isAssertsPanelShown() {
        return workspaceSplitPane != null && assertsPanel != null
                && workspaceSplitPane.getItems().contains(assertsPanel);
    }

    /** Shows or hides the Asserts panel; also syncs the header toggle. */
    public void setAssertsPanelVisible(boolean visible) {
        if (workspaceSplitPane == null || assertsPanel == null) {
            return;
        }
        boolean shown = isAssertsPanelShown();
        if (visible && !shown) {
            workspaceSplitPane.getItems().add(assertsPanel);
            workspaceSplitPane.setDividerPositions(lastAssertsDivider);
        } else if (!visible && shown) {
            double[] dividers = workspaceSplitPane.getDividerPositions();
            if (dividers.length > 0) {
                lastAssertsDivider = dividers[0];
            }
            workspaceSplitPane.getItems().remove(assertsPanel);
        }
        updateAssertsToggleState();
    }

    private void toggleAssertsPanel() {
        setAssertsPanelVisible(!isAssertsPanelShown());
    }

    private void updateAssertsToggleState() {
        if (assertsToggleBtn == null) {
            return;
        }
        boolean shown = isAssertsPanelShown();
        if (shown) {
            if (!assertsToggleBtn.getStyleClass().contains(CSS_TOGGLE_ACTIVE)) {
                assertsToggleBtn.getStyleClass().add(CSS_TOGGLE_ACTIVE);
            }
        } else {
            assertsToggleBtn.getStyleClass().remove(CSS_TOGGLE_ACTIVE);
        }
    }

    /**
     * Renders every authored step inside the parsed script envelope (class wrapper preserved).
     *
     * <p>A disabled step is included on purpose: it emits {@link
     * StepScriptBidirectionalSync#DISABLED_STEP_PLACEHOLDER} instead of a statement, which keeps the
     * executed line count — and therefore the run's step numbers — aligned with the card numbers.
     * Dropping it here would shift every later verdict onto the wrong card.</p>
     */
    private String renderScriptFromSteps() {
        List<TestStep> steps = visualStepFlowPane.getAllSteps();
        String startUrl = startUrlField != null ? startUrlField.getText() : "";
        if (scriptDocument != null) {
            return scriptDocument.render(steps, startUrl);
        }
        return StepScriptBidirectionalSync.stepsToScript(steps, startUrl);
    }

    /**
     * Re-reads the script text into step cards. Only runs when the text was not produced by
     * {@link #renderScriptFromSteps()}, i.e. the user actually typed in the code view.
     *
     * @param force re-parse even when the text is unchanged (used right before saving)
     */
    private void syncStepsFromCode(boolean force) {
        if (visualStepFlowPane == null || playwrightCodeArea == null) {
            return;
        }
        String code = playwrightCodeArea.getText();
        if (code == null || (!force && code.equals(lastGeneratedScript))) {
            return;
        }
        StepScriptBidirectionalSync.Document parsed = StepScriptBidirectionalSync.parseDocument(code);
        List<TestStep> steps = mergeDisabledSteps(parsed.getSteps(), visualStepFlowPane.getAllSteps());
        scriptDocument = parsed;
        lastGeneratedScript = code;
        suppressDualSync = true;
        try {
            visualStepFlowPane.loadSteps(steps);
        } finally {
            suppressDualSync = false;
        }
    }

    /**
     * A switched-off step emits a placeholder line instead of a statement, so re-parsing the code
     * cannot see it. Re-insert each one at its previous position instead of dropping authored work.
     */
    private static List<TestStep> mergeDisabledSteps(List<TestStep> parsed, List<TestStep> previous) {
        if (previous == null || previous.isEmpty()) {
            return parsed;
        }
        List<TestStep> merged = new ArrayList<>(parsed);
        for (TestStep step : previous) {
            if (step == null || step.isEnabled()) {
                continue;
            }
            int index = Math.min(Math.max(step.getStepNumber() - 1, 0), merged.size());
            merged.add(index, step);
        }
        if (merged.size() != parsed.size()) {
            for (int i = 0; i < merged.size(); i++) {
                merged.get(i).setStepNumber(i + 1);
            }
        }
        return merged;
    }

    private void switchWorkspaceViewMode(int mode) {
        if (editorContainer == null) return;

        // Reparent the two editors instead of stacking them: a JavaFX node has exactly one parent,
        // so adding the split pane on top of the same children used to leave Split view empty.
        editorContainer.getChildren().clear();
        dualSplitPane.getItems().clear();

        switch (mode) {
            case VIEW_CODE -> editorContainer.getChildren().add(playwrightCodeArea);
            case VIEW_SPLIT -> {
                dualSplitPane.getItems().setAll(visualStepFlowPane, playwrightCodeArea);
                dualSplitPane.setDividerPositions(0.5);
                editorContainer.getChildren().add(dualSplitPane);
            }
            case VIEW_NO_CODE -> editorContainer.getChildren().add(visualStepFlowPane);
            default -> editorContainer.getChildren().add(visualStepFlowPane);
        }

        if (viewNoCodeBtn != null) {
            viewNoCodeBtn.getStyleClass().removeAll("active");
            viewJavaBtn.getStyleClass().removeAll("active");
            viewSplitBtn.getStyleClass().removeAll("active");
            switch (mode) {
                case VIEW_CODE -> viewJavaBtn.getStyleClass().add("active");
                case VIEW_SPLIT -> viewSplitBtn.getStyleClass().add("active");
                default -> viewNoCodeBtn.getStyleClass().add("active");
            }
        }
    }

    /** Docks the AI Copilot drawer over the workspace and wires its actions to the selected step. */
    private void setupAiCopilot() {
        if (workspaceLayer == null && editorContainer == null) return;
        if (aiCopilotDrawer == null) {
            aiCopilotDrawer = new AiCopilotDrawer(new AiCopilotDrawer.CopilotContext() {
                @Override
                public TestCase testCase() {
                    return selectedCase;
                }

                @Override
                public TestStep step() {
                    return visualStepFlowPane != null ? visualStepFlowPane.getSelectedStep() : null;
                }

                @Override
                public String startUrl() {
                    if (startUrlField != null && startUrlField.getText() != null && !startUrlField.getText().isBlank()) {
                        return startUrlField.getText().trim();
                    }
                    return selectedCase != null ? selectedCase.getStartUrl() : "";
                }

                @Override
                public String currentScript() {
                    return codeEditor != null ? codeEditor.getText() : "";
                }

                @Override
                public AiConfig aiConfig() {
                    try {
                        return settingsStore.load().getAiConfig();
                    } catch (IOException ex) {
                        AiLoggingService.error("AI-COPILOT", "Không đọc được cấu hình AI", ex);
                        return null;
                    }
                }

                @Override
                public void applySelector(String selector) {
                    if (visualStepFlowPane == null) return;
                    visualStepFlowPane.updateSelectedStepTarget(selector);
                    ToastService.success("Đã áp dụng selector vào bước.");
                }

                @Override
                public void applyAssertion(TestStep assertionStep) {
                    if (visualStepFlowPane == null || assertionStep == null) return;
                    visualStepFlowPane.appendStep(assertionStep);
                    switchWorkspaceViewMode(VIEW_NO_CODE);
                }

                @Override
                public void requestConfiguration() {
                    ToastService.warn("Chưa có API Key — cấu hình AI trước khi dùng AI Copilot.");
                    AiSettingsDialog.show(settingsStore);
                }

                @Override
                public void onVisibilityChanged(boolean visible) {
                    if (workspaceLayer != null) {
                        workspaceLayer.setRight(visible ? aiCopilotDrawer : null);
                    }
                }
            });
            aiCopilotDrawer.setVisible(false);
            aiCopilotDrawer.setManaged(false);
            if (workspaceLayer != null) {
                workspaceLayer.setRight(null);
            } else {
                editorContainer.getChildren().add(aiCopilotDrawer);
            }
        }
        if (copilotBtn != null) {
            UiIcons.setGraphic(copilotBtn, UiIcons.AI_SPARKLES, VIEW_MODE_ICON_SIZE);
            UiTooltips.install(copilotBtn, "AI Copilot — Sửa selector và sinh assertion ngay trong workspace");
        }
    }

    /** FXML action: opens/closes the docked AI Copilot drawer. */
    @FXML
    public void toggleAiCopilot() {
        if (selectedCase == null) {
            ToastService.warn("Hãy chọn một test case trước khi mở AI Copilot.");
            return;
        }
        setupAiCopilot();
        if (aiCopilotDrawer == null) {
            return;
        }
        aiCopilotDrawer.toggleDrawer();
        if (aiCopilotDrawer.isVisible()) {
            aiCopilotDrawer.refreshContext();
        }
    }

    private ListCell<String> statusComboCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : StatusDisplay.label(item));
            }
        };
    }

    private void onSuiteStatusChosen() {
        if (suite == null || suitePath == null || suiteStatusCombo == null) return;
        String chosen = suiteStatusCombo.getValue();
        if (chosen == null || chosen.isBlank()) return;
        String current = suite.getStatus() != null ? suite.getStatus() : "DRAFT";
        if (StatusDisplay.normalize(current).equals(StatusDisplay.normalize(chosen))) {
            return;
        }
        try {
            projectStore.updateSuiteStatus(suitePath, StatusDisplay.normalize(chosen));
            suite.setStatus(StatusDisplay.normalize(chosen));
            refreshSuiteInfoPanel();
            updateCaseActionButtons();
            ToastService.success("Đã cập nhật trạng thái suite: " + StatusDisplay.label(chosen));
        } catch (IOException ex) {
            ToastService.error("Không lưu được trạng thái suite: " + ex.getMessage());
            refreshSuiteInfoPanel();
        }
    }

    private void setupCaseListCells() {
        ContextMenu menu = buildCaseContextMenu();
        // Uniform row height lets VirtualFlow skip per-cell measurement during
        // scrolling, which is what made long lists stutter. Every cell here is a
        // single line (no wrapped labels), so a fixed size is safe.
        caseListView.setFixedCellSize(34);
        caseListView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                private final HBox row = new HBox(8);
                private final Label nameLabel = new Label();
                private final Label statusLabel = new Label();
                private final Button runBtn = new Button();
                private final Button deleteBtn = new Button();

                {
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("case-row");
                    nameLabel.getStyleClass().add("case-row-label");
                    nameLabel.setMaxWidth(Double.MAX_VALUE);
                    // Single line + ellipsis, same as the assert rows: a case name
                    // must never be cut mid-word when the list panel narrows.
                    nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);
                    statusLabel.getStyleClass().add("case-row-status");
                    statusLabel.setMinWidth(Label.USE_PREF_SIZE);
                    runBtn.getStyleClass().add("case-row-run-btn");
                    UiIcons.setThemedGraphic(runBtn, UiIcons.PLAY, TOOLBAR_ICON_SIZE, UiIcons.STYLE_SUCCESS);
                    com.studio.util.UiTooltips.install(runBtn, "Chạy case này");
                    runBtn.setFocusTraversable(false);
                    // Consume press so ListView does not change selection; run immediately
                    runBtn.setOnMousePressed(e -> {
                        String name = getItem();
                        if (name != null) {
                            runCaseFromRow(name);
                        }
                        e.consume();
                    });
                    deleteBtn.getStyleClass().add("case-row-delete-btn");
                    UiIcons.setThemedGraphic(deleteBtn, UiIcons.TRASH, TOOLBAR_ICON_SIZE, UiIcons.STYLE_DANGER);
                    com.studio.util.UiTooltips.install(deleteBtn, "Xóa case này");
                    deleteBtn.setFocusTraversable(false);
                    deleteBtn.setOnMousePressed(e -> {
                        String name = getItem();
                        if (name != null) {
                            deleteCaseFromRow(name);
                        }
                        e.consume();
                    });
                    row.getChildren().addAll(nameLabel, statusLabel, runBtn, deleteBtn);
                }

                @Override
                protected void updateItem(String name, boolean empty) {
                    super.updateItem(name, empty);
                    getStyleClass().removeAll(
 "status-active", "status-draft", "status-inactive", "status-ready",
 "status-passed", "status-failed", "status-skipped", "status-error",
 "status-pending", "status-running", "status-unknown");
                    statusLabel.getStyleClass().removeAll(
 "status-active", "status-draft", "status-inactive", "status-ready",
 "status-passed", "status-failed", "status-skipped", "status-error",
 "status-pending", "status-running", "status-unknown");
                    UiMotion.stopPulse(statusLabel);
                    if (empty || name == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    TestCase tc = findCaseByName(name);
                    boolean showRun = false;
                    nameLabel.setText(name);
                    UiTooltips.install(nameLabel, name);
                    String suiteName = suite != null ? suite.getName() : null;
                    String overlay = tc != null
                            ? RunProgressService.overlayForCase(tc.getId(), suiteName, name)
                            : RunProgressService.overlayForCase(null, suiteName, name);
                    if (overlay != null) {
                        String sc = StatusDisplay.styleClass(overlay);
                        getStyleClass().add(sc);
                        statusLabel.getStyleClass().add(sc);
                        statusLabel.setText(StatusDisplay.label(overlay));
                        if ("RUNNING".equalsIgnoreCase(overlay)) {
                            UiMotion.startPulse(statusLabel);
                        }
                        showRun = false;
                    } else if (tc != null && tc.getStatus() != null) {
                        String sc = StatusDisplay.styleClass(tc.getStatus());
                        getStyleClass().add(sc);
                        statusLabel.getStyleClass().add(sc);
                        statusLabel.setText(StatusDisplay.label(tc.getStatus()));
                        showRun = StatusDisplay.canRunCase(suiteStatus(), tc.getStatus())
                                && suitePath != null
                                && !BusyButtons.isBusy();
                    } else {
                        statusLabel.setText("");
                    }
                    runBtn.setVisible(showRun);
                    runBtn.setManaged(showRun);
                    runBtn.setDisable(!showRun);
                    String tip;
                    if (!StatusDisplay.isActive(suiteStatus())) {
                        tip = "Suite chưa Đang hoạt động — không chạy case được";
                    } else if (tc != null && !StatusDisplay.isRunnable(tc.getStatus())) {
                        tip = "Case chưa Sẵn sàng — ghi hình trước";
                    } else {
                        tip = "Chạy case \"" + name + "\"";
                    }
                    com.studio.util.UiTooltips.install(runBtn, tip);
                    setText(null);
                    setGraphic(row);
                }
            };
            cell.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                // Row run button handles its own press — do not toggle selection
                if (isCaseRowRunButtonEvent(e)) {
                    return;
                }
                if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    if (cell.isEmpty() || cell.getItem() == null) {
                        contextCaseName = null;
                    } else {
                        contextCaseName = cell.getItem();
                    }
                    showCaseContextMenu(menu, cell, e.getScreenX(), e.getScreenY());
                    e.consume();
                    return;
                }
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && e.getClickCount() == 1
                        && !cell.isEmpty() && cell.getItem() != null
                        && cell.getItem().equals(caseListView.getSelectionModel().getSelectedItem())) {
                    contextCaseName = null;
                    javafx.application.Platform.runLater(() -> {
                        if (!tryLeaveUnsavedCode(false)) return;
                        caseListView.getSelectionModel().clearSelection();
                    });
                    e.consume();
                }
            });
            return cell;
        });
        caseListView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.SECONDARY || e.isConsumed()) {
                return;
            }
            ListCell<?> cell = findListCellAncestor(e.getTarget());
            if (cell != null && !cell.isEmpty()) {
                return;
            }
            contextCaseName = null;
            showCaseContextMenu(menu, caseListView, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        menu.setOnHidden(ev -> contextCaseName = null);
    }

    private String resolveCaseMenuTarget() {
        return contextCaseName != null
                ? contextCaseName
                : caseListView.getSelectionModel().getSelectedItem();
    }

    private static boolean isCaseRowRunButtonEvent(javafx.scene.input.MouseEvent e) {
        if (!(e.getTarget() instanceof javafx.scene.Node node)) {
            return false;
        }
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (cur.getStyleClass().contains("case-row-run-btn")
                    || cur.getStyleClass().contains("case-row-delete-btn")) {
                return true;
            }
            if (cur instanceof ListCell<?>) {
                return false;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private static ListCell<?> findListCellAncestor(Object target) {
        if (!(target instanceof javafx.scene.Node node)) {
            return null;
        }
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (cur instanceof ListCell<?> cell) {
                return cell;
            }
            cur = cur.getParent();
        }
        return null;
    }

    private void showCaseContextMenu(ContextMenu menu, javafx.scene.Node anchor,
                                     double screenX, double screenY) {
        if (menu == null || anchor == null) return;
        if (menu.isShowing()) menu.hide();
        if (anchor.getScene() != null && anchor.getScene().getWindow() != null) {
            menu.show(anchor.getScene().getWindow(), screenX, screenY);
        } else {
            menu.show(anchor, screenX, screenY);
        }
    }

    private ContextMenu buildCaseContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("studio-context-menu");
        menu.setOnShowing(ev -> {
            menu.getItems().clear();
            String selected = resolveCaseMenuTarget();
            if (selected != null) {
                TestCase tc = findCaseByName(selected);
                MenuItem recordItem = menuItem("Ghi hình lại Case", () -> {
                    ensureCaseSelectedForAction(selected);
                    startRecord();
                });
                recordItem.setDisable(!hasStartUrlForCase(tc) || BusyButtons.isBusy());
                menu.getItems().add(recordItem);
                if (StatusDisplay.canRunCase(suiteStatus(),
                        tc != null ? tc.getStatus() : null)) {
                    MenuItem runItem = menuItem("Chạy Case này", () -> {
                        ensureCaseSelectedForAction(selected);
                        runCase();
                    });
                    runItem.setDisable(BusyButtons.isBusy());
                    menu.getItems().add(runItem);
                }
                menu.getItems().addAll(
                        new SeparatorMenuItem(),
                        menuItem("Sửa Asserts", () -> {
                            ensureCaseSelectedForAction(selected);
                            openAssertBuilder();
                        }),
                        menuItem("Lưu code", () -> {
                            ensureCaseSelectedForAction(selected);
                            saveCode();
                        }),
                        new SeparatorMenuItem(),
                        menuItem("Xóa Case", () -> {
                            deleteCaseFromRow(selected);
                        })
                );
            } else {
                menu.getItems().add(menuItem("Case mới", this::addNewCase));
            }
        });
        return menu;
    }

    private void ensureCaseSelectedForAction(String caseName) {
        if (caseName == null) return;
        if (caseName.equals(caseListView.getSelectionModel().getSelectedItem())) {
            return;
        }
        caseListView.getSelectionModel().select(caseName);
        if (selectedCase == null || !caseName.equals(selectedCase.getName())) {
            loadCase(caseName);
        }
    }

    private boolean hasStartUrlForCase(TestCase tc) {
        if (tc != null && tc.getStartUrl() != null && !tc.getStartUrl().isBlank()) {
            return true;
        }
        return hasStartUrl();
    }

    private MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(ev -> action.run());
        return item;
    }

    private boolean hasStartUrl() {
        return startUrlField != null
                && startUrlField.getText() != null
                && !startUrlField.getText().trim().isEmpty();
    }

    private String suiteStatus() {
        return suite != null ? suite.getStatus() : null;
    }

    private void updateCaseActionButtons() {
        updateRunSuiteVisibility();
        boolean hasCase = selectedCase != null;
        boolean suiteActive = StatusDisplay.isActive(suiteStatus()) && !BusyButtons.isBusy();
        boolean hasUrl = hasStartUrlForCase(selectedCase);

        if (recordBtn != null) {
            recordBtn.setDisable(!hasCase || !hasUrl || BusyButtons.isBusy());
            recordBtn.setVisible(hasCase);
            recordBtn.setManaged(hasCase);
        }
        if (runCaseBtn != null) {
            boolean caseRunnable = hasCase && StatusDisplay.isRunnable(selectedCase.getStatus());
            runCaseBtn.setDisable(!hasCase || !suiteActive || !caseRunnable || BusyButtons.isBusy());
            runCaseBtn.setVisible(hasCase);
            runCaseBtn.setManaged(hasCase);
        }
        if (aiAssistBtn != null) {
            aiAssistBtn.setDisable(!hasCase || BusyButtons.isBusy());
        }
        if (saveBtn != null) {
            saveBtn.setDisable(!hasCase || BusyButtons.isBusy());
        }
        if (deleteCaseBtn != null) {
            deleteCaseBtn.setDisable(!hasCase || BusyButtons.isBusy());
            deleteCaseBtn.setVisible(hasCase);
            deleteCaseBtn.setManaged(hasCase);
        }
        if (toggleSplitBtn != null) {
            toggleSplitBtn.setVisible(hasCase);
            toggleSplitBtn.setManaged(hasCase);
        }
        if (caseListView != null) {
            caseListView.refresh();
        }
    }

    private void updateRunSuiteVisibility() {
        if (runSuiteBtn == null) return;
        boolean suiteActive = StatusDisplay.isActive(suiteStatus()) && !BusyButtons.isBusy();
        boolean hasRunnableCases = suite != null && suite.getCases() != null
                && suite.getCases().stream()
                        .anyMatch(c -> StatusDisplay.isRunnable(c.getStatus()) && c.hasActiveAssertions());
        boolean show = suite != null && suiteActive;
        runSuiteBtn.setVisible(show);
        runSuiteBtn.setManaged(show);
        runSuiteBtn.setText("Chạy suite");
        UiIcons.setThemedGraphic(runSuiteBtn, UiIcons.RUN_ALL, TOOLBAR_ICON_SIZE, UiIcons.STYLE_SUCCESS);
        runSuiteBtn.setOnAction(e -> runSuite());
        runSuiteBtn.setDisable(!suiteActive || !hasRunnableCases || BusyButtons.isBusy());
        com.studio.util.UiTooltips.install(runSuiteBtn,
                !suiteActive
                        ? "Chỉ hiện khi suite Đang hoạt động — đổi ở Thông tin Suite"
                        : (!hasRunnableCases
                                ? "Suite không có test case nào đang hoạt động để chạy"
                                : "Chạy toàn bộ case trong suite"));
    }

    private void updateSelectedCaseHint() {
        if (selectedCaseHint == null) return;
        if (selectedCase == null) {
            selectedCaseHint.setText("Chọn một case để ghi hình / chạy / sửa code");
        } else {
            selectedCaseHint.setText("Case: " + selectedCase.getName());
        }
        updateCaseActionButtons();
    }

    private void showSuiteInfoMode() {
        if (suiteInfoPanel != null) {
            suiteInfoPanel.setVisible(true);
            suiteInfoPanel.setManaged(true);
            refreshSuiteInfoPanel();
        }
        if (workspaceTabs != null) {
            workspaceTabs.setVisible(false);
            workspaceTabs.setManaged(false);
        }
        if (workspaceSplitPane != null) {
            workspaceSplitPane.setVisible(false);
            workspaceSplitPane.setManaged(false);
        }
        if (startUrlField != null) {
            startUrlField.setDisable(true);
        }
        if (aiCopilotDrawer != null) {
            aiCopilotDrawer.hideDrawer();
        }
        updateCaseActionButtons();
    }

    private void showCaseWorkspaceMode() {
        if (suiteInfoPanel != null) {
            suiteInfoPanel.setVisible(false);
            suiteInfoPanel.setManaged(false);
        }
        if (workspaceTabs != null) {
            workspaceTabs.setVisible(true);
            workspaceTabs.setManaged(true);
        }
        if (workspaceSplitPane != null) {
            workspaceSplitPane.setVisible(true);
            workspaceSplitPane.setManaged(true);
        }
        if (startUrlField != null) {
            startUrlField.setDisable(false);
        }
        updateCaseActionButtons();
    }

    private boolean isSplitLayout = true;

    @FXML
    public void toggleSplitLayout() {
        if (workspaceSplitPane == null) return;
        isSplitLayout = !isSplitLayout;
        if (isSplitLayout) {
            workspaceSplitPane.setDividerPositions(0.62);
            if (assertsPanel != null) {
                assertsPanel.setVisible(true);
                assertsPanel.setManaged(true);
            }
            if (toggleSplitBtn != null) {
                toggleSplitBtn.setText("Chia đôi");
                com.studio.util.UiTooltips.install(toggleSplitBtn, "Thu gọn Asserts để mở rộng Code");
                UiIcons.setGraphic(toggleSplitBtn, UiIcons.SPLIT, 12);
            }
        } else {
            workspaceSplitPane.setDividerPositions(1.0);
            if (assertsPanel != null) {
                assertsPanel.setVisible(false);
                assertsPanel.setManaged(false);
            }
            if (toggleSplitBtn != null) {
                toggleSplitBtn.setText("Toàn màn hình");
                com.studio.util.UiTooltips.install(toggleSplitBtn, "Hiện Asserts song song với Code");
                UiIcons.setGraphic(toggleSplitBtn, UiIcons.FULLSCREEN, 12);
            }
        }
    }

    private void refreshSuiteInfoPanel() {
        if (suiteInfoTitle == null) return;
        if (suite == null) {
            suiteInfoTitle.setText("—");
            if (suiteInfoStatus != null) suiteInfoStatus.setText("");
            if (suiteInfoStats != null) suiteInfoStats.setText("");
            if (suiteInfoDesc != null) suiteInfoDesc.setText("Chưa tải suite");
            return;
        }
        suiteInfoTitle.setText(suite.getName() != null ? suite.getName() : "—");
        if (suiteStatusCombo != null) {
            String st = suite.getStatus() != null ? StatusDisplay.normalize(suite.getStatus()) : "DRAFT";
            if (!"ACTIVE".equals(st) && !"DRAFT".equals(st) && !"INACTIVE".equals(st)) {
                st = "DRAFT";
            }
            suiteStatusCombo.setOnAction(null);
            suiteStatusCombo.setValue(st);
            suiteStatusCombo.setOnAction(e -> onSuiteStatusChosen());
        }
        if (suiteInfoStatus != null) {
            StatusDisplay.apply(suiteInfoStatus, suite.getStatus() != null ? suite.getStatus() : "DRAFT");
        }
        int caseCount = suite.getCases() != null ? suite.getCases().size() : 0;
        long ready = suite.getCases() == null ? 0 : suite.getCases().stream()
                .filter(c -> StatusDisplay.isRunnable(c.getStatus())).count();
        if (suiteInfoStats != null) {
            String gate = StatusDisplay.isActive(suite.getStatus())
                    ? "Có thể chạy"
                    : "Chưa Active — không chạy được";
            suiteInfoStats.setText(String.format("Case: %d  ·  Sẵn sàng: %d  ·  %s",
                    caseCount, ready, gate));
        }
        if (suiteInfoDesc != null) {
            String desc = suite.getDescription();
            suiteInfoDesc.setText(desc != null && !desc.isBlank() ? desc : "Chưa có mô tả");
        }
    }


    private TestCase findCaseByName(String caseName) {
        if (suite == null || suite.getCases() == null || caseName == null) return null;
        return suite.getCases().stream()
                .filter(c -> caseName.equals(c.getName()))
                .findFirst()
                .orElse(null);
    }

    private void loadCase(String caseName) {
        TestCase tc = findCaseByName(caseName);

        if (tc == null) {
            ToastService.warn("Không tìm thấy case: " + caseName);
            return;
        }

        this.selectedCase = tc;
        updateSelectedCaseHint();
        showCaseWorkspaceMode();
        String code = null;
        try {
            code = projectStore.readCaseScript(suitePath, tc.getId());
            codeEditor.setEditable(true);
            setEditorText(code != null ? code
                    : "// Chưa có script — bấm \"Ghi hình\" hoặc viết tay rồi bấm Lưu.\n", true);
        } catch (IOException e) {
            codeEditor.setEditable(true);
            setEditorText("// Lỗi đọc script: " + e.getMessage() + "\n", true);
            ToastService.error("Không đọc được script: " + e.getMessage());
        }
        applyCaseUrlToField(tc, code);
        refreshAssertsList();
        if (aiCopilotDrawer != null && aiCopilotDrawer.isVisible()) {
            aiCopilotDrawer.refreshContext();
        }
    }

    private void applyCaseUrlToField(TestCase tc, String scriptCode) {
        if (startUrlField == null || tc == null) return;
        String url = tc.getStartUrl() != null ? tc.getStartUrl().trim() : "";
        if (url.isEmpty() && scriptCode != null) {
            String extracted = extractNavigateUrl(scriptCode);
            if (extracted != null && !extracted.isBlank()) {
                url = extracted.trim();
                tc.setStartUrl(url);
                try {
                    if (suitePath != null) {
                        projectStore.updateCaseStartUrl(suitePath, tc.getId(), url);
                    }
                } catch (IOException ignored) {
                    // keep in-memory; will retry on next persist
                }
            }
        }
        suppressUrlTracking = true;
        startUrlField.setText(url);
        suppressUrlTracking = false;
    }

    private void clearUrlField() {
        if (startUrlField == null) return;
        suppressUrlTracking = true;
        startUrlField.clear();
        suppressUrlTracking = false;
    }

    /** Persist URL field into the selected case (suite.json). */
    private void persistSelectedCaseUrl() {
        if (selectedCase == null || suitePath == null || startUrlField == null) return;
        String url = startUrlField.getText() != null ? startUrlField.getText().trim() : "";
        String current = selectedCase.getStartUrl() != null ? selectedCase.getStartUrl().trim() : "";
        if (Objects.equals(current, url)) return;
        selectedCase.setStartUrl(url);
        try {
            projectStore.updateCaseStartUrl(suitePath, selectedCase.getId(), url);
        } catch (IOException e) {
            ToastService.error("Không lưu được URL case: " + e.getMessage());
        }
    }

    /** Pull first page.navigate("...") URL from recorded Java script. */
    private static String extractNavigateUrl(String script) {
        if (script == null || script.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\.navigate\\(\\s*\"([^\"]+)\"")
                .matcher(script);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern
                .compile("\\.navigate\\(\\s*'([^']+)'")
                .matcher(script);
        return m.find() ? m.group(1) : null;
    }

    @FXML
    public void addNewCase() {
        if (suite == null) {
            ToastService.warn("Chưa tải suite");
            return;
        }

        EntityFormDialog.showCreateCase(suite.getName()).ifPresent(form -> {
            boolean exists = suite.getCases().stream()
                    .anyMatch(c -> c.getName().equals(form.name()));
            if (exists) {
                ToastService.warn("Case '" + form.name() + "' đã tồn tại");
                return;
            }
            try {
                TestCase tc = projectStore.createCase(
                        suitePath,
                        form.name(),
                        form.description(),
                        form.status(),
                        form.startUrl(),
                        form.browser(),
                        form.headless());
                suite.getCases().add(tc);
                refreshCaseList(tc.getName());
                caseListView.getSelectionModel().select(tc.getName());
                selectedCase = tc;
                loadCase(tc.getName());
                ToastService.success("Đã tạo test case: " + form.name());
                if (form.startRecord()) {
                    Platform.runLater(this::startRecord);
                }
            } catch (IOException e) {
                ToastService.error("Không thể tạo case: " + e.getMessage());
            }
        });
    }

    @FXML
    public void startRecord() {
        if (BusyButtons.isBusy()) {
            ToastService.warn("Đang có thao tác khác — chờ hoàn tất");
            return;
        }
        terminalPanel.setVisible(true);
        terminalPanel.setManaged(true);

        if (suitePath == null || !Files.isDirectory(suitePath)) {
            ToastService.error("Không tìm thấy thư mục suite: " + suitePath);
            return;
        }
        if (selectedCase == null) {
            ToastService.warn("Ghi hình theo Case — hãy chọn hoặc tạo Case trước");
            return;
        }

        String url = startUrlField != null && startUrlField.getText() != null
                ? startUrlField.getText().trim() : "";
        if (url.isEmpty()) {
            ToastService.warn("Điền URL trang cần test trước khi ghi hình");
            if (startUrlField != null) startUrlField.requestFocus();
            return;
        }
        persistSelectedCaseUrl();

        if (caseHasRecordedScript(selectedCase) && !confirmRerecord(selectedCase)) {
            return;
        }

        StatusBarService.set("Đang ghi hình case: " + selectedCase.getName());
        terminalAppend("Ghi hình lại case: " + selectedCase.getName());
        terminalAppend("URL: " + url);
        terminalAppend("Đang khởi động Playwright codegen...");

        final TestCase targetCase = selectedCase;
        BusyButtons.runAsync(
                null,
 "Đang ghi…",
                () -> runCodegenBlocking(targetCase, url),
                exitCode -> {
                    StatusBarService.ready();
                    if (exitCode == 0) {
                        terminalAppend("Ghi hình hoàn tất — đã ghi vào script của case.");
                        try {
                            projectStore.updateCaseStatus(suitePath, targetCase.getId(), "Ready");
                            targetCase.setStatus("Ready");
                            selectedCase = targetCase;
                            String code = projectStore.readCaseScript(suitePath, targetCase.getId());
                            setEditorText(code != null ? code : "", true);
                            int idx = caseListView.getSelectionModel().getSelectedIndex();
                            caseListView.refresh();
                            if (idx >= 0) caseListView.getSelectionModel().select(idx);
                        } catch (IOException ex) {
                            terminalAppend("Không reload được script: " + ex.getMessage());
                        }
                        openAssertBuilder();
                    } else {
                        terminalAppend("Codegen thoát với mã: " + exitCode);
                        ToastService.error("Codegen thoát với mã: " + exitCode);
                    }
                },
                ex -> {
                    StatusBarService.ready();
                    if (ex instanceof InterruptedException) {
                        // This handler runs on the JavaFX Application Thread: interrupting it would
                        // leave the toolkit thread's interrupt flag set and break its internal waits.
                        terminalAppend("Codegen bị ngắt");
                        ToastService.warn("⏹️ Codegen bị ngắt");
                    } else {
                        terminalAppend("Không thể khởi động codegen: " + ex.getMessage());
                        ToastService.error("Không thể khởi chạy Playwright codegen: " + ex.getMessage());
                    }
                },
                this::updateCaseActionButtons,
                runSuiteBtn
        );
    }

    private boolean caseHasRecordedScript(TestCase tc) {
        if (tc == null || suitePath == null) return false;
        try {
            String code = projectStore.readCaseScript(suitePath, tc.getId());
            if (code == null || code.isBlank()) return false;
            // Stub from createCase is short; treat Ready or non-stub body as existing recording
            if ("Ready".equals(tc.getStatus())) return true;
            return code.lines().filter(l -> !l.isBlank() && !l.trim().startsWith("//")).count() > 8;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean confirmRerecord(TestCase tc) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Ghi hình lại");
        confirm.setHeaderText("Ghi hình lại case: " + tc.getName());
        confirm.setContentText("Script hiện tại sẽ bị ghi đè bởi codegen mới. Tiếp tục?");
        return confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    @FXML
    private void exportSuiteExcel() {
        if (suite == null || suitePath == null) {
            ToastService.warn("Không có Suite nào đang mở để xuất Excel");
            return;
        }

        Window window = backBtn != null && backBtn.getScene() != null ? backBtn.getScene().getWindow() : null;
        Optional<ExportLanguage> langOpt = ExportLanguageDialog.show(window, "Xuất Kịch Bản Test Cases (Excel)");
        if (langOpt.isEmpty()) return;
        ExportLanguage lang = langOpt.get();

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nService.get(lang, "export.chooser.title.tc_excel"));
        File exportsDir = StudioPaths.getExportsDir().toFile();
        if (exportsDir.exists() || exportsDir.mkdirs()) {
            chooser.setInitialDirectory(exportsDir);
        }
        String sName = suite.getName() != null && !suite.getName().isBlank() ? suite.getName() : "Suite";
        String safeName = sName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filePrefix = I18nService.get(lang, "export.filename.prefix.tc");
        chooser.setInitialFileName(filePrefix + safeName + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File Excel (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(window);
        if (file == null) return;

        try {
            String groupName = suitePath.getParent() != null ? suitePath.getParent().getFileName().toString() : "Project";
            TestCaseExcelExportService.exportSuite(suite, groupName, suitePath, file.toPath(), lang);
            ToastService.success("Đã xuất tài liệu Test Cases: " + file.getName());
        } catch (Exception e) {
            ToastService.error("Lỗi xuất Excel: " + e.getMessage());
        }
    }

    private int runCodegenBlocking(TestCase targetCase, String url) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        Path nodeExe = StudioPaths.getNodeExecutable();
        Path playwrightCli = StudioPaths.getPlaywrightCliDir();

        if (Files.exists(nodeExe) && Files.exists(playwrightCli)) {
            cmd.add(nodeExe.toString());
            cmd.add(playwrightCli.resolve("node_modules/.bin/playwright").toString());
            cmd.add("codegen");
        } else {
            cmd.add("npx");
            cmd.add("playwright");
            cmd.add("codegen");
        }

        cmd.add("--target=java");
        String scriptFile = (targetCase.getScriptFile() != null && !targetCase.getScriptFile().isBlank())
                ? targetCase.getScriptFile()
                : com.studio.util.StringEscapeUtil.safeFileName(targetCase.getName(), "case") + ".java";
        Path outFile = suitePath.resolve(scriptFile);
        cmd.add("--output=" + outFile.toAbsolutePath());

        String browser = resolvedBrowserId();
        if (browser != null) {
            String b = browser.toUpperCase();
            if (b.contains("FIREFOX")) cmd.add("--browser=firefox");
            else if (b.contains("WEBKIT")) cmd.add("--browser=webkit");
            else if (b.contains("EDGE")) {
                cmd.add("--browser=chromium");
                cmd.add("--channel=msedge");
            } else if (b.contains("CHROME") && !b.contains("CHROMIUM")) {
                cmd.add("--browser=chromium");
                cmd.add("--channel=chrome");
            } else {
                cmd.add("--browser=chromium");
            }
        }

        if (url != null && !url.isBlank()) {
            cmd.add(url);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(suitePath.toFile());
        pb.redirectErrorStream(true);

        // Bind bundled browsers and node paths so codegen works completely offline
        Path bundledBrowsers = PlaywrightUtil.resolveBrowsersPath();
        if (bundledBrowsers != null && Files.isDirectory(bundledBrowsers)) {
            pb.environment().put("PLAYWRIGHT_BROWSERS_PATH", bundledBrowsers.toAbsolutePath().toString());
        }
        Path nodeExecutable = StudioPaths.getNodeExecutable();
        if (nodeExecutable != null && Files.exists(nodeExecutable)) {
            Path nodeBin = nodeExecutable.getParent();
            if (nodeBin != null && Files.isDirectory(nodeBin)) {
                String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
                String curPath = pb.environment().getOrDefault("PATH", "");
                pb.environment().put("PATH", nodeBin.toAbsolutePath() + sep + curPath);
            }
        }

        Process process = pb.start();
        activeCodegenProcess.set(process);

        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    Platform.runLater(() -> terminalAppend(finalLine));
                }
            } catch (IOException e) {
                Platform.runLater(() -> terminalAppend("Lỗi đọc output: " + e.getMessage()));
            }
        }, "codegen-out");
        outputThread.setDaemon(true);
        outputThread.start();

        try {
            int exitCode = process.waitFor();
            outputThread.join(5000);
            return exitCode;
        } finally {
            activeCodegenProcess.compareAndSet(process, null);
        }
    }


    private String resolvedBrowserId() {
        return RunTargetResolve.resolveBrowser(selectedCase, suite);
    }

    private void runCaseFromRow(String caseName) {
        if (caseName == null || caseName.isBlank()) return;
        TestCase target = findCaseByName(caseName);
        if (target == null) {
            ToastService.warn("Không tìm thấy case: " + caseName);
            return;
        }
        // Run without changing ListView selection / loaded editor
        runCaseTarget(target);
    }

    @FXML
    public void runCase() {
        if (selectedCase == null) {
            ToastService.warn("Chưa chọn test case");
            return;
        }
        runCaseTarget(selectedCase);
    }

    private void runCaseTarget(TestCase targetCase) {
        if (BusyButtons.isBusy()) {
            ToastService.warn("Đang có thao tác khác — chờ hoàn tất");
            return;
        }
        if (!StatusDisplay.isActive(suiteStatus())) {
            ToastService.warn("Suite chưa Đang hoạt động — đổi trạng thái ở Thông tin Suite");
            return;
        }
        if (targetCase == null || !StatusDisplay.isRunnable(targetCase.getStatus())) {
            ToastService.warn("Case chưa Sẵn sàng — ghi hình trước khi chạy");
            return;
        }
        terminalPanel.setVisible(true);
        terminalPanel.setManaged(true);

        if (suitePath == null) {
            ToastService.error("Chưa tải suite");
            return;
        }

        SettingsStore.Settings settings;
        try {
            settings = new SettingsStore().load();
        } catch (IOException e) {
            ToastService.error("Không tải được cài đặt: " + e.getMessage());
            return;
        }

        String browserId = RunTargetResolve.resolveBrowser(targetCase, suite);
        boolean headless = RunTargetResolve.resolveHeadless(targetCase, suite, settings.isHeadlessDefault());
        boolean stopOnFail = stopOnFirstFailureCheck.isSelected();
        boolean shareCtx = shareContextCheck.isSelected();
        final Path targetSuitePath = suitePath;
        String caseName = targetCase.getName();
        String scriptFile = (targetCase.getScriptFile() != null && !targetCase.getScriptFile().isBlank())
                ? targetCase.getScriptFile()
                : com.studio.util.StringEscapeUtil.safeFileName(targetCase.getName(), "case") + ".java";
        final Path casePath = targetSuitePath.resolve(scriptFile);

        terminalAppend("Đang chạy case: " + caseName + " (" + browserId + ")");
        StatusBarService.set("Đang chạy case: " + caseName);

        BusyButtons.runAsync(
                null,
                null,
                () -> {
                    TestRunnerService caseRunner = new TestRunnerService(projectStore, reportStore);
                    return caseRunner.run("CASE", casePath, headless, browserId, stopOnFail, shareCtx,
                            msg -> Platform.runLater(() -> terminalAppend(msg)));
                },
                report -> {
                    StatusBarService.ready();
                    if (report.getTotal() == 0) {
                        terminalAppend("Không có test case — không lưu báo cáo");
                        ToastService.warn("Không có test case nào để chạy");
                        return;
                    }
                    terminalAppend("Kết quả: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại, " + report.getSkipped() + " bỏ qua");
                    applyRunTrace(report, targetCase);
                    ToastService.success("Case hoàn tất: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại");
                    ViewNavigator.navigateToReportDetail(report.getRunId());
                },
                ex -> {
                    StatusBarService.ready();
                    ToastService.error("Chạy case thất bại: " + ex.getMessage());
                },
                this::updateCaseActionButtons,
                runSuiteBtn, addCaseBtn
        );
    }

    /**
     * Streams the steps of the case that is executing right now onto its cards.
     *
     * <p>Bound to the case identity, not the selection alone: a suite run moves from case to case
     * while the editor still shows one of them, and the trace of a case the user is not looking at
     * must not be painted over the one they are.</p>
     */
    private void refreshLiveStepTrace() {
        if (visualStepFlowPane == null || selectedCase == null || suitePath == null) {
            return;
        }
        if (!RunProgressService.isActiveCase(selectedCase.getId(), selectedCase.getName())) {
            return;
        }
        List<StepResult> trace = RunProgressService.stepTraceFor(RunProgressService.activeCaseKey());
        if (trace.isEmpty()) {
            return;
        }
        visualStepFlowPane.applyStepResults(trace);
    }

    /**
     * Paints a finished case run onto the No-Code cards, so "what did the test actually do" is
     * answerable without opening the report or the generated Java.
     *
     * <p>Guarded by case identity: a case can be run straight from its row while the editor still
     * shows a different one, and those verdicts must never land on another case's steps.</p>
     */
    private void applyRunTrace(RunReport report, TestCase ranCase) {
        if (visualStepFlowPane == null || report == null || ranCase == null) {
            return;
        }
        if (selectedCase == null || !Objects.equals(selectedCase.getId(), ranCase.getId())) {
            return;
        }
        CaseResult ranResult = findCaseResult(report, ranCase);
        if (ranResult == null) {
            return;
        }
        List<StepResult> results = ranResult.getStepResults();
        if (results.isEmpty()) {
            return;
        }
        visualStepFlowPane.applyStepResults(results);
        long failed = results.stream().filter(StepResult::isFailed).count();
        // Only steps that actually executed: the trace also carries a NOT_RUN entry for a step that
        // was switched off, and counting it would claim the run walked a step it never emitted.
        long ran = results.stream().filter(StepResult::isExecuted).count();
        terminalAppend(failed > 0
                ? "Bước lỗi đầu tiên đã được đánh dấu trên kịch bản No-Code."
                : "Tất cả " + ran + " bước đã chạy — kết quả hiện trên từng thẻ bước.");
    }

    /**
     * @return the result row of {@code ranCase} in {@code report}, matched by case id
     *         (falling back to the name when an older report has no id), or {@code null} when the
     *         report does not contain it
     *
     * <p>Package-private so a test can pin the matching rule: taking "the first row that has step
     * results" paints one case's verdicts on another case's cards whenever a suite run is involved.</p>
     */
    static CaseResult findCaseResult(RunReport report, TestCase ranCase) {
        for (CaseResult cr : report.getCases()) {
            if (Objects.equals(cr.getTestCaseId(), ranCase.getId())) {
                return cr;
            }
        }
        for (CaseResult cr : report.getCases()) {
            if (Objects.equals(cr.getTestCaseName(), ranCase.getName())) {
                return cr;
            }
        }
        return null;
    }

    @FXML
    public void runSuite() {
        if (BusyButtons.isBusy()) {
            ToastService.warn("Đang có thao tác khác — chờ hoàn tất");
            return;
        }
        if (!StatusDisplay.isActive(suiteStatus())) {
            ToastService.warn("Suite chưa Đang hoạt động — đổi trạng thái ở Thông tin Suite");
            return;
        }
        if (suite == null || suitePath == null) {
            ToastService.warn("Chưa tải suite");
            return;
        }
        long runnableCount = suite.getCases() != null
                ? suite.getCases().stream()
                        .filter(c -> StatusDisplay.isRunnable(c.getStatus()) && c.hasActiveAssertions())
                        .count()
                : 0;
        if (runnableCount == 0) {
            ToastService.warn("Suite không có test case nào đang hoạt động để chạy");
            return;
        }
        terminalPanel.setVisible(true);
        terminalPanel.setManaged(true);

        SettingsStore.Settings settings;
        try {
            settings = new SettingsStore().load();
        } catch (IOException e) {
            ToastService.error("Không tải được cài đặt: " + e.getMessage());
            return;
        }

        String browserId = RunTargetResolve.resolveBrowser(null, suite);
        boolean headless = RunTargetResolve.resolveHeadless(null, suite, settings.isHeadlessDefault());
        boolean stopOnFail = stopOnFirstFailureCheck.isSelected();
        boolean shareCtx = shareContextCheck.isSelected();
        String suiteName = suite.getName();
        final Path targetSuitePath = suitePath;

        terminalAppend("Đang chạy suite: " + suiteName + " (" + browserId + ")");
        StatusBarService.set("Đang chạy suite: " + suiteName);

        BusyButtons.runAsync(
                runSuiteBtn,
 "Đang chạy…",
                () -> {
                    TestRunnerService suiteRunner = new TestRunnerService(projectStore, reportStore);
                    return suiteRunner.run("SUITE", targetSuitePath, headless, browserId, stopOnFail, shareCtx,
                            msg -> Platform.runLater(() -> terminalAppend(msg)));
                },
                report -> {
                    StatusBarService.ready();
                    if (report.getTotal() == 0) {
                        terminalAppend("Không có test case — không lưu báo cáo");
                        ToastService.warn("Không có test case nào để chạy");
                        return;
                    }
                    terminalAppend("Suite hoàn tất: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại, " + report.getSkipped() + " bỏ qua");
                    ToastService.success("Suite hoàn tất: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại");
                    ViewNavigator.navigateToReportDetail(report.getRunId());
                },
                ex -> {
                    StatusBarService.ready();
                    ToastService.error("Chạy suite thất bại: " + ex.getMessage());
                },
                this::updateCaseActionButtons,
                addCaseBtn
        );
    }

    @FXML
    public void deleteSelectedCase() {
        if (selectedCase == null) {
            ToastService.warn("Chưa chọn case nào");
            return;
        }
        deleteCaseFromRow(selectedCase.getName());
    }

    private void deleteCaseFromRow(String caseName) {
        if (caseName == null || suitePath == null || suite == null) return;
        TestCase tc = findCaseByName(caseName);
        if (tc == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Xác nhận xóa Case");
        confirm.setHeaderText("Xóa Case '" + caseName + "'?");
        confirm.setContentText("Case và file script liên quan sẽ bị xóa. Không thể hoàn tác.");

        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                try {
                    projectStore.deleteCase(suitePath, tc.getId());
                    if (selectedCase != null && tc.getId().equals(selectedCase.getId())) {
                        selectedCase = null;
                        markCodeClean("");
                        updateSelectedCaseHint();
                        showSuiteInfoMode();
                    }
                    TestSuite reloaded = projectStore.loadAndMigrateSuite(suitePath);
                    loadSuite(reloaded, suitePath, null, false);
                    ToastService.success("Đã xóa case: " + caseName);
                } catch (IOException e) {
                    ToastService.error("Không thể xóa case: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    public void deleteCurrentSuite() {
        if (suitePath == null || suite == null) return;
        int caseCount = suite.getCases() != null ? suite.getCases().size() : 0;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Xác nhận xóa Suite");
        confirm.setHeaderText("Xóa Suite '" + suite.getName() + "'?");
        confirm.setContentText(caseCount > 0
                ? "Sẽ xóa toàn bộ case và file script trong suite (" + caseCount + " case). Không thể hoàn tác."
                : "Hành động này không thể hoàn tác.");

        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                try {
                    projectStore.deleteSuite(suitePath);
                    ToastService.success("Đã xóa suite: " + suite.getName());
                    goBack();
                } catch (IOException e) {
                    ToastService.error("Không thể xóa suite: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    public void saveCode() {
        if (!saveCodeInternal(true)) {
            // toast already shown on error / no case
        }
    }

    @FXML
    public void openAiCaseAssistant() {
        if (selectedCase == null) {
            ToastService.warn("Hãy chọn một test case để AI hỗ trợ.");
            return;
        }
        String currentCode = codeEditor != null ? codeEditor.getText() : "";
        AiCasePromptDialog.show(selectedCase, currentCode, suitePath, projectStore, settingsStore,
                (newScript, newAsserts) -> {
                    if (newScript != null && !newScript.isBlank() && codeEditor != null) {
                        setEditorText(newScript, true);
                        markCodeClean(newScript);
                    }
                    if (newAsserts != null) {
                        selectedCase.setAssertions(new java.util.ArrayList<>(newAsserts));
                        refreshAssertsList();
                    }
                    if (selectedCase.getStartUrl() != null && startUrlField != null) {
                        startUrlField.setText(selectedCase.getStartUrl());
                    }
                });
    }

    /** @return true if saved or nothing to save; false on failure / no case */
    private boolean saveCodeInternal(boolean toastOnSuccess) {
        if (selectedCase == null) {
            ToastService.warn("Chưa chọn case để lưu");
            return false;
        }
        // Flush a pending code->steps re-parse so the persisted step model matches the saved script.
        if (codeSyncDebounce != null) {
            codeSyncDebounce.stop();
        }
        if (playwrightCodeArea != null) {
            syncStepsFromCode(true);
        }
        String code = codeEditor.getText() != null ? codeEditor.getText() : "";
        try {
            projectStore.saveCaseScript(suitePath, selectedCase.getId(), code);
            if (visualStepFlowPane != null) {
                // Persist every step, including disabled ones, so toggling a step off is reversible.
                selectedCase.setTestSteps(visualStepFlowPane.getAllSteps());
                if (suitePath != null && suite != null) {
                    projectStore.saveSuite(suite, suitePath);
                }
            }
            // Keep the envelope of hand-edited code for the next regeneration.
            scriptDocument = StepScriptBidirectionalSync.parseDocument(code);
            lastGeneratedScript = code;
            markCodeClean(code);
            if (toastOnSuccess) {
                ToastService.success("Đã lưu code: " + selectedCase.getName());
            }
            terminalAppend("Đã lưu: " + selectedCase.getName() + " (" + code.length() + " ký tự)");
            return true;
        } catch (IOException e) {
            ToastService.error("Không thể lưu code: " + e.getMessage());
            terminalAppend("LỖI LƯU: " + e.getMessage());
            return false;
        }
    }

    private boolean isCodeDirty() {
        if (codeEditor == null || selectedCase == null) return false;
        String current = codeEditor.getText() != null ? codeEditor.getText() : "";
        return !Objects.equals(current, lastSavedCode);
    }

    /**
     * @param restoreEditorOnDiscard when leaving Code tab, restore saved text into editor
     * @return true to proceed with navigation
     */
    private boolean tryLeaveUnsavedCode(boolean restoreEditorOnDiscard) {
        if (!isCodeDirty()) return true;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(alert);
        alert.setTitle("Chưa lưu code");
        alert.setHeaderText("Code có thay đổi chưa lưu"
                + (selectedCase != null ? " — " + selectedCase.getName() : ""));
        alert.setContentText("Lưu trước khi tiếp tục, bỏ thay đổi, hoặc ở lại chỉnh sửa?");

        ButtonType save = new ButtonType("Lưu", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Bỏ thay đổi", ButtonBar.ButtonData.NO);
        ButtonType stay = new ButtonType("Ở lại", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(save, discard, stay);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == stay) {
            return false;
        }
        if (result.get() == save) {
            return saveCodeInternal(true);
        }
        // discard
        if (restoreEditorOnDiscard) {
            setEditorText(lastSavedCode, true);
        } else {
            markCodeClean(lastSavedCode);
        }
        return true;
    }

    private void setEditorText(String text, boolean markClean) {
        if (codeEditor == null) return;
        String script = text != null ? text : "";
        suppressDirtyTracking = true;
        suppressDualSync = true;
        try {
            codeEditor.setText(script);
            if (playwrightCodeArea != null) {
                playwrightCodeArea.replaceText(script);
            }
            // The script on disk is the source of truth: parsing it losslessly (RAW steps keep
            // comments and hand-written calls) means a recorded Java class is never flattened.
            scriptDocument = StepScriptBidirectionalSync.parseDocument(script);
            lastGeneratedScript = script;
            if (visualStepFlowPane != null) {
                List<TestStep> modelSteps = selectedCase != null ? selectedCase.getTestSteps() : null;
                List<TestStep> parsedSteps = scriptDocument.getSteps();
                List<TestStep> steps;
                if (parsedSteps.isEmpty() && modelSteps != null && !modelSteps.isEmpty()) {
                    // Blank/placeholder script: keep the steps authored in a previous session.
                    steps = modelSteps;
                } else {
                    steps = mergeDisabledSteps(parsedSteps, modelSteps);
                }
                visualStepFlowPane.loadSteps(steps);
            }
        } finally {
            suppressDualSync = false;
            suppressDirtyTracking = false;
        }
        if (markClean) {
            markCodeClean(codeEditor.getText());
        } else {
            updateSaveButtonState();
        }
    }

    private void markCodeClean(String code) {
        lastSavedCode = code != null ? code : "";
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        if (saveBtn == null) return;
        boolean dirty = isCodeDirty();
        boolean canSave = selectedCase != null;
        saveBtn.setDisable(!canSave);
        saveBtn.setText(dirty ? "Lưu code *" : "Lưu code");
        if (!saveIconReady) {
            UiIcons.setGraphic(saveBtn, UiIcons.SAVE, 12);
            saveIconReady = true;
        }
        saveBtn.getStyleClass().removeAll(UiIcons.STYLE_WARNING, UiIcons.STYLE_ACCENT);
        saveBtn.getStyleClass().add(dirty ? UiIcons.STYLE_WARNING : UiIcons.STYLE_ACCENT);
        if (codeTab != null) {
            codeTab.setText(dirty ? "Code *" : "Code");
        }
        if (codeDirtyLabel != null) {
            codeDirtyLabel.setVisible(dirty);
            codeDirtyLabel.setManaged(dirty);
        }
        if (codeStatsLabel != null && codeEditor != null) {
            String text = codeEditor.getText();
            int lines = (text == null || text.isEmpty()) ? 0 : PlaywrightCodeArea.countLines(text);
            codeStatsLabel.setText(lines + " dòng · Java Script");
        }
        TabManager tm = ViewNavigator.getTabManager();
        if (tm != null && suitePath != null) {
            String tabId = "SUITE:" + suitePath.toString();
            tm.setDirty(tabId, dirty);
        }
    }

    private void terminalAppend(String message) {
        if (terminalOutput == null || message == null) {
            return;
        }
        pendingTerminalLines.add(message);
        // One flush per FX pulse instead of one append + applyCss per harness line. A suite run emits
        // dozens of lines per second; pushing each one through a full CSS pass is what made the whole
        // workspace window stutter while tests executed.
        if (terminalFlushQueued.compareAndSet(false, true)) {
            Platform.runLater(this::flushTerminal);
        }
    }

    /** Drains buffered harness lines into the terminal and keeps its text within a bounded size. */
    private void flushTerminal() {
        terminalFlushQueued.set(false);
        if (terminalOutput == null) {
            pendingTerminalLines.clear();
            return;
        }
        StringBuilder batch = new StringBuilder();
        String line;
        while ((line = pendingTerminalLines.poll()) != null) {
            batch.append(line).append('\n');
        }
        if (batch.length() == 0) {
            return;
        }
        terminalOutput.appendText(batch.toString());
        trimTerminalIfOversized();
    }

    /**
     * A growing wrapped TextArea re-measures every paragraph on layout, so an unbounded harness log
     * eventually makes the terminal crawl. The oldest lines are dropped once the buffer passes its
     * budget; the run report keeps the complete output.
     */
    private void trimTerminalIfOversized() {
        String text = terminalOutput.getText();
        if (text == null || text.length() <= TERMINAL_MAX_CHARS) {
            return;
        }
        int cut = text.indexOf('\n', text.length() - TERMINAL_MAX_CHARS);
        terminalOutput.setText(text.substring(cut < 0 ? text.length() - TERMINAL_MAX_CHARS : cut + 1));
    }

    @FXML
    public void clearTerminal() {
        pendingTerminalLines.clear();
        terminalOutput.clear();
    }

    @FXML
    public void hideTerminal() {
        terminalPanel.setVisible(false);
        terminalPanel.setManaged(false);
    }

    private void refreshAssertsList() {
        String filter = assertTypeFilter != null ? assertTypeFilter.getValue() : null;
        String search = assertSearchField != null ? assertSearchField.getText() : "";
        assertListView.getItems().clear();

        if (selectedCase == null) {
            return;
        }

        for (Assertion a : selectedCase.getAssertions()) {
            boolean show = filter == null || StatusDisplay.isAll(filter) || assertMatchesFilter(a, filter);
            if (show && assertTextMatches(a, search)) {
                assertListView.getItems().add(a);
            }
        }
    }

    private static boolean assertTextMatches(Assertion a, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        String type = a.getType() != null ? a.getType().toLowerCase(Locale.ROOT) : "";
        String typeLabel = AssertTypeDisplay.label(a.getType()).toLowerCase(Locale.ROOT);
        String value = a.getEffectiveValue().toLowerCase(Locale.ROOT);
        String locator = a.getLocator() != null ? a.getLocator().toLowerCase(Locale.ROOT) : "";
        String status = a.getStatus() != null ? a.getStatus().toLowerCase(Locale.ROOT) : "";
        return type.contains(q) || typeLabel.contains(q) || value.contains(q)
                || locator.contains(q) || status.contains(q);
    }

    private void refreshCaseList(String preferSelect) {
        String selected = preferSelect != null
                ? preferSelect
                : caseListView.getSelectionModel().getSelectedItem();
        String query = caseSearchField != null ? caseSearchField.getText() : "";
        // Rebuild under suppression: clearing the items fires the selection listener with null,
        // which used to blank the editor and drop the active case every time the user typed in the
        // search box. Deletion clears the editor explicitly in deleteCaseFromRow instead.
        boolean previousSuppress = suppressCaseSelection;
        suppressCaseSelection = true;
        try {
            caseListView.getItems().clear();
            if (suite != null && suite.getCases() != null) {
                for (TestCase tc : suite.getCases()) {
                    if (nameContains(tc.getName(), query)) {
                        caseListView.getItems().add(tc.getName());
                    }
                }
            }
            if (selected != null && caseListView.getItems().contains(selected)) {
                caseListView.getSelectionModel().select(selected);
            }
        } finally {
            suppressCaseSelection = previousSuppress;
        }
    }

    private static boolean nameContains(String name, String query) {
        if (query == null || query.isBlank()) return true;
        if (name == null) return false;
        return name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private void setupAssertListCells() {
        assertListView.setCellFactory(lv -> new ListCell<>() {
            private final HBox row = new HBox(8);
            private final Label infoLabel = new Label();
            private final Button editBtn = new Button("Sửa");
            private final Button deleteBtn = new Button("Xóa");

            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("assert-row");
                infoLabel.getStyleClass().add("assert-row-label");
                infoLabel.setMaxWidth(Double.MAX_VALUE);
                // One line per row: wrap made the row height jump around while
                // scrolling and still overflowed in a narrow panel. Ellipsis keeps
                // the list rhythm even and the tooltip carries the full text.
                infoLabel.setWrapText(false);
                infoLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(infoLabel, Priority.ALWAYS);
                editBtn.getStyleClass().add("assert-row-edit-btn");
                deleteBtn.getStyleClass().add("assert-row-delete-btn");
                editBtn.setFocusTraversable(false);
                deleteBtn.setFocusTraversable(false);
                com.studio.util.UiTooltips.install(editBtn, "Sửa assertion");
                com.studio.util.UiTooltips.install(deleteBtn, "Xóa assertion");
                editBtn.setOnAction(e -> {
                    Assertion a = getItem();
                    if (a != null) editAssert(a);
                    e.consume();
                });
                deleteBtn.setOnAction(e -> {
                    Assertion a = getItem();
                    if (a != null) deleteAssert(a);
                    e.consume();
                });
                row.getChildren().addAll(infoLabel, editBtn, deleteBtn);
            }

            @Override
            protected void updateItem(Assertion a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                infoLabel.setText(formatAssertLine(a));
                UiTooltips.install(infoLabel, infoLabel.getText());
                String sc = StatusDisplay.styleClass(a.getStatus());
                infoLabel.getStyleClass().removeAll(
 "status-active", "status-draft", "status-inactive", "status-ready",
 "status-unknown");
                infoLabel.getStyleClass().add(sc);
                setText(null);
                setGraphic(row);
            }
        });
    }

    private static String formatAssertLine(Assertion a) {
        StringBuilder line = new StringBuilder();
        line.append(StatusDisplay.label(a.getStatus())).append(" ").append(AssertTypeDisplay.label(a.getType()));
        String effVal = a.getEffectiveValue();
        if (!effVal.isBlank()) {
            line.append(" = ").append(effVal);
        }
        if (a.getLocator() != null && !a.getLocator().isBlank()) {
            line.append(" [").append(a.getLocator()).append("]");
        }
        return line.toString();
    }

    private void editAssert(Assertion assertion) {
        if (selectedCase == null || assertion == null) return;
        int index = selectedCase.getAssertions().indexOf(assertion);
        if (index < 0) {
            ToastService.warn("Không tìm thấy assertion");
            return;
        }
        AssertBuilderPopupController popup = AssertBuilderPopupController.create();
        popup.setup(suite, selectedCase, suitePath, this::reloadCaseAssertsFromDisk);
        popup.startEditing(index);
        popup.showAndWait();
    }

    private void deleteAssert(Assertion assertion) {
        if (selectedCase == null || assertion == null || suitePath == null) return;
        int index = selectedCase.getAssertions().indexOf(assertion);
        if (index < 0) {
            ToastService.warn("Không tìm thấy assertion");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Xóa assertion");
        confirm.setHeaderText("Xóa assertion?");
        confirm.setContentText(formatAssertLine(assertion));
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return;
        }
        try {
            projectStore.deleteAssertion(suitePath, selectedCase.getId(), assertion);
            selectedCase.getAssertions().remove(index);
            refreshAssertsList();
            ToastService.success("Đã xóa assertion");
        } catch (IOException ex) {
            ToastService.error("Không xóa được: " + ex.getMessage());
        }
    }

    private void reloadCaseAssertsFromDisk() {
        try {
            suite = projectStore.loadAndMigrateSuite(suitePath);
            String caseId = selectedCase.getId();
            selectedCase = suite.getCases().stream()
                    .filter(c -> c.getId().equals(caseId))
                    .findFirst()
                    .orElse(selectedCase);
        } catch (IOException ex) {
            // keep in-memory
        }
        refreshAssertsList();
    }

    private boolean assertMatchesFilter(Assertion a, String filter) {
        String type = a.getType() != null ? a.getType().toUpperCase() : "";
        return switch (filter) {
            case "TEXT" -> type.startsWith("TEXT") || type.startsWith("PAGE_") || type.equals("HEADING_VISIBLE")
                    || type.equals("TOAST_VISIBLE") || type.equals("BREADCRUMB_CONTAINS")
                    || type.equals("DIALOG_ALERT_TEXT_CONTAINS");
            case "ELEMENT" -> type.startsWith("ELEMENT") || type.startsWith("ATTRIBUTE_") || type.startsWith("CSS_")
                    || type.equals("LINK_HREF_CONTAINS") || type.startsWith("IMAGE_") || type.equals("VIDEO_VISIBLE")
                    || type.equals("AUDIO_VISIBLE") || type.startsWith("MODAL_") || type.equals("BADGE_COUNT_EQUALS");
            case "URL" -> type.startsWith("URL");
            case "TITLE" -> type.startsWith("TITLE");
            case "TABLE" -> type.startsWith("TABLE");
            case "FORM" -> type.startsWith("FORM") || type.startsWith("INPUT") || type.startsWith("SELECT_")
                    || type.equals("PLACEHOLDER_EQUALS");
            case "STORAGE" -> type.contains("STORAGE") || type.startsWith("COOKIE_") || type.equals("NO_CONSOLE_ERROR")
                    || type.equals("API_STATUS_EQUALS");
            case "CUSTOM" -> "CUSTOM".equals(type) || "CUSTOM_JS".equals(type) || "AI_SEMANTIC_ASSERT".equals(type);
            default -> true;
        };
    }

    @FXML
    public void openAssertBuilder() {
        if (selectedCase == null) {
            ToastService.warn("Chọn case trước khi sửa assertions");
            return;
        }
        AssertBuilderPopupController popup = AssertBuilderPopupController.create();
        popup.setup(suite, selectedCase, suitePath, this::reloadCaseAssertsFromDisk);
        popup.showAndWait();
    }

    @FXML
    public void goBack() {
        if (!tryLeaveUnsavedCode(false)) {
            return;
        }
        persistSelectedCaseUrl();
        ViewNavigator.navigateTo(ViewNavigator.Page.EXPLORER);
    }
}
