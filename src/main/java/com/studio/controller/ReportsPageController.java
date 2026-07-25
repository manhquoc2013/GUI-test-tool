package com.studio.controller;

import com.studio.model.RunReport;
import com.studio.service.ExcelReportService;
import com.studio.store.ReportStore;
import com.studio.service.ToastService;
import com.studio.util.DialogTheme;
import com.studio.util.ResponsiveTableColumns;
import com.studio.util.StatusDisplay;
import com.studio.util.StudioPaths;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import javafx.util.StringConverter;

/**
 * Controller for the Reports list page (reports_page.fxml).
 * Loads the report list, supports filtering by scope/result/suite,
 * and navigates to report detail via ViewNavigator.
 */
public class ReportsPageController {

    @FXML private TableView<RunReport> reportsTable;
    @FXML private TableColumn<RunReport, String> runIdCol;
    @FXML private TableColumn<RunReport, String> scopeCol;
    @FXML private TableColumn<RunReport, String> targetCol;
    @FXML private TableColumn<RunReport, String> resultCol;
    @FXML private TableColumn<RunReport, String> durationCol;
    @FXML private TableColumn<RunReport, String> browserCol;
    @FXML private TableColumn<RunReport, String> startedCol;
    @FXML private TableColumn<RunReport, String> actionsCol;

    @FXML private ComboBox<String> scopeFilter;
    @FXML private ComboBox<String> resultFilter;
    @FXML private ComboBox<String> suiteFilter;

    private final ReportStore reportStore = new ReportStore();

