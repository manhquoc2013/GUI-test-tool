package com.studio.controller;

import com.studio.model.*;
import com.studio.service.RunProgressService;
import com.studio.service.StatusBarService;
import com.studio.service.TestRunnerService;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.util.AssertTypeDisplay;
import com.studio.util.BusyButtons;
import com.studio.util.DialogTheme;
import com.studio.util.RunTargetResolve;
import com.studio.util.StatusDisplay;
import com.studio.util.StudioPaths;
import com.studio.util.UiIcons;
import com.studio.util.UiMotion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
    @FXML private VBox codePanel;
    @FXML private VBox assertsPanel;
    @FXML private Label codeDirtyLabel;
    @FXML private Label codeStatsLabel;
    @FXML private VBox suiteInfoPanel;
    @FXML private Label suiteInfoTitle;
    @FXML private Label suiteInfoStatus;
    @FXML private Label suiteInfoStats;
    @FXML private Label suiteInfoDesc;
    @FXML private ComboBox<String> suiteStatusCombo;

    private TestSuite suite;
    private Path suitePath;
    private TestCase selectedCase;
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
                ToastService.warn("⚠️ Không chọn được case để ghi hình");
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
            RunProgressService.addListener(() -> {
                if (caseListView != null) {
                    caseListView.refresh();
                }
            });
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
            }
            if (addCaseBtn != null) {
                addCaseBtn.setOnAction(e -> addNewCase());
                UiIcons.setGraphic(addCaseBtn, UiIcons.PLUS, 12);
            }
            if (recordBtn != null) {
                recordBtn.setOnAction(e -> startRecord());
                UiIcons.setGraphic(recordBtn, UiIcons.RECORD, 12, "#f7768e");
            }
            if (runCaseBtn != null) {
                runCaseBtn.setOnAction(e -> runCase());
                UiIcons.setGraphic(runCaseBtn, UiIcons.PLAY, 12, "#9ece6a");
            }
            if (runSuiteBtn != null) {
                runSuiteBtn.setOnAction(e -> runSuite());
                UiIcons.setGraphic(runSuiteBtn, UiIcons.RUN_ALL, 12, "#73daca");
            }
            if (aiAssistBtn != null) {
                UiIcons.setGraphic(aiAssistBtn, UiIcons.AI_SPARKLES, 12, "#bb9af7");
            }
            if (saveBtn != null) {
                saveBtn.setOnAction(e -> saveCode());
                UiIcons.setGraphic(saveBtn, UiIcons.SAVE, 12, "#7aa2f7");
            }
            if (toggleSplitBtn != null) {
                toggleSplitBtn.setOnAction(e -> toggleSplitLayout());
                UiIcons.setGraphic(toggleSplitBtn, UiIcons.SPLIT, 12);
            }
            if (clearTerminalBtn != null) {
                clearTerminalBtn.setOnAction(e -> clearTerminal());
                UiIcons.setGraphic(clearTerminalBtn, UiIcons.TRASH, 12);
            }
            if (hideTerminalBtn != null) {
                hideTerminalBtn.setOnAction(e -> hideTerminal());
                UiIcons.setGraphic(hideTerminalBtn, UiIcons.CLOSE, 12);
            }
            if (addAssertBtn != null) {
                addAssertBtn.setOnAction(e -> openAssertBuilder());
                UiIcons.setGraphic(addAssertBtn, UiIcons.PLUS, 12);
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
            controlsWired = true;
        }
        updateCaseActionButtons();
        updateSaveButtonState();
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
            ToastService.success("✅ Đã cập nhật trạng thái suite: " + StatusDisplay.label(chosen));
        } catch (IOException ex) {
            ToastService.error("❌ Không lưu được trạng thái suite: " + ex.getMessage());
            refreshSuiteInfoPanel();
        }
    }

    private void setupCaseListCells() {
        ContextMenu menu = buildCaseContextMenu();
        caseListView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                private final HBox row = new HBox(8);
                private final Label nameLabel = new Label();
                private final Label statusLabel = new Label();
                private final Button runBtn = new Button();

                {
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("case-row");
                    nameLabel.getStyleClass().add("case-row-label");
                    nameLabel.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);
                    statusLabel.getStyleClass().add("case-row-status");
                    statusLabel.setMinWidth(Label.USE_PREF_SIZE);
                    runBtn.getStyleClass().add("case-row-run-btn");
                    UiIcons.setGraphic(runBtn, UiIcons.PLAY, 10, "#9ece6a");
                    runBtn.setTooltip(new Tooltip("Chạy case này"));
                    runBtn.setFocusTraversable(false);
                    // Consume press so ListView does not change selection; run immediately
                    runBtn.setOnMousePressed(e -> {
                        String name = getItem();
                        if (name != null) {
                            runCaseFromRow(name);
                        }
                        e.consume();
                    });
                    row.getChildren().addAll(nameLabel, statusLabel, runBtn);
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
                    runBtn.setTooltip(new Tooltip(tip));
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
            if (cur.getStyleClass().contains("case-row-run-btn")) {
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
        boolean caseSelected = selectedCase != null;
        boolean caseRunnable = caseSelected && StatusDisplay.isRunnable(selectedCase.getStatus());
        boolean show = suite != null && (suiteActive || caseSelected);
        runSuiteBtn.setVisible(show);
        runSuiteBtn.setManaged(show);
        if (caseSelected) {
            // Selection drives label/action: run only the selected case
            runSuiteBtn.setText("Chạy case");
            UiIcons.setGraphic(runSuiteBtn, UiIcons.PLAY, 12, "#9ece6a");
            runSuiteBtn.setOnAction(e -> runCase());
            runSuiteBtn.setDisable(!suiteActive || !caseRunnable || BusyButtons.isBusy());
            String tip;
            if (!suiteActive) {
                tip = "Suite chưa Đang hoạt động — đổi ở Thông tin Suite";
            } else if (!caseRunnable) {
                tip = "Case chưa Sẵn sàng — ghi hình trước khi chạy";
            } else {
                tip = "Chạy case đang chọn: " + selectedCase.getName();
            }
            runSuiteBtn.setTooltip(new Tooltip(tip));
        } else {
            // No case selected → run entire suite
            runSuiteBtn.setText("Chạy suite");
            UiIcons.setGraphic(runSuiteBtn, UiIcons.RUN_ALL, 12, "#73daca");
            runSuiteBtn.setOnAction(e -> runSuite());
            runSuiteBtn.setDisable(!suiteActive);
            runSuiteBtn.setTooltip(new Tooltip(
                    suiteActive
                            ? "Chạy toàn bộ case trong suite"
                            : "Chỉ hiện khi suite Đang hoạt động — đổi ở Thông tin Suite"));
        }
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
                toggleSplitBtn.setTooltip(new Tooltip("Thu gọn Asserts để mở rộng Code"));
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
                toggleSplitBtn.setTooltip(new Tooltip("Hiện Asserts song song với Code"));
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
            ToastService.warn("⚠️ Không tìm thấy case: " + caseName);
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
                    : "// Chưa có script — dùng 🔴 Ghi hình lại hoặc viết tay rồi 💾 Lưu.\n", true);
        } catch (IOException e) {
            codeEditor.setEditable(true);
            setEditorText("// Lỗi đọc script: " + e.getMessage() + "\n", true);
            ToastService.error("❌ Không đọc được script: " + e.getMessage());
        }
        applyCaseUrlToField(tc, code);
        refreshAssertsList();
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
            ToastService.error("❌ Không lưu được URL case: " + e.getMessage());
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
            ToastService.warn("⚠️ Chưa tải suite");
            return;
        }

        EntityFormDialog.showCreateCase(suite.getName()).ifPresent(form -> {
            boolean exists = suite.getCases().stream()
                    .anyMatch(c -> c.getName().equals(form.name()));
            if (exists) {
                ToastService.warn("⚠️ Case '" + form.name() + "' đã tồn tại");
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
                ToastService.success("✅ Đã tạo test case: " + form.name());
                if (form.startRecord()) {
                    Platform.runLater(this::startRecord);
                }
            } catch (IOException e) {
                ToastService.error("❌ Không thể tạo case: " + e.getMessage());
            }
        });
    }

    @FXML
    public void startRecord() {
        if (BusyButtons.isBusy()) {
            ToastService.warn("⚠️ Đang có thao tác khác — chờ hoàn tất");
            return;
        }
        terminalPanel.setVisible(true);
        terminalPanel.setManaged(true);

        if (suitePath == null || !Files.isDirectory(suitePath)) {
            ToastService.error("⚠️ Không tìm thấy thư mục suite: " + suitePath);
            return;
        }
        if (selectedCase == null) {
            ToastService.warn("⚠️ Ghi hình theo Case — hãy chọn hoặc tạo Case trước");
            return;
        }

        String url = startUrlField != null && startUrlField.getText() != null
                ? startUrlField.getText().trim() : "";
        if (url.isEmpty()) {
            ToastService.warn("⚠️ Điền 🔗 URL trang cần test trước khi ghi hình");
            if (startUrlField != null) startUrlField.requestFocus();
            return;
        }
        persistSelectedCaseUrl();

        if (caseHasRecordedScript(selectedCase) && !confirmRerecord(selectedCase)) {
            return;
        }

        StatusBarService.set("🔴 Đang ghi hình case: " + selectedCase.getName());
        terminalAppend("🔴 Ghi hình lại case: " + selectedCase.getName());
        terminalAppend("🔗 URL: " + url);
        terminalAppend("Đang khởi động Playwright codegen...");

        final TestCase targetCase = selectedCase;
        BusyButtons.runAsync(
                null,
                "Đang ghi…",
                () -> runCodegenBlocking(targetCase, url),
                exitCode -> {
                    StatusBarService.ready();
                    if (exitCode == 0) {
                        terminalAppend("✅ Ghi hình hoàn tất — đã ghi vào script của case.");
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
                            terminalAppend("⚠️ Không reload được script: " + ex.getMessage());
                        }
                        openAssertBuilder();
                    } else {
                        terminalAppend("Codegen thoát với mã: " + exitCode);
                        ToastService.error("⚠️ Codegen thoát với mã: " + exitCode);
                    }
                },
                ex -> {
                    StatusBarService.ready();
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        terminalAppend("Codegen bị ngắt");
                        ToastService.warn("⏹️ Codegen bị ngắt");
                    } else {
                        terminalAppend("Không thể khởi động codegen: " + ex.getMessage());
                        ToastService.error("❌ Không thể khởi chạy Playwright codegen: " + ex.getMessage());
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
        confirm.setHeaderText("🔴 Ghi hình lại case: " + tc.getName());
        confirm.setContentText("Script hiện tại sẽ bị ghi đè bởi codegen mới. Tiếp tục?");
        return confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
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
        String scriptFile = targetCase.getScriptFile() != null
                ? targetCase.getScriptFile()
                : targetCase.getName() + ".java";
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
        Process process = pb.start();

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

        int exitCode = process.waitFor();
        outputThread.join(5000);
        return exitCode;
    }


    private String resolvedBrowserId() {
        return RunTargetResolve.resolveBrowser(selectedCase, suite);
    }

    private void runCaseFromRow(String caseName) {
        if (caseName == null || caseName.isBlank()) return;
        TestCase target = findCaseByName(caseName);
        if (target == null) {
            ToastService.warn("⚠️ Không tìm thấy case: " + caseName);
            return;
        }
        // Run without changing ListView selection / loaded editor
        runCaseTarget(target);
    }

    @FXML
    public void runCase() {
        if (selectedCase == null) {
            ToastService.warn("⚠️ Chưa chọn test case");
            return;
        }
        runCaseTarget(selectedCase);
    }

    private void runCaseTarget(TestCase targetCase) {
        if (BusyButtons.isBusy()) {
            ToastService.warn("⚠️ Đang có thao tác khác — chờ hoàn tất");
            return;
        }
        if (!StatusDisplay.isActive(suiteStatus())) {
            ToastService.warn("⚠️ Suite chưa Đang hoạt động — đổi trạng thái ở Thông tin Suite");
            return;
        }
        if (targetCase == null || !StatusDisplay.isRunnable(targetCase.getStatus())) {
            ToastService.warn("⚠️ Case chưa Sẵn sàng — ghi hình trước khi chạy");
            return;
        }
        terminalPanel.setVisible(true);
        terminalPanel.setManaged(true);

        if (suitePath == null) {
            ToastService.error("⚠️ Chưa tải suite");
            return;
        }

        SettingsStore.Settings settings;
        try {
            settings = new SettingsStore().load();
        } catch (IOException e) {
            ToastService.error("❌ Không tải được cài đặt: " + e.getMessage());
            return;
        }

        String browserId = RunTargetResolve.resolveBrowser(targetCase, suite);
        boolean headless = RunTargetResolve.resolveHeadless(targetCase, suite, settings.isHeadlessDefault());
        boolean stopOnFail = stopOnFirstFailureCheck.isSelected();
        boolean shareCtx = shareContextCheck.isSelected();
        final Path targetSuitePath = suitePath;
        String caseName = targetCase.getName();
        String scriptFile = targetCase.getScriptFile() != null
                ? targetCase.getScriptFile()
                : targetCase.getName() + ".java";
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
                        ToastService.warn("⚠️ Không có test case nào để chạy");
                        return;
                    }
                    terminalAppend("Kết quả: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại, " + report.getSkipped() + " bỏ qua");
                    ToastService.success("✅ Case hoàn tất: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại");
                    ViewNavigator.navigateToReportDetail(report.getRunId());
                },
                ex -> {
                    StatusBarService.ready();
                    ToastService.error("❌ Chạy case thất bại: " + ex.getMessage());
                },
                this::updateCaseActionButtons,
                runSuiteBtn, addCaseBtn
        );
    }

    @FXML
    public void runSuite() {
        if (BusyButtons.isBusy()) {
            ToastService.warn("⚠️ Đang có thao tác khác — chờ hoàn tất");
            return;
        }
        if (!StatusDisplay.isActive(suiteStatus())) {
            ToastService.warn("⚠️ Suite chưa Đang hoạt động — đổi trạng thái ở Thông tin Suite");
            return;
        }
        terminalPanel.setVisible(true);
        terminalPanel.setManaged(true);

        if (suite == null || suitePath == null) {
            ToastService.warn("⚠️ Chưa tải suite");
            return;
        }

        SettingsStore.Settings settings;
        try {
            settings = new SettingsStore().load();
        } catch (IOException e) {
            ToastService.error("❌ Không tải được cài đặt: " + e.getMessage());
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
                        ToastService.warn("⚠️ Không có test case nào để chạy");
                        return;
                    }
                    terminalAppend("Suite hoàn tất: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại, " + report.getSkipped() + " bỏ qua");
                    ToastService.success("✅ Suite hoàn tất: " + report.getPassed() + " đạt, "
                            + report.getFailed() + " thất bại");
                    ViewNavigator.navigateToReportDetail(report.getRunId());
                },
                ex -> {
                    StatusBarService.ready();
                    ToastService.error("❌ Chạy suite thất bại: " + ex.getMessage());
                },
                this::updateCaseActionButtons,
                addCaseBtn
        );
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
            ToastService.warn("⚠️ Hãy chọn một test case để AI hỗ trợ.");
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
            ToastService.warn("⚠️ Chưa chọn case để lưu");
            return false;
        }
        String code = codeEditor.getText() != null ? codeEditor.getText() : "";
        try {
            projectStore.saveCaseScript(suitePath, selectedCase.getId(), code);
            markCodeClean(code);
            if (toastOnSuccess) {
                ToastService.success("💾 Đã lưu code: " + selectedCase.getName());
            }
            terminalAppend("Đã lưu: " + selectedCase.getName() + " (" + code.length() + " ký tự)");
            return true;
        } catch (IOException e) {
            ToastService.error("❌ Không thể lưu code: " + e.getMessage());
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
        suppressDirtyTracking = true;
        codeEditor.setText(text != null ? text : "");
        suppressDirtyTracking = false;
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
        UiIcons.setGraphic(saveBtn, UiIcons.SAVE, 12, dirty ? "#e0af68" : "#7aa2f7");
        if (codeTab != null) {
            codeTab.setText(dirty ? "Code *" : "Code");
        }
        if (codeDirtyLabel != null) {
            codeDirtyLabel.setVisible(dirty);
            codeDirtyLabel.setManaged(dirty);
        }
        if (codeStatsLabel != null && codeEditor != null) {
            String text = codeEditor.getText();
            int lines = (text == null || text.isEmpty()) ? 0 : text.split("\r\n|\r|\n", -1).length;
            codeStatsLabel.setText(lines + " dòng · Java Script");
        }
        TabManager tm = ViewNavigator.getTabManager();
        if (tm != null && suitePath != null) {
            String tabId = "SUITE:" + suitePath.toString();
            tm.setDirty(tabId, dirty);
        }
    }

    private void terminalAppend(String message) {
        if (terminalOutput == null) return;
        Runnable append = () -> {
            terminalOutput.appendText(message + "\n");
            terminalOutput.applyCss();
        };
        if (Platform.isFxApplicationThread()) {
            append.run();
        } else {
            Platform.runLater(append);
        }
    }

    @FXML
    public void clearTerminal() {
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
        String value = a.getValue() != null ? a.getValue().toLowerCase(Locale.ROOT) : "";
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
        caseListView.getItems().clear();
        if (suite != null && suite.getCases() != null) {
            for (TestCase tc : suite.getCases()) {
                if (nameContains(tc.getName(), query)) {
                    caseListView.getItems().add(tc.getName());
                }
            }
        }
        if (selected != null && caseListView.getItems().contains(selected)) {
            suppressCaseSelection = true;
            caseListView.getSelectionModel().select(selected);
            suppressCaseSelection = false;
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
                infoLabel.setWrapText(true);
                HBox.setHgrow(infoLabel, Priority.ALWAYS);
                editBtn.getStyleClass().add("assert-row-edit-btn");
                deleteBtn.getStyleClass().add("assert-row-delete-btn");
                editBtn.setFocusTraversable(false);
                deleteBtn.setFocusTraversable(false);
                editBtn.setTooltip(new Tooltip("Sửa assertion"));
                deleteBtn.setTooltip(new Tooltip("Xóa assertion"));
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
        line.append(StatusDisplay.label(a.getStatus())).append("  ").append(AssertTypeDisplay.label(a.getType()));
        if (a.getValue() != null && !a.getValue().isBlank()) {
            line.append(" = ").append(a.getValue());
        }
        if (a.getLocator() != null && !a.getLocator().isBlank()) {
            line.append("  [").append(a.getLocator()).append("]");
        }
        return line.toString();
    }

    private void editAssert(Assertion assertion) {
        if (selectedCase == null || assertion == null) return;
        int index = selectedCase.getAssertions().indexOf(assertion);
        if (index < 0) {
            ToastService.warn("⚠️ Không tìm thấy assertion");
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
            ToastService.warn("⚠️ Không tìm thấy assertion");
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
            projectStore.deleteAssertion(suitePath, selectedCase.getId(), index);
            selectedCase.getAssertions().remove(index);
            refreshAssertsList();
            ToastService.success("🗑️ Đã xóa assertion");
        } catch (IOException ex) {
            ToastService.error("❌ Không xóa được: " + ex.getMessage());
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
            case "TEXT" -> type.startsWith("TEXT");
            case "ELEMENT" -> type.startsWith("ELEMENT");
            case "URL" -> type.startsWith("URL");
            case "TITLE" -> type.startsWith("TITLE");
            case "TABLE" -> type.startsWith("TABLE");
            case "FORM" -> type.startsWith("FORM") || type.startsWith("INPUT") || type.equals("PLACEHOLDER_EQUALS");
            case "STORAGE" -> type.contains("STORAGE") || type.equals("COOKIE_EXISTS") || type.equals("COOKIE_VALUE_EQUALS");
            case "CUSTOM" -> "CUSTOM".equals(a.getType()) || "CUSTOM_JS".equals(a.getType());
            default -> true;
        };
    }

    @FXML
    public void openAssertBuilder() {
        if (selectedCase == null) {
            ToastService.warn("⚠️ Chọn case trước khi sửa assertions");
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
