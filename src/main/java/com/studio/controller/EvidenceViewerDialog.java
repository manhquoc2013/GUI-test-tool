package com.studio.controller;

import com.studio.service.ToastService;
import com.studio.util.DialogTheme;
import com.studio.util.UiIcons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Modal dialog to view screenshot evidence in full high resolution, with zoom
 * and desktop open options.
 */
public class EvidenceViewerDialog {

    private static final double ZOOM_STEP = 100.0;
    private static final double ZOOM_MIN  = 100.0;
    private static final double ZOOM_MAX  = 2000.0;

    public static void show(String title, Path imagePath) {
        if (imagePath == null || !Files.exists(imagePath)) {
            ToastService.warn("⚠️ Không tìm thấy file ảnh chụp màn hình.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        String fileName = imagePath.getFileName() != null ? imagePath.getFileName().toString() : "";
        dialog.setTitle("Bằng chứng ảnh chụp — " + (title != null ? title : fileName));
        dialog.setHeaderText("Xem bằng chứng ảnh chụp màn hình thực thi (Evidence)");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        closeBtn.setText("Đóng");

        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setPrefWidth(900);
        content.setPrefHeight(680);

        // --- File name label ---
        Label fileNameLabel = new Label("📸 " + fileName);
        fileNameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #c0caf5;");

        // --- Path + action row ---
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label pathLabel = new Label(imagePath.toAbsolutePath().normalize().toString());
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa5ce;");
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        Button copyPathBtn = new Button("Chép đường dẫn");
        copyPathBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(copyPathBtn, UiIcons.COPY, 12);
        copyPathBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(imagePath.toAbsolutePath().normalize().toString());
            Clipboard.getSystemClipboard().setContent(cc);
            ToastService.info("Đã chép đường dẫn ảnh vào Clipboard!");
        });

        Button openDesktopBtn = new Button("Mở trên máy tính");
        openDesktopBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(openDesktopBtn, UiIcons.FOLDER, 12);
        openDesktopBtn.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(imagePath.toFile());
                }
            } catch (Exception ex) {
                ToastService.error("Không thể mở file: " + ex.getMessage());
            }
        });

        topRow.getChildren().addAll(pathLabel, copyPathBtn, openDesktopBtn);

        // --- Image View ---
        Image img = new Image(imagePath.toUri().toString(), false);
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);

        // initial fit width — will be adjusted by zoom
        double[] currentFitWidth = { Math.max(ZOOM_MIN, content.getPrefWidth() - 36) };
        iv.setFitWidth(currentFitWidth[0]);

        ScrollPane scroll = new ScrollPane(iv);
        scroll.setFitToWidth(false);
        scroll.setStyle("-fx-background-color: #111218; -fx-border-color: #2a2b3d; -fx-border-radius: 8px;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // --- Zoom controls ---
        Label zoomLabel = new Label(String.format("%.0f%%", currentFitWidth[0]));
        zoomLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa5ce; -fx-min-width: 50px;");
        zoomLabel.setAlignment(Pos.CENTER);

        Button zoomInBtn  = new Button("+");
        Button zoomOutBtn = new Button("−");
        Button zoomFitBtn = new Button("Fit");
        zoomInBtn.getStyleClass().add("flat");
        zoomOutBtn.getStyleClass().add("flat");
        zoomFitBtn.getStyleClass().addAll("flat", "small");

        Runnable applyZoom = () -> {
            iv.setFitWidth(currentFitWidth[0]);
            zoomLabel.setText(String.format("%.0f%%", currentFitWidth[0]));
        };

        zoomInBtn.setOnAction(e -> {
            currentFitWidth[0] = Math.min(ZOOM_MAX, currentFitWidth[0] + ZOOM_STEP);
            applyZoom.run();
        });
        zoomOutBtn.setOnAction(e -> {
            currentFitWidth[0] = Math.max(ZOOM_MIN, currentFitWidth[0] - ZOOM_STEP);
            applyZoom.run();
        });
        zoomFitBtn.setOnAction(e -> {
            currentFitWidth[0] = Math.max(ZOOM_MIN, content.getWidth() - 36);
            applyZoom.run();
        });

        HBox zoomRow = new HBox(6, zoomOutBtn, zoomLabel, zoomInBtn, zoomFitBtn);
        zoomRow.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(fileNameLabel, topRow, zoomRow, scroll);
        pane.setContent(content);
        DialogTheme.apply(dialog);

        dialog.showAndWait();
    }
}
