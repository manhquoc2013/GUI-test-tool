package com.studio.controller;

import com.studio.service.DocumentParserService.ParsedDocument;
import com.studio.service.ToastService;
import com.studio.util.DialogTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
 * Modal dialog to view and inspect auto-converted Markdown AST documents.
 */
public class MarkdownPreviewDialog {

    public static void show(ParsedDocument doc) {
        if (doc == null || doc.getContent() == null) {
            ToastService.warn("⚠️ Không có nội dung Markdown để hiển thị.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Xem Trước Markdown Tối Ưu — " + doc.getFileName());
        dialog.setHeaderText("Nội dung Markdown AST đã chuẩn hóa cho AI Agent");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setText("Đóng");
        }

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.setPrefWidth(850);
        content.setPrefHeight(600);

        // Metadata Header Row
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Path mdPath = doc.getMarkdownPath();
        String pathStr = mdPath != null ? mdPath.toAbsolutePath().normalize().toString() : "Chưa lưu file";
        Label pathLabel = new Label("📁 " + pathStr);
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-text-muted;");
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        if (doc.getConversionResult() != null) {
            var res = doc.getConversionResult();
            Label statLabel = new Label("📊 " + res.getWordCount() + " từ | ~" + res.getEstimatedTokens() + " tokens");
            statLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -color-primary; -fx-background-color: -color-surface-hover; -fx-padding: 3 6; -fx-background-radius: 4;");
            topRow.getChildren().add(statLabel);
        }

        Button copyBtn = new Button("Sao chép Markdown");
        copyBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent cc = new ClipboardContent();
            cc.putString(doc.getContent());
            clipboard.setContent(cc);
            ToastService.info("Đã sao chép nội dung Markdown vào clipboard.");
        });

        Button openDesktopBtn = new Button("Mở file .md");
        openDesktopBtn.setDisable(mdPath == null || !Files.exists(mdPath));
        openDesktopBtn.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(mdPath.toFile());
                } else {
                    ToastService.warn("Hệ điều hành không hỗ trợ mở file trực tiếp.");
                }
            } catch (Exception ex) {
                ToastService.error("Không thể mở file: " + ex.getMessage());
            }
        });

        topRow.getChildren().addAll(pathLabel, copyBtn, openDesktopBtn);

        // Markdown Viewer TextArea
        TextArea markdownArea = new TextArea(doc.getContent());
        markdownArea.setEditable(false);
        markdownArea.setWrapText(true);
        markdownArea.setStyle("-fx-font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(markdownArea, Priority.ALWAYS);

        content.getChildren().addAll(topRow, markdownArea);
        pane.setContent(content);

        DialogTheme.apply(dialog);
        dialog.showAndWait();
    }
}
