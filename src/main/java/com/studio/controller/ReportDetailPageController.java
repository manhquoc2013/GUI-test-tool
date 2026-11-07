package com.studio.controller;

import com.studio.model.AssertResult;
import com.studio.model.CaseResult;
import com.studio.model.HealedStepMeta;
import com.studio.model.RunReport;
import com.studio.service.ExcelReportService;
import com.studio.service.HtmlReportService;
import com.studio.service.ToastService;
import com.studio.store.ReportStore;
import com.studio.util.AssertTypeDisplay;
import com.studio.util.DialogTheme;
import com.studio.util.ResponsiveTableColumns;
import com.studio.util.StatusDisplay;
import com.studio.util.UiMotion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the report detail page (report_detail_page.fxml).
 * Shows case results, assertion details, log excerpt, screenshot path,
 * and supports Excel export and back navigation.
 */
public class ReportDetailPageController {

    // --- Header ---
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> caseFilter;
    @FXML private Button exportHtmlBtn;
    @FXML private Button exportBtn;
    @FXML private Button deleteReportBtn;
    @FXML private Button backBtn;

    // --- Cases table ---
    @FXML private TableView<CaseResult> casesTable;
    @FXML private TableColumn<CaseResult, Integer> caseIndexCol;
    @FXML private TableColumn<CaseResult, String> caseNameCol;
    @FXML private TableColumn<CaseResult, String> caseSuiteCol;
    @FXML private TableColumn<CaseResult, String> caseStatusCol;
    @FXML private TableColumn<CaseResult, String> caseDurationCol;
    @FXML private TableColumn<CaseResult, Void> caseEvidenceCol;
    @FXML private TableColumn<CaseResult, String> caseReasonCol;

    // --- Asserts detail table ---
    @FXML private TableView<AssertResult> assertsTable;
    @FXML private TableColumn<AssertResult, Integer> assertIndexCol;
    @FXML private TableColumn<AssertResult, String> assertTypeCol;
    @FXML private TableColumn<AssertResult, String> assertDetailCol;
    @FXML private TableColumn<AssertResult, String> assertConfigCol;
    @FXML private TableColumn<AssertResult, String> assertResultCol;
    @FXML private TableColumn<AssertResult, String> assertMessageCol;

    // --- Screenshot ---
    @FXML private Label screenshotLabel;
    @FXML private Button viewScreenshotBtn;
    @FXML private ImageView screenshotView;

    // --- Log excerpt ---
    @FXML private Label detailLogHeader;
    @FXML private TextArea logExcerpt;

    // --- Right detail drawer (help-style overlay) ---
    @FXML private StackPane detailLayer;
    @FXML private Region detailScrim;
    @FXML private VBox caseDetailSidebar;
    @FXML private ScrollPane detailScroll;
    @FXML private VBox detailBody;
    @FXML private Label detailCaseTitle;
    @FXML private Button closeDetailBtn;

    private final ReportStore reportStore = new ReportStore();
    private RunReport currentReport;
    private ObservableList<CaseResult> allCases = FXCollections.observableArrayList();
    private boolean detailVisible = false;

    @FXML
    public void initialize() {
        setupCaseColumns();
        setupAssertColumns();
        setupFilters();
        setupButtons();
        setupSelection();
        wireDetailDrawer();
    }

