package com.studio.controller;

import com.studio.service.ToastService;
import com.studio.util.DialogTheme;
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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Modal dialog to view screenshot evidence in full high resolution, with zoom and desktop open options.
 */
public class EvidenceViewerDialog {

    public static void show(String title, Path imagePath) {
        if (imagePath == null || !Files.exists(imagePath)) {
            ToastService.warn("⚠️ Không tìm thấy file ảnh chụp màn hình.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("📸 Bằng chứng ảnh chụp — " + (title != null ? title : imagePath.getFileName()));
        dialog.setHeaderText("Xem bằng chứng ảnh chụp màn hình thực thi (Evidence)");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        closeBtn.setText("Đóng");

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.setPrefWidth(850);
        content.setPrefHeight(650);

        // Path header row
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label pathLabel = new Label("📁 " + imagePath.toAbsolutePath().normalize());
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-subtext;");
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        Button copyPathBtn = new Button("📋 Chép đường dẫn");
        copyPathBtn.getStyleClass().add("btn-secondary");
        copyPathBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(imagePath.toAbsolutePath().normalize().toString());
            Clipboard.getSystemClipboard().setContent(cc);
            ToastService.info("📋 Đã chép đường dẫn ảnh vào Clipboard!");
        });

        Button openDesktopBtn = new Button("📂 Mở trên máy tính");
        openDesktopBtn.getStyleClass().add("btn-secondary");
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

        // Image View inside ScrollPane
        Image img = new Image(imagePath.toUri().toString(), false);
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.fitWidthProperty().bind(content.widthProperty().subtract(36));

        ScrollPane scroll = new ScrollPane(iv);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #111218; -fx-border-color: #2a2b3d; -fx-border-radius: 8px;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        content.getChildren().addAll(topRow, scroll);
        pane.setContent(content);
        DialogTheme.apply(dialog);

        dialog.showAndWait();
    }
}