    private ObservableList<RunReport> allReports = FXCollections.observableArrayList();
    private ObservableList<RunReport> filteredReports = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        setupFilters();
        refresh();
    }

    private void setupColumns() {
        ResponsiveTableColumns.install(reportsTable,
                ResponsiveTableColumns.Spec.flex(runIdCol, 1.2, 110),
                ResponsiveTableColumns.Spec.fixed(scopeCol, 96),
                ResponsiveTableColumns.Spec.flex(targetCol, 1.4, 100),
                ResponsiveTableColumns.Spec.flex(resultCol, 1.3, 120),
                ResponsiveTableColumns.Spec.fixed(durationCol, 72),
                ResponsiveTableColumns.Spec.fixed(browserCol, 100),
                ResponsiveTableColumns.Spec.flex(startedCol, 1.5, 130),
                ResponsiveTableColumns.Spec.fixed(actionsCol, 230));

        // Keep list order (newest first) — disable user column sort reordering
        reportsTable.setSortPolicy(table -> true);
        for (TableColumn<?, ?> col : reportsTable.getColumns()) {
            col.setSortable(false);
        }

        runIdCol.setCellValueFactory(new PropertyValueFactory<>("runId"));

        scopeCol.setCellValueFactory(cell -> {
            String scope = cell.getValue().getScope();
            String labeled = switch (scope != null ? scope : "") {
                case "ALL", "TẤT CẢ" -> "🌐 " + scope;
                case "GROUP", "NHÓM" -> "📁 " + scope;
                case "SUITE" -> "📋 " + scope;
                case "CASE" -> "🎯 " + scope;
                default -> scope != null ? "📐 " + scope : "";
            };
            return new javafx.beans.property.SimpleStringProperty(labeled);
        });

        targetCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().getScopeName()));

        resultCol.setCellValueFactory(cell -> {
            RunReport r = cell.getValue();
            String s = String.format("✅ %d  ❌ %d  ⏭️ %d",
                    r.getPassed(), r.getFailed(), r.getSkipped());
            return new javafx.beans.property.SimpleStringProperty(s);
        });
        resultCol.setCellFactory(col -> new TableCell<RunReport, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-passed", "status-failed", "status-skipped");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    RunReport r = getTableRow() != null ? getTableRow().getItem() : null;
                    if (r != null && r.getFailed() > 0) {
                        getStyleClass().add("status-failed");
                    } else if (r != null && r.getPassed() > 0) {
                        getStyleClass().add("status-passed");
                    } else if (r != null && r.getSkipped() > 0) {
                        getStyleClass().add("status-skipped");
                    }
                }
            }
        });
        durationCol.setCellValueFactory(cell -> {
            RunReport r = cell.getValue();
            return new javafx.beans.property.SimpleStringProperty(formatDuration(r.getStartedAt(), r.getFinishedAt()));
        });

        browserCol.setCellValueFactory(new PropertyValueFactory<>("browser"));

        startedCol.setCellValueFactory(cell -> {
            String val = cell.getValue().getStartedAt();
            if (val == null) return new javafx.beans.property.SimpleStringProperty("");
            return new javafx.beans.property.SimpleStringProperty(truncateIso(val));
        });

        // Actions: Open + Excel + Delete
        actionsCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(""));
        actionsCol.setCellFactory(col -> new TableCell<RunReport, String>() {
            private final Button openBtn = new Button("Mở");
            private final Button excelBtn = new Button("Excel");
            private final Button deleteBtn = new Button("Xóa");
            private final HBox row = new HBox(6, openBtn, excelBtn, deleteBtn);
            {
                openBtn.getStyleClass().add("toolbar-btn");
                excelBtn.getStyleClass().add("toolbar-btn");
                deleteBtn.getStyleClass().add("toolbar-btn-danger");
                openBtn.setTooltip(new Tooltip("Mở báo cáo"));
                excelBtn.setTooltip(new Tooltip("Xuất Excel"));
                deleteBtn.setTooltip(new Tooltip("Xóa báo cáo và ảnh chụp"));
                openBtn.setMinWidth(52);
                excelBtn.setMinWidth(56);
                deleteBtn.setMinWidth(52);
                row.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                RunReport report = getTableRow().getItem();
                openBtn.setOnAction(e -> openDetail(report));
                excelBtn.setOnAction(e -> exportExcel(report));
                deleteBtn.setOnAction(e -> confirmDelete(report));
                setGraphic(row);
            }
        });
    }

    private void setupFilters() {
        // Scope filter — ALL_LABEL = no filter; "ALL" = reports whose run scope is ALL
        scopeFilter.setItems(FXCollections.observableArrayList(
                StatusDisplay.ALL_LABEL,
                "ALL",
                "GROUP",
                "SUITE",
                "CASE"
        ));
        scopeFilter.setValue(StatusDisplay.ALL_LABEL);
        scopeFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String s) {
                if (s == null || StatusDisplay.isAll(s)) {
                    return "📋 Mọi phạm vi";
                }
                return switch (s) {
                    case "ALL" -> "🌐 Toàn dự án";
                    case "GROUP" -> "📁 Nhóm";
                    case "SUITE" -> "📋 Suite";
                    case "CASE" -> "🎯 Case";
                    default -> s;
                };
            }
            @Override
            public String fromString(String s) {
                if (s == null || s.isBlank()) return StatusDisplay.ALL_LABEL;
                if (s.contains("Mọi phạm vi") || StatusDisplay.isAll(s)) return StatusDisplay.ALL_LABEL;
                if (s.contains("Toàn dự án") || "ALL".equals(s)) return "ALL";
                if (s.contains("Nhóm") || "GROUP".equals(s)) return "GROUP";
                if (s.toLowerCase().contains("suite") || "SUITE".equals(s)) return "SUITE";
                if (s.toLowerCase().contains("case") || "CASE".equals(s)) return "CASE";
                return s;
            }
        });
        scopeFilter.setOnAction(e -> applyFilters());

        // Result filter
        resultFilter.setItems(FXCollections.observableArrayList(
                StatusDisplay.ALL_LABEL,
                StatusDisplay.label("PASSED"),
                StatusDisplay.label("FAILED"),
                StatusDisplay.label("SKIPPED")
        ));
        resultFilter.setValue(StatusDisplay.ALL_LABEL);
        resultFilter.setOnAction(e -> applyFilters());

        // Suite filter (populated from data)
        suiteFilter.setItems(FXCollections.observableArrayList(StatusDisplay.ALL_LABEL));
        suiteFilter.setValue(StatusDisplay.ALL_LABEL);
        suiteFilter.setConverter(new StringConverter<>() {
            @Override public String toString(String s) { return s; }
            @Override public String fromString(String s) { return s; }
        });
        suiteFilter.setOnAction(e -> applyFilters());
    }

    @FXML
    public void refresh() {
        reload();
    }

    /** Reload when navigating to the page. */
    public void reloadQuiet() {
        reload();
    }

    private void reload() {
        allReports.clear();
        try {
            List<RunReport> reports = reportStore.listReports();
            if (reports != null) {
                allReports.addAll(reports);
            }
        } catch (Exception e) {
            ToastService.error("❌ Không thể tải báo cáo: " + e.getMessage());
        }
        allReports.sort(newestFirst());
        updateSuiteFilter();
        applyFilters();
    }

    /** Newest run first: prefer runId (yyyyMMdd-HHmmss), then startedAt. */
    private static Comparator<RunReport> newestFirst() {
        return Comparator
                .comparing((RunReport r) -> safe(r.getRunId()), Comparator.reverseOrder())
                .thenComparing(r -> safe(r.getStartedAt()), Comparator.reverseOrder());
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    @FXML
    public void onFilterChanged() {
        applyFilters();
    }

    /** Rebuild suite filter values from the loaded reports (unique scope names). */
    private void updateSuiteFilter() {
        String previous = suiteFilter.getValue();
        java.util.LinkedHashSet<String> uniqueNames = new java.util.LinkedHashSet<>();
        for (RunReport r : allReports) {
            String name = r.getScopeName();
            if (name != null && !name.isBlank()) {
                uniqueNames.add(name.trim());
            }
        }
        ObservableList<String> suiteNames = FXCollections.observableArrayList();
        suiteNames.add(StatusDisplay.ALL_LABEL);
        for (String name : uniqueNames) {
            suiteNames.add("📁 " + name);
        }
        suiteFilter.setItems(suiteNames);
        if (previous != null && suiteNames.contains(previous)) {
            suiteFilter.setValue(previous);
        } else {
            suiteFilter.setValue(StatusDisplay.ALL_LABEL);
        }
    }

    private void applyFilters() {
        String scopeVal = scopeFilter.getValue();
        String resultVal = resultFilter.getValue();
        String suiteVal = suiteFilter.getValue();

        filteredReports.clear();
        for (RunReport r : allReports) {
            // Scope filter — combo holds ALL/GROUP/SUITE/CASE (or ALL_LABEL)
            if (!StatusDisplay.isAll(scopeVal)) {
                if (!scopeVal.equals(r.getScope())) continue;
            }
            // Result filter
            if (!StatusDisplay.isAll(resultVal)) {
                if (resultVal.contains("Đạt") || resultVal.startsWith("✅")) {
                    if (r.getFailed() > 0 || r.getPassed() == 0) continue;
                } else if (resultVal.contains("Thất bại") || resultVal.startsWith("❌")) {
                    if (r.getFailed() == 0) continue;
                } else if (resultVal.contains("Bỏ qua") || resultVal.startsWith("⏭️")) {
                    if (r.getSkipped() == 0) continue;
                }
            }
            // Suite filter
            if (!StatusDisplay.isAll(suiteVal)) {
                String want = suiteVal.replace("📁 ", "").trim();
                String reportName = r.getScopeName();
                if (!want.equals(reportName)) continue;
            }
            filteredReports.add(r);
        }
        reportsTable.setItems(filteredReports);
    }

    /** Open the report detail page for the given run. */
    public void openDetail(RunReport report) {
        openDetail(report.getRunId());
    }

    public void openDetail(String runId) {
        ReportDetailPageController ctrl = ViewNavigator.navigateToReportDetail(runId);
        if (ctrl == null) {
            ToastService.error("❌ Không mở được báo cáo: " + runId);
        }
    }

    private void exportExcel(RunReport listItem) {
        if (listItem == null || listItem.getRunId() == null || listItem.getRunId().isBlank()) {
            ToastService.warn("⚠️ Không có báo cáo để xuất.");
            return;
        }
        String runId = listItem.getRunId();
        RunReport full;
        try {
            // List view strips cases — reload full report for export
            full = reportStore.loadReport(runId);
        } catch (Exception e) {
            ToastService.error("❌ Không tải được báo cáo: " + e.getMessage());
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("📤 Xuất báo cáo Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File Excel (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("report_" + runId + ".xlsx");
        try {
            var exports = StudioPaths.getExportsDir();
            Files.createDirectories(exports);
            chooser.setInitialDirectory(exports.toFile());
        } catch (Exception ignored) {
        }
        File file = chooser.showSaveDialog(reportsTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            ExcelReportService.export(full, file.toPath());
            ToastService.success("📤 Đã xuất Excel: " + file.getName());
        } catch (Exception e) {
            ToastService.error("❌ Xuất thất bại: " + e.getMessage());
        }
    }

    private void confirmDelete(RunReport report) {
        if (report == null || report.getRunId() == null || report.getRunId().isBlank()) {
            return;
        }
        String runId = report.getRunId();
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
            allReports.removeIf(r -> runId.equals(r.getRunId()));
            filteredReports.removeIf(r -> runId.equals(r.getRunId()));
            updateSuiteFilter();
            ToastService.success("🗑️ Đã xóa báo cáo: " + runId);
        } catch (Exception e) {
            ToastService.error("❌ Không xóa được báo cáo: " + e.getMessage());
            reload();
        }
    }

    // --- Helpers ---

    private static String formatDuration(String startedAt, String finishedAt) {
        try {
            String s = startedAt != null ? startedAt : "";
            String f = finishedAt != null ? finishedAt : "";
            if (s.isEmpty() || f.isEmpty()) return "—";
            // Parse up to seconds (ISO-8601 may have timezone suffix)
            String parseS = s.length() >= 19 ? s.substring(0, 19) : s;
            String parseF = f.length() >= 19 ? f.substring(0, 19) : f;
            LocalDateTime start = LocalDateTime.parse(parseS);
            LocalDateTime end = LocalDateTime.parse(parseF);
            long secs = Duration.between(start, end).getSeconds();
            if (secs < 0) return "—";
            if (secs < 60) return secs + "s";
            return (secs / 60) + "m " + (secs % 60) + "s";
        } catch (Exception e) {
            return "—";
        }
    }

    private static String truncateIso(String iso) {
        if (iso == null) return "";
        // Display as yyyy-MM-dd HH:mm:ss (no T / timezone) so the column fits cleanly
        int t = iso.indexOf('T');
        if (t < 0) return iso;
        int end = Math.min(t + 19, iso.length());
        String core = iso.substring(0, end);
        return core.replace('T', ' ');
    }
}
