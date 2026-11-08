package com.studio.controller;

import com.studio.service.DataCleanupService;
import com.studio.service.ToastService;
import com.studio.store.SettingsStore;
import com.studio.util.DialogTheme;
import com.studio.util.UiIcons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;

/**
 * Modal dialog for performing system maintenance and data cleanup
 * while ensuring absolute persistence and safety for system configurations.
 */
public class DataCleanupDialog {

    public static void show() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Dọn dẹp hệ thống & Quản lý dữ liệu");
        dialog.setHeaderText("Dọn dẹp báo cáo, lịch sử chat và file tạm để tối ưu dung lượng");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.CLOSE);
        Button closeButton = (Button) pane.lookupButton(ButtonType.CLOSE);
        closeButton.setText("Đóng");
        closeButton.setMinWidth(80);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setPrefWidth(580);

        DataCleanupService cleanupService = new DataCleanupService();
        SettingsStore settingsStore = new SettingsStore();
        DataCleanupService.DiskUsageStats initialStats = cleanupService.calculateDiskUsage();

        // ── 1. Settings Safety & Backup Banner ──
        VBox safetyBox = new VBox(8);
        safetyBox.setStyle(
                "-fx-background-color: -color-surface; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: #10b981; -fx-border-radius: 8; -fx-border-width: 1;");

        HBox safetyHeader = new HBox(8);
        safetyHeader.setAlignment(Pos.CENTER_LEFT);
        Label safetyTitle = new Label("Cấu hình hệ thống được bảo toàn tuyệt đối");
        safetyTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #10b981; -fx-font-size: 13px;");
        safetyHeader.getChildren().add(safetyTitle);

        Label safetyDesc = new Label(
                "Mọi cấu hình (Theme, Trình duyệt mặc định, OpenAI API Key, Model, Timeout...) được lưu kép trên file và database nhúng H2, không bao giờ bị xóa khi dọn dẹp.");
        safetyDesc.setWrapText(true);
        safetyDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-subtext;");

        HBox backupButtonsRow = new HBox(10);
        backupButtonsRow.setAlignment(Pos.CENTER_LEFT);

        Button exportBtn = new Button("Sao lưu cấu hình (Export)");
        exportBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(exportBtn, UiIcons.SAVE, 11);
        exportBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Lưu file sao lưu cấu hình");
            chooser.setInitialFileName("playwright_studio_settings_backup.json");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            File file = chooser.showSaveDialog(pane.getScene().getWindow());
            if (file != null) {
                try {
                    settingsStore.exportSettings(file.toPath());
                    ToastService.success("Đã sao lưu cấu hình thành công: " + file.getName());
                } catch (Exception ex) {
                    ToastService.error("Lỗi khi sao lưu: " + ex.getMessage());
                }
            }
        });

        Button importBtn = new Button("Khôi phục cấu hình (Import)");
        importBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(importBtn, UiIcons.FOLDER, 11);
        importBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn file cấu hình để khôi phục");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            File file = chooser.showOpenDialog(pane.getScene().getWindow());
            if (file != null) {
                try {
                    settingsStore.importSettings(file.toPath());
                    ToastService.success("Đã khôi phục cấu hình thành công!");
                } catch (Exception ex) {
                    ToastService.error("Lỗi khi khôi phục: " + ex.getMessage());
                }
            }
        });

        backupButtonsRow.getChildren().addAll(exportBtn, importBtn);
        safetyBox.getChildren().addAll(safetyHeader, safetyDesc, backupButtonsRow);

        // ── 2. Maintenance Targets Section ──
        VBox targetsBox = new VBox(10);
        targetsBox.setStyle(
                "-fx-background-color: -color-surface; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: -color-border; -fx-border-radius: 8;");

        Label targetsTitle = new Label("Chọn dữ liệu cần dọn dẹp:");
        targetsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // Target A: Reports
        CheckBox cleanReportsCb = new CheckBox("Lịch sử chạy & Báo cáo kiểm thử (" + initialStats.reportsCount
                + " báo cáo, " + initialStats.formatBytes(initialStats.reportsBytes) + ")");
        cleanReportsCb.setSelected(true);

        HBox retentionBox = new HBox(8);
        retentionBox.setAlignment(Pos.CENTER_LEFT);
        retentionBox.setPadding(new Insets(0, 0, 0, 24));
        Label retentionLabel = new Label("Tùy chọn giữ lại:");
        retentionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-subtext;");

        ComboBox<String> retentionCombo = new ComboBox<>();
        retentionCombo.getItems().addAll(
                "Xóa toàn bộ báo cáo cũ",
                "Giữ lại 5 báo cáo mới nhất",
                "Giữ lại 10 báo cáo mới nhất");
        retentionCombo.getSelectionModel().select(1); // Default keep 5
        retentionCombo.disableProperty().bind(cleanReportsCb.selectedProperty().not());
        retentionBox.getChildren().addAll(retentionLabel, retentionCombo);

        // Target B: AI Chat
        CheckBox cleanChatCb = new CheckBox("Lịch sử hội thoại Trợ lý AI (" + initialStats.chatFilesCount + " phiên, "
                + initialStats.formatBytes(initialStats.chatBytes) + ")");
        cleanChatCb.setSelected(true);

        // Target C: Work Dir
        CheckBox cleanWorkCb = new CheckBox("Thư mục làm việc tạm thời work/ (" + initialStats.workFilesCount
                + " files, " + initialStats.formatBytes(initialStats.workBytes) + ")");
        cleanWorkCb.setSelected(true);

        targetsBox.getChildren().addAll(targetsTitle, cleanReportsCb, retentionBox, cleanChatCb, cleanWorkCb);

        // ── 3. Actions Row ──
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        Button executeBtn = new Button("Bắt đầu dọn dẹp");
        executeBtn.getStyleClass().add("toolbar-btn-record");
        UiIcons.setGraphic(executeBtn, UiIcons.TRASH, 12);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);
        progress.setManaged(false);

        executeBtn.setOnAction(e -> {
            if (com.studio.service.RunCancelService.isRunActive()) {
                ToastService.error(
                        "Không thể dọn dẹp dữ liệu khi kiểm thử đang chạy. Vui lòng dừng hoặc chờ phiên chạy hoàn tất.");
                return;
            }
            if (!cleanReportsCb.isSelected() && !cleanChatCb.isSelected() && !cleanWorkCb.isSelected()) {
                ToastService.warn("Vui lòng chọn ít nhất 1 mục để dọn dẹp.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Xác nhận dọn dẹp hệ thống");
            confirm.setHeaderText("Xác nhận dọn dẹp dữ liệu đã chọn");
            confirm.setContentText(
                    "Các báo cáo, lịch sử chat hoặc file tạm đã chọn sẽ được giải phóng. Bạn có chắc chắn muốn tiếp tục?");
            DialogTheme.apply(confirm);

            Optional<ButtonType> confirmResult = confirm.showAndWait();
            if (confirmResult.isEmpty() || confirmResult.get() != ButtonType.OK) {
                return;
            }

            int keep = 0;
            if (retentionCombo.getSelectionModel().getSelectedIndex() == 1)
                keep = 5;
            else if (retentionCombo.getSelectionModel().getSelectedIndex() == 2)
                keep = 10;

            DataCleanupService.CleanupOptions options = new DataCleanupService.CleanupOptions(
                    cleanReportsCb.isSelected(),
                    keep,
                    cleanChatCb.isSelected(),
                    cleanWorkCb.isSelected());

            executeBtn.setDisable(true);
            progress.setVisible(true);
            progress.setManaged(true);

            Thread thread = new Thread(() -> {
                DataCleanupService.CleanupResult result = cleanupService.executeCleanup(options);
                Platform.runLater(() -> {
                    executeBtn.setDisable(false);
                    progress.setVisible(false);
                    progress.setManaged(false);

                    String msg = "✨ Đã dọn dẹp xong! Giải phóng: " + result.formatFreedSpace()
                            + " (Đã xóa " + result.getDeletedReportsCount() + " báo cáo, "
                            + result.getDeletedChatCount() + " phiên chat, "
                            + result.getDeletedWorkFilesCount()
                            + " file tạm). Cấu hình hệ thống được giữ an toàn tuyệt đối.";
                    ToastService.success(msg);
                    dialog.close();
                });
            }, "DataCleanup-Worker");
            thread.setDaemon(true);
            thread.start();
        });

        actionsRow.getChildren().addAll(progress, executeBtn);

        content.getChildren().addAll(safetyBox, targetsBox, actionsRow);
        pane.setContent(content);
        DialogTheme.apply(dialog);

        dialog.showAndWait();
    }
}