    private void wireDetailDrawer() {
        if (detailScrim != null) {
            detailScrim.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                closeDetailSidebar();
                e.consume();
            });
        }
        if (caseDetailSidebar != null) {
            caseDetailSidebar.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            if (detailLayer != null) {
                detailLayer.widthProperty().addListener((obs, o, n) -> applyDrawerWidth(n.doubleValue()));
                caseDetailSidebar.prefHeightProperty().bind(detailLayer.heightProperty());
                caseDetailSidebar.maxHeightProperty().bind(detailLayer.heightProperty());
                caseDetailSidebar.minHeightProperty().bind(detailLayer.heightProperty());
            }
        }
        if (detailScroll != null) {
            detailScroll.setFitToWidth(true);
            detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            detailScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        }
        if (detailBody != null && detailScroll != null) {
            detailBody.maxWidthProperty().bind(detailScroll.widthProperty().subtract(4));
            detailBody.minWidthProperty().bind(detailScroll.widthProperty().subtract(4));
        }
        if (screenshotView != null && detailBody != null) {
            screenshotView.fitWidthProperty().bind(detailBody.widthProperty().subtract(32));
            screenshotView.setPreserveRatio(true);
            screenshotView.setSmooth(true);
            screenshotView.setStyle("-fx-cursor: hand;");
            screenshotView.setOnMouseClicked(e -> viewScreenshot());
        }
        if (screenshotLabel != null) {
            screenshotLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            screenshotLabel.setMaxWidth(Double.MAX_VALUE);
        }
        if (assertsTable != null && detailBody != null) {
            assertsTable.maxWidthProperty().bind(detailBody.widthProperty().subtract(8));
        }
    }

    private void applyDrawerWidth(double pageWidth) {
        if (caseDetailSidebar == null || pageWidth <= 0) return;
        double w = Math.max(360, pageWidth * 0.8);
        caseDetailSidebar.setPrefWidth(w);
        caseDetailSidebar.setMinWidth(w);
        caseDetailSidebar.setMaxWidth(w);
    }

    private void setupCaseColumns() {
        ResponsiveTableColumns.install(casesTable,
                ResponsiveTableColumns.Spec.fixed(caseIndexCol, 44),
                ResponsiveTableColumns.Spec.flex(caseNameCol, 1.4, 120),
                ResponsiveTableColumns.Spec.flex(caseSuiteCol, 1.1, 100),
                ResponsiveTableColumns.Spec.fixed(caseStatusCol, 120),
                ResponsiveTableColumns.Spec.fixed(caseDurationCol, 80),
                ResponsiveTableColumns.Spec.fixed(caseEvidenceCol, 110),
                ResponsiveTableColumns.Spec.flex(caseReasonCol, 1.6, 120));

        caseIndexCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(cell.getValue().getIndex() + 1).asObject());

        caseNameCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getTestCaseName()));

        caseSuiteCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getSuiteName() != null
                        ? cell.getValue().getSuiteName() : ""));

        caseStatusCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getStatus()));
        caseStatusCol.setCellFactory(col -> new TableCell<CaseResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    StatusDisplay.apply(this, null);
                } else {
                    StatusDisplay.apply(this, item);
                }
            }
        });

        caseDurationCol.setCellValueFactory(cell -> {
            long ms = cell.getValue().getDurationMs();
            String s = ms + "ms";
            if (ms >= 1000) {
                double secs = ms / 1000.0;
                s = String.format("%.1fs", secs);
            }
            return new javafx.beans.property.SimpleStringProperty(s);
        });

        if (caseEvidenceCol != null) {
            caseEvidenceCol.setCellFactory(col -> new TableCell<CaseResult, Void>() {
                private final Button btn = new Button("🖼️ Ảnh chụp");
                {
                    btn.getStyleClass().add("btn-sm");
                    btn.setStyle("-fx-font-size: 11px; -fx-padding: 2px 8px; -fx-cursor: hand;");
                    btn.setOnAction(e -> {
                        if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                            CaseResult cr = getTableView().getItems().get(getIndex());
                            Path shot = resolveScreenshotFile(cr.getScreenshotPath());
                            if (shot != null) {
                                EvidenceViewerDialog.show(cr.getTestCaseName(), shot);
                            } else {
                                ToastService.info("ℹ️ Case này không có ảnh chụp màn hình.");
                            }
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        CaseResult cr = getTableView().getItems().get(getIndex());
                        Path shot = resolveScreenshotFile(cr.getScreenshotPath());
                        if (shot != null) {
                            setGraphic(btn);
                            setText(null);
                        } else {
                            setGraphic(null);
                            setText("—");
                        }
                    }
                }
            });
        }

        caseReasonCol.setCellValueFactory(cell -> {
            CaseResult cr = cell.getValue();
            if ("SKIPPED".equals(cr.getStatus()) && cr.getSkipReason() != null) {
                return new javafx.beans.property.SimpleStringProperty(cr.getSkipReason());
            }
            if (("FAILED".equals(cr.getStatus()) || "ERROR".equals(cr.getStatus())) && cr.getFailureReason() != null) {
                return new javafx.beans.property.SimpleStringProperty(truncate(cr.getFailureReason(), 120));
            }
            if (cr.getSkipReason() != null && !cr.getSkipReason().isEmpty()) {
                return new javafx.beans.property.SimpleStringProperty(cr.getSkipReason());
            }
            return new javafx.beans.property.SimpleStringProperty("");
        });
        caseReasonCol.setCellFactory(col -> new TableCell<CaseResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    CaseResult cr = getTableRow() != null ? getTableRow().getItem() : null;
                    if (cr != null && cr.getFailureReason() != null && !cr.getFailureReason().isBlank()) {
                        com.studio.util.UiTooltips.install(this, cr.getFailureReason());
                    } else {
                        com.studio.util.UiTooltips.install(this, item);
                    }
                }
            }
        });
    }

    private void setupAssertColumns() {
        Label emptyAssertPlaceholder = new Label("Không có kết quả Assert (kịch bản không khai báo assertion hoặc gặp ngoại lệ trong quá trình chạy).");
        emptyAssertPlaceholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-padding: 10px;");
        assertsTable.setPlaceholder(emptyAssertPlaceholder);

        ResponsiveTableColumns.install(assertsTable,
                ResponsiveTableColumns.Spec.fixed(assertIndexCol, 44),
                ResponsiveTableColumns.Spec.flex(assertTypeCol, 1.3, 120),
                ResponsiveTableColumns.Spec.flex(assertDetailCol, 1.3, 120),
                ResponsiveTableColumns.Spec.fixed(assertConfigCol, 110),
                ResponsiveTableColumns.Spec.fixed(assertResultCol, 110),
                ResponsiveTableColumns.Spec.flex(assertMessageCol, 1.6, 120));

        assertIndexCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(cell.getValue().getIndex() + 1).asObject());

        assertTypeCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        AssertTypeDisplay.label(cell.getValue().getType())));

        assertDetailCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getDetail() != null
                        ? cell.getValue().getDetail() : ""));

        // Config column shows assertStatus if available, otherwise status
        assertConfigCol.setCellValueFactory(cell -> {
            AssertResult ar = cell.getValue();
            String val = ar.getAssertStatus();
            if (val == null || val.isEmpty()) {
                val = ar.getStatus();
            }
            return new javafx.beans.property.SimpleStringProperty(val != null ? val : "");
        });

        assertResultCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getStatus() != null
                        ? cell.getValue().getStatus() : ""));
        assertResultCol.setCellFactory(col -> new TableCell<AssertResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    StatusDisplay.apply(this, null);
                } else {
                    StatusDisplay.apply(this, item);
                }
            }
        });

        assertConfigCol.setCellFactory(col -> new TableCell<AssertResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    StatusDisplay.apply(this, null);
                } else if (isLifecycleStatus(item)) {
                    StatusDisplay.apply(this, item);
                } else {
                    StatusDisplay.apply(this, null);
                    setText(item);
                }
            }
        });

        assertMessageCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getMessage() != null
                        ? cell.getValue().getMessage() : ""));
    }

    private static boolean isLifecycleStatus(String value) {
        String key = StatusDisplay.normalize(value);
        return "ACTIVE".equals(key) || "DRAFT".equals(key) || "INACTIVE".equals(key) || "READY".equals(key);
    }

    @FXML
    private void onCaseFilterChanged() {
        applyCaseFilter();
    }

    private void setupFilters() {
        caseFilter.setItems(FXCollections.observableArrayList(
                StatusDisplay.ALL_LABEL,
                "PASSED",
                "FAILED",
                "ERROR",
                "SKIPPED"
        ));
        caseFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String s) {
                if (s == null || StatusDisplay.isAll(s)) return StatusDisplay.ALL_LABEL;
                return StatusDisplay.label(s);
            }
            @Override
            public String fromString(String s) { return s; }
        });
        caseFilter.setValue(StatusDisplay.ALL_LABEL);
    }

    private void setupButtons() {
        exportBtn.setOnAction(e -> exportExcel());
        backBtn.setOnAction(e -> goBack());
        if (deleteReportBtn != null) {
            deleteReportBtn.setOnAction(e -> deleteCurrentReport());
            com.studio.util.UiIcons.setGraphic(deleteReportBtn, com.studio.util.UiIcons.TRASH, 12, "#f7768e");
        }
        if (exportHtmlBtn != null) {
            com.studio.util.UiIcons.setGraphic(exportHtmlBtn, com.studio.util.UiIcons.GLOBE, 12);
        }
        com.studio.util.UiIcons.setGraphic(exportBtn, com.studio.util.UiIcons.SAVE, 12);
        com.studio.util.UiIcons.setGraphic(backBtn, com.studio.util.UiIcons.BACK, 12);
        viewScreenshotBtn.setOnAction(e -> viewScreenshot());
    }

    private void setupSelection() {
        casesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                showCaseDetail(newSel);
                openDetailSidebar();
            } else {
                setDetailSidebarVisible(false);
            }
        });
    }

    @FXML
    private void closeDetailSidebar() {
        if (casesTable != null && casesTable.getSelectionModel().getSelectedItem() != null) {
            casesTable.getSelectionModel().clearSelection();
        } else {
            setDetailSidebarVisible(false);
        }
    }

    private void openDetailSidebar() {
        setDetailSidebarVisible(true);
    }

    private void setDetailSidebarVisible(boolean show) {
        if (detailLayer == null || caseDetailSidebar == null) {
            detailVisible = show;
            return;
        }
        if (show == detailVisible && detailLayer.isVisible() == show) {
            return;
        }
        if (UiMotion.isBusy(caseDetailSidebar)) {
            return;
        }
        if (show) {
            detailVisible = true;
            applyDrawerWidth(detailLayer.getWidth() > 0
                    ? detailLayer.getWidth()
                    : (detailLayer.getScene() != null ? detailLayer.getScene().getWidth() * 0.8 : 800));
            detailLayer.setManaged(true);
            detailLayer.setVisible(true);
            caseDetailSidebar.setTranslateX(Math.max(caseDetailSidebar.getPrefWidth(), 360));
            if (detailScrim != null) {
                detailScrim.setOpacity(0);
            }
            Platform.runLater(() -> {
                detailLayer.applyCss();
                detailLayer.layout();
                applyDrawerWidth(detailLayer.getWidth());
                UiMotion.playDrawerOpen(detailScrim, caseDetailSidebar, null);
            });
        } else {
            if (!detailLayer.isVisible()) {
                detailVisible = false;
                return;
            }
            UiMotion.playDrawerClose(detailScrim, caseDetailSidebar, () -> {
                detailVisible = false;
                detailLayer.setVisible(false);
                detailLayer.setManaged(false);
            });
        }
    }

    // --- Public API ---

    /**
     * Load report data for the given run ID into the UI.
     * Called by ViewNavigator.navigateToReportDetail().
     */
    public void loadReport(String runId) {
        try {
            currentReport = reportStore.loadReport(runId);
            if (currentReport == null) {
                ToastService.error("⚠️ Không tìm thấy báo cáo: " + runId);
                return;
            }

            // Summary header
            updateSummary();

            // Populate case list
            allCases.clear();
            if (currentReport.getCases() != null) {
                allCases.addAll(currentReport.getCases());
            }
            if (caseFilter != null) {
                caseFilter.setValue(StatusDisplay.ALL_LABEL);
            }
            casesTable.setItems(allCases);

            // Clear detail panes — do not auto-select a case (drawer stays closed)
            assertsTable.setItems(FXCollections.emptyObservableList());
            logExcerpt.clear();
            screenshotLabel.setVisible(false);
            viewScreenshotBtn.setVisible(false);
            if (screenshotView != null) {
                screenshotView.setImage(null);
                screenshotView.setVisible(false);
            }
            casesTable.getSelectionModel().clearSelection();
            setDetailSidebarVisible(false);
        } catch (Exception e) {
            ToastService.error("Failed to load report " + runId + ": " + e.getMessage());
        }
    }

    /** Refresh the summary label from the current report. */
    private void updateSummary() {
        if (currentReport == null) return;
        String runId = currentReport.getRunId() != null ? currentReport.getRunId() : "—";
        summaryLabel.setText(String.format(
                "🆔 %s  ·  ✅ %d Đạt  ·  ❌ %d Thất bại  ·  ⏭️ %d Bỏ qua",
                runId,
                currentReport.getPassed(),
                currentReport.getFailed(),
                currentReport.getSkipped()
        ));
    }

    // --- Case filter ---

    private void applyCaseFilter() {
        String filter = caseFilter.getValue();
        if (StatusDisplay.isAll(filter)) {
            casesTable.setItems(allCases);
            return;
        }
        String want = StatusDisplay.normalize(filter);
        ObservableList<CaseResult> filtered = FXCollections.observableArrayList();
        for (CaseResult cr : allCases) {
            if (want.equals(StatusDisplay.normalize(cr.getStatus()))) {
                filtered.add(cr);
            }
        }
        casesTable.setItems(filtered);
    }

    // --- Case detail ---

    private void showCaseDetail(CaseResult cr) {
        if (detailCaseTitle != null) {
            String name = cr.getTestCaseName() != null ? cr.getTestCaseName() : "Case";
            if (cr.getHealedSteps() != null && !cr.getHealedSteps().isEmpty()) {
                detailCaseTitle.setText("🎯 " + name + " [✨ Auto-Healed: " + cr.getHealedSteps().size() + "]");
            } else {
                detailCaseTitle.setText("🎯 " + name);
            }
        }

        // Assert results table
        List<AssertResult> asserts = cr.getAssertResults();
        if (asserts != null && !asserts.isEmpty()) {
            assertsTable.setItems(FXCollections.observableArrayList(asserts));
        } else {
            assertsTable.getItems().clear();
        }

        // Update detail header based on status
        if (detailLogHeader != null) {
            if ("FAILED".equals(cr.getStatus()) || "ERROR".equals(cr.getStatus())) {
                detailLogHeader.setText("Chi tiết lỗi & Cách tái hiện");
            } else if ("SKIPPED".equals(cr.getStatus())) {
                detailLogHeader.setText("Lý do bỏ qua");
            } else {
                detailLogHeader.setText("Ghi chú thực thi");
            }
        }

        // Build comprehensive detail log
        StringBuilder sb = new StringBuilder();
        if ("FAILED".equals(cr.getStatus())) {
            if (cr.getFailureReason() != null && !cr.getFailureReason().isBlank()) {
                sb.append("❌ NGUYÊN NHÂN THẤT BẠI: ").append(cr.getFailureReason()).append("\n\n");
            }
            if (cr.getLogExcerpt() != null && !cr.getLogExcerpt().isBlank()) {
                sb.append(cr.getLogExcerpt());
            } else if (sb.length() == 0) {
                sb.append("❌ Test case thực thi thất bại.");
            }
        } else if ("ERROR".equals(cr.getStatus())) {
            if (cr.getFailureReason() != null && !cr.getFailureReason().isBlank()) {
                sb.append("⚠️ LỖI KỊCH BẢN / BIÊN DỊCH: ").append(cr.getFailureReason()).append("\n\n");
            }
            if (cr.getLogExcerpt() != null && !cr.getLogExcerpt().isBlank()) {
                sb.append(cr.getLogExcerpt());
            }
        } else if ("SKIPPED".equals(cr.getStatus())) {
            sb.append("⏭️ ĐÃ BỎ QUA: ").append(cr.getSkipReason() != null ? cr.getSkipReason() : "Không có lý do");
        } else if ("PASSED".equals(cr.getStatus())) {
            sb.append("✅ Test case thực thi thành công và đạt tất cả các kiểm tra.");
            if (cr.getScreenshotPath() != null) {
                sb.append(" Xem ảnh chụp màn hình kiểm chứng bên trên.");
            }
        } else {
            sb.append("📝 Không có thông tin bổ sung cho case này.");
        }

        // Self-Healing details
        List<HealedStepMeta> healed = cr.getHealedSteps();
        if (healed != null && !healed.isEmpty()) {
            StringBuilder healedLog = new StringBuilder("\n\n✨ [TỰ ĐỘNG SỬA LỖI - SELF-HEALING]:\n");
            for (HealedStepMeta hm : healed) {
                healedLog.append(String.format(" • %s (%s, %.0f%%): '%s' ➔ '%s'\n",
                        hm.getActionDescription(), hm.getMethod(), hm.getConfidence() * 100,
                        hm.getOriginalSelector(), hm.getRepairedSelector()));
            }
            sb.append(healedLog);
        }

        logExcerpt.setText(sb.toString());

        // Screenshot path + preview
        Path resolvedShot = resolveScreenshotFile(cr.getScreenshotPath());
        if (resolvedShot != null) {
            String pathText = resolvedShot.toAbsolutePath().normalize().toString();
            cr.setScreenshotPath(pathText);
            String fileName = Path.of(pathText).getFileName() != null
                    ? Path.of(pathText).getFileName().toString()
                    : pathText;
            screenshotLabel.setText("📸 " + fileName);
            com.studio.util.UiTooltips.install(screenshotLabel, pathText);
            screenshotLabel.setVisible(true);
            viewScreenshotBtn.setVisible(true);
            loadScreenshotPreview(pathText);
        } else {
            screenshotLabel.setVisible(false);
            viewScreenshotBtn.setVisible(false);
            if (screenshotView != null) {
                screenshotView.setImage(null);
                screenshotView.setVisible(false);
            }
        }
    }

    /**
     * Harness historically wrote {@code case.png} while reports stored
     * {@code case_PASSED.png} / {@code case_FAILED.png}. Accept any of these.
     */
    private static Path resolveScreenshotFile(String recordedPath) {
        if (recordedPath == null || recordedPath.isBlank()) {
            return null;
        }
        Path recorded = Path.of(recordedPath);
        if (Files.isRegularFile(recorded)) {
            return recorded;
        }
        String name = recorded.getFileName() != null ? recorded.getFileName().toString() : "";
        Path parent = recorded.getParent();
        if (parent == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        if (name.endsWith("_PASSED.png")) {
            candidates.add(name.substring(0, name.length() - "_PASSED.png".length()) + ".png");
            candidates.add(name.replace("_PASSED.png", "_FAILED.png"));
        } else if (name.endsWith("_FAILED.png")) {
            candidates.add(name.substring(0, name.length() - "_FAILED.png".length()) + ".png");
            candidates.add(name.replace("_FAILED.png", "_PASSED.png"));
        } else if (name.endsWith(".png")) {
            String base = name.substring(0, name.length() - 4);
            candidates.add(base + "_PASSED.png");
            candidates.add(base + "_FAILED.png");
        }
        for (String candidate : candidates) {
            Path alt = parent.resolve(candidate);
            if (Files.isRegularFile(alt)) {
                return alt;
            }
        }
        return null;
    }

    private void loadScreenshotPreview(String screenshotPath) {
        if (screenshotView == null) return;
        try {
            Path path = Path.of(screenshotPath);
            if (!Files.isRegularFile(path)) {
                screenshotView.setImage(null);
                screenshotView.setVisible(false);
                return;
            }
            Image image = new Image(path.toUri().toString(), true);
            screenshotView.setPreserveRatio(true);
            screenshotView.setSmooth(true);
            screenshotView.setImage(image);
            screenshotView.setVisible(true);
        } catch (Exception e) {
            screenshotView.setImage(null);
            screenshotView.setVisible(false);
        }
    }

    // --- Actions ---

    @FXML
    private void exportHtml() {
        if (currentReport == null) {
            ToastService.warn("⚠️ Chưa tải báo cáo để xuất.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("🌐 Xuất báo cáo HTML độc lập");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File HTML (*.html)", "*.html"));
        chooser.setInitialFileName("report_" + currentReport.getRunId() + ".html");
        File file = chooser.showSaveDialog(exportHtmlBtn != null && exportHtmlBtn.getScene() != null
                ? exportHtmlBtn.getScene().getWindow()
                : (exportBtn != null && exportBtn.getScene() != null ? exportBtn.getScene().getWindow() : null));
        if (file != null) {
            try {
                HtmlReportService.export(currentReport, file.toPath());
                ToastService.success("🌐 Đã xuất báo cáo HTML: " + file.getName());
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(file.toURI());
                    }
                } catch (Exception ignored) {}
            } catch (Exception e) {
                ToastService.error("❌ Xuất HTML thất bại: " + e.getMessage());
            }
        }
    }

    @FXML
    private void exportExcel() {
        if (currentReport == null) {
            ToastService.warn("⚠️ Chưa tải báo cáo để xuất.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("📤 Xuất báo cáo Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File Excel (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("report_" + currentReport.getRunId() + ".xlsx");
        File file = chooser.showSaveDialog(exportBtn.getScene().getWindow());
        if (file != null) {
            try {
                ExcelReportService.export(currentReport, file.toPath());
                ToastService.success("📤 Đã xuất Excel: " + file.getName());
            } catch (Exception e) {
                ToastService.error("❌ Xuất thất bại: " + e.getMessage());
            }
        }
    }

    @FXML
    private void goBack() {
        ViewNavigator.navigateTo(ViewNavigator.Page.REPORTS);
    }

    @FXML
    private void deleteCurrentReport() {
        if (currentReport == null || currentReport.getRunId() == null || currentReport.getRunId().isBlank()) {
            ToastService.warn("⚠️ Chưa tải báo cáo để xóa.");
            return;
        }
        String runId = currentReport.getRunId();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Xóa báo cáo");
        confirm.setHeaderText("🗑️ Xóa báo cáo " + runId + "?");
        confirm.setContentText(
                "Sẽ xóa report.json, ảnh chụp và dữ liệu harness liên quan. Không thể hoàn tác.");
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return;
        }
        try {
            reportStore.deleteReport(runId);
            ToastService.success("🗑️ Đã xóa báo cáo: " + runId);
            ViewNavigator.invalidate(ViewNavigator.Page.REPORTS);
            ViewNavigator.navigateTo(ViewNavigator.Page.REPORTS);
        } catch (Exception e) {
            ToastService.error("❌ Không xóa được báo cáo: " + e.getMessage());
        }
    }

    @FXML
    private void viewScreenshot() {
        CaseResult selected = casesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastService.warn("⚠️ Chọn một case trong bảng trước.");
            return;
        }
        Path resolved = resolveScreenshotFile(selected.getScreenshotPath());
        if (resolved == null) {
            ToastService.warn("⚠️ Không tìm thấy file ảnh chụp"
                    + (selected.getScreenshotPath() != null ? ": " + selected.getScreenshotPath() : ""));
            return;
        }
        EvidenceViewerDialog.show(selected.getTestCaseName(), resolved);
    }

    // --- Helpers ---

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "…";
    }
}
