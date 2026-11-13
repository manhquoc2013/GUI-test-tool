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
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Read-only viewer for a long text that must not live inside the transcript.
 *
 * <p>A chat bubble keeps a bounded excerpt inline; rendering a multi-thousand-character answer in
 * the scrollable transcript re-measures every neighbour bubble on each layout pass. The full text
 * stays available here, in a modal the user explicitly opens.</p>
 */
public final class LongTextDialog {

    /** Above this, wrapping a single TextArea costs more than the readability it buys. */
    private static final int WRAP_LIMIT_CHARS = 60_000;
    private static final double PREF_WIDTH = 900;
    private static final double PREF_HEIGHT = 620;
    private static final double AREA_MIN_HEIGHT = 420;

    private LongTextDialog() {
    }

    /**
     * Shows {@code content} in a modal viewer.
     *
     * @param title   window title
     * @param header  one-line context (speaker, time, size)
     * @param content full text, never truncated
     */
    public static void show(String title, String header, String content) {
        if (content == null || content.isBlank()) {
            ToastService.info("Không có nội dung để hiển thị.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title == null ? "Nội dung đầy đủ" : title);
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setText("Đóng");
        }

        Label headerLabel = new Label(header == null ? "" : header);
        headerLabel.getStyleClass().add("section-summary");
        headerLabel.setWrapText(true);
        headerLabel.setMaxWidth(Double.MAX_VALUE);

        Label sizeLabel = new Label(String.format(Locale.ROOT, "%d ký tự", content.length()));
        sizeLabel.getStyleClass().add("field-label-dim");

        Button copyBtn = new Button("Sao chép");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setOnAction(e -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
            ToastService.success("Đã sao chép toàn bộ nội dung!");
        });

        HBox toolbar = new HBox(8, sizeLabel, new HBox(), copyBtn);
        HBox.setHgrow(toolbar.getChildren().get(1), Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setWrapText(content.length() <= WRAP_LIMIT_CHARS);
        area.getStyleClass().add("code-editor");
        area.setMinHeight(AREA_MIN_HEIGHT);
        VBox.setVgrow(area, Priority.ALWAYS);

        VBox body = new VBox(8, headerLabel, toolbar, area);
        body.setPadding(new Insets(12));
        body.setPrefWidth(PREF_WIDTH);
        body.setPrefHeight(PREF_HEIGHT);

        pane.setContent(body);
        DialogTheme.apply(dialog);
        dialog.showAndWait();
    }
}
