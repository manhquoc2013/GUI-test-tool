package com.studio.controller;

import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.service.AiTestGeneratorService;
import com.studio.service.DocumentParserService;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.store.SettingsStore;
import com.studio.util.DialogTheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Modal dialog launched from Suite Workspace to prompt AI to generate or refine
 * Playwright scripts and Assertions for the active TestCase.
 */
public class AiCasePromptDialog {

    public static void show(TestCase activeCase, String currentScript, Path suiteDir,
                            ProjectStore projectStore, SettingsStore settingsStore,
                            BiConsumer<String, List<Assertion>> onApplied) {
        if (activeCase == null) {
            ToastService.warn("⚠️ Vui lòng chọn một TestCase trước khi mở AI Hỗ trợ.");
            return;
        }

        SettingsStore.Settings settings;
        try {
            settings = settingsStore.load();
        } catch (IOException e) {
            settings = new SettingsStore.Settings();
        }
        AiConfig aiConfig = settings.getAiConfig();

        if (!aiConfig.hasApiKey()) {
            ToastService.warn("⚠️ Chưa có API Key. Vui lòng mở Cài đặt OpenAI để điền API Key.");
            AiSettingsDialog.show(settingsStore);
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("✨ AI Hỗ trợ Case: " + activeCase.getName());
        dialog.setHeaderText("Sinh hoặc tối ưu hóa kịch bản Playwright & Assertions từ yêu cầu");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.CLOSE);
        Button closeButton = (Button) pane.lookupButton(ButtonType.CLOSE);
        closeButton.setText("Đóng");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(680);
        content.setPrefHeight(600);

        // Case Info header
        HBox infoBox = new HBox(16);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        infoBox.setStyle("-fx-background-color: -color-surface-hover; -fx-padding: 10px 14px; -fx-background-radius: 8px;");

        Label caseNameLbl = new Label("🎯 Case: " + activeCase.getName());
        caseNameLbl.setStyle("-fx-font-weight: bold;");

        Label caseUrlLbl = new Label("🔗 " + (activeCase.getStartUrl().isBlank() ? "(Chưa có URL)" : activeCase.getStartUrl()));
        caseUrlLbl.setStyle("-fx-text-fill: -color-subtext;");
        HBox.setHgrow(caseUrlLbl, Priority.ALWAYS);

        Label assertsCountLbl = new Label("☑️ " + activeCase.getAssertions().size() + " asserts");
        infoBox.getChildren().addAll(caseNameLbl, caseUrlLbl, assertsCountLbl);

        // Prompt input area
        Label promptTitle = new Label("Mô tả yêu cầu kịch bản hoặc hành động cần test:");
        promptTitle.getStyleClass().add("field-label");

        TextArea promptArea = new TextArea();
        promptArea.setPromptText("Ví dụ: Mở trang và điền form đăng ký với email hợp lệ, kiểm tra thông báo chào mừng và chuyển hướng đến trang dashboard...");
        promptArea.setPrefRowCount(3);
        promptArea.setWrapText(true);

        // Quick action chips
        HBox quickActions = new HBox(8);
        quickActions.setAlignment(Pos.CENTER_LEFT);

        Button chipFull = new Button("🚀 Sinh kịch bản đầy đủ");
        chipFull.getStyleClass().add("btn-secondary");
        chipFull.setOnAction(e -> promptArea.setText("Sinh toàn bộ các thao tác Playwright cần thiết để thực thi kịch bản này trên URL " + activeCase.getStartUrl() + ", kèm các Assertions kiểm tra kết quả mong đợi."));

        Button chipAsserts = new Button("☑️ Bổ sung Assertions");
        chipAsserts.getStyleClass().add("btn-secondary");
        chipAsserts.setOnAction(e -> promptArea.setText("Dựa trên kịch bản hiện tại, bổ sung các Assertions chặt chẽ (kiểm tra trạng thái hiển thị của các phần tử chính, text thông báo, URL chuyển hướng)."));

        Button chipEdge = new Button("🛡️ Thêm test biên / ngoại lệ");
        chipEdge.getStyleClass().add("btn-secondary");
        chipEdge.setOnAction(e -> promptArea.setText("Tạo kịch bản kiểm thử trường hợp dữ liệu sai hoặc để trống các trường bắt buộc, xác minh thông báo lỗi hiển thị đúng."));

        quickActions.getChildren().addAll(new Label("Gợi ý:"), chipFull, chipAsserts, chipEdge);

        // Attachment row
        List<DocumentParserService.ParsedDocument> attachedDocs = new ArrayList<>();
        FlowPane chipsContainer = new FlowPane(6, 6);

        Button attachBtn = new Button("📎 Đính kèm tài liệu / Ảnh UI");
        attachBtn.getStyleClass().add("btn-secondary");

        attachBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn tài liệu hoặc ảnh chụp giao diện");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Tất cả tài liệu hỗ trợ", "*.txt", "*.md", "*.json", "*.csv", "*.docx", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                    new FileChooser.ExtensionFilter("Văn bản & Specs", "*.txt", "*.md", "*.json", "*.csv", "*.docx", "*.pdf"),
                    new FileChooser.ExtensionFilter("Hình ảnh / Screenshots", "*.png", "*.jpg", "*.jpeg", "*.webp")
            );
            List<File> files = chooser.showOpenMultipleDialog(pane.getScene().getWindow());
            if (files != null) {
                DocumentParserService parser = new DocumentParserService();
                for (File f : files) {
                    try {
                        DocumentParserService.ParsedDocument doc = parser.parse(f.toPath());
                        attachedDocs.add(doc);
                        addChip(chipsContainer, doc, attachedDocs);
                    } catch (Exception ex) {
                        ToastService.error("Không thể đọc file: " + f.getName());
                    }
                }
            }
        });

        HBox attachRow = new HBox(10, attachBtn, chipsContainer);
        attachRow.setAlignment(Pos.CENTER_LEFT);

        // Action generate button & progress indicator
        Button generateBtn = new Button("✨ Tạo kịch bản với AI (" + aiConfig.getEffectiveModel() + ")");
        generateBtn.getStyleClass().add("toolbar-btn-run");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(22, 22);
        progress.setVisible(false);
        progress.setManaged(false);

        Label statusLbl = new Label("");
        statusLbl.setStyle("-fx-font-size: 12px;");

        HBox genActionRow = new HBox(12, generateBtn, progress, statusLbl);
        genActionRow.setAlignment(Pos.CENTER_LEFT);

        // Result Preview tabpane
        TabPane previewTabs = new TabPane();
        previewTabs.getStyleClass().add("workspace-tabs");
        VBox.setVgrow(previewTabs, Priority.ALWAYS);

        TextArea codePreview = new TextArea();
        codePreview.setPromptText("// Mã Playwright Java sinh ra sẽ hiển thị tại đây...");
        codePreview.setWrapText(false);
        codePreview.getStyleClass().add("code-editor");

        Tab codeTab = new Tab("📝 Kịch bản Playwright", codePreview);
        codeTab.setClosable(false);

        ListView<String> assertsPreview = new ListView<>();
        Tab assertTab = new Tab("☑️ Danh sách Assertions (0)", assertsPreview);
        assertTab.setClosable(false);

        previewTabs.getTabs().addAll(codeTab, assertTab);

        // Apply Button
        Button applyBtn = new Button("⚡ Áp dụng vào Case này");
        applyBtn.getStyleClass().add("toolbar-btn-save");
        applyBtn.setDisable(true);

        final AiGeneratedCase[] lastGenerated = new AiGeneratedCase[1];

        generateBtn.setOnAction(e -> {
            String prompt = promptArea.getText().trim();
            if (prompt.isEmpty() && attachedDocs.isEmpty()) {
                ToastService.warn("⚠️ Vui lòng nhập yêu cầu hoặc đính kèm tài liệu trước khi bấm Tạo.");
                return;
            }

            generateBtn.setDisable(true);
            progress.setVisible(true);
            progress.setManaged(true);
            statusLbl.setText("🤖 AI đang phân tích và viết code Playwright...");
            applyBtn.setDisable(true);

            Thread thread = new Thread(() -> {
                try {
                    AiTestGeneratorService generator = new AiTestGeneratorService();
                    AiTestGeneratorService.GenerationResult result = generator.generateOrUpdateSingleCase(
                            aiConfig, prompt, attachedDocs, activeCase, currentScript
                    );

                    Platform.runLater(() -> {
                        generateBtn.setDisable(false);
                        progress.setVisible(false);
                        progress.setManaged(false);

                        if (!result.getCases().isEmpty()) {
                            AiGeneratedCase generated = result.getCases().get(0);
                            generated.setTargetCaseId(activeCase.getId());
                            lastGenerated[0] = generated;

                            codePreview.setText(generated.getScriptCode());

                            assertsPreview.getItems().clear();
                            for (Assertion a : generated.getAssertions()) {
                                String loc = a.getLocator() != null && !a.getLocator().isBlank() ? " | locator: " + a.getLocator() : "";
                                String val = a.getValue() != null && !a.getValue().isBlank() ? " | value: " + a.getValue() : "";
                                assertsPreview.getItems().add(a.getType() + loc + val);
                            }
                            assertTab.setText("☑️ Danh sách Assertions (" + generated.getAssertions().size() + ")");

                            statusLbl.setText("✅ " + result.getSummary());
                            statusLbl.setStyle("-fx-text-fill: #a6e3a1;");
                            applyBtn.setDisable(false);
                            ToastService.success("✨ Đã sinh kịch bản thành công! Hãy xem trước và bấm Áp dụng.");
                        } else {
                            statusLbl.setText("⚠️ AI không thể tạo cấu trúc test case: " + result.getSummary());
                            statusLbl.setStyle("-fx-text-fill: #f9e2af;");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        generateBtn.setDisable(false);
                        progress.setVisible(false);
                        progress.setManaged(false);
                        statusLbl.setText("❌ Lỗi: " + ex.getMessage());
                        statusLbl.setStyle("-fx-text-fill: #f38ba8;");
                        ToastService.error("Lỗi AI: " + ex.getMessage());
                    });
                }
            }, "Ai-Generate-Case");
            thread.setDaemon(true);
            thread.start();
        });

        applyBtn.setOnAction(e -> {
            if (lastGenerated[0] != null) {
                try {
                    AiGeneratedCase gen = lastGenerated[0];
                    if (gen.getScriptCode() != null && !gen.getScriptCode().isBlank()) {
                        projectStore.saveCaseScript(suiteDir, activeCase.getId(), gen.getScriptCode());
                    }
                    if (gen.getAssertions() != null) {
                        projectStore.replaceAssertions(suiteDir, activeCase.getId(), gen.getAssertions());
                    }
                    if (gen.getStartUrl() != null && !gen.getStartUrl().isBlank() && activeCase.getStartUrl().isBlank()) {
                        projectStore.updateCaseMeta(suiteDir, activeCase.getId(), activeCase.getDescription(),
                                "Ready", gen.getStartUrl(), activeCase.getBrowser(), activeCase.getHeadless());
                    }
                    if (onApplied != null) {
                        onApplied.accept(gen.getScriptCode(), gen.getAssertions());
                    }
                    ToastService.success("⚡ Đã cập nhật kịch bản và Assertions vào case!");
                    dialog.close();
                } catch (Exception ex) {
                    ToastService.error("Lỗi khi lưu vào case: " + ex.getMessage());
                }
            }
        });

        HBox footer = new HBox(12, applyBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(infoBox, promptTitle, promptArea, quickActions, attachRow, genActionRow, previewTabs, footer);
        pane.setContent(content);
        DialogTheme.apply(dialog);

        dialog.showAndWait();
    }

    private static void addChip(FlowPane container, DocumentParserService.ParsedDocument doc,
                                List<DocumentParserService.ParsedDocument> docList) {
        HBox chip = new HBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle("-fx-background-color: -color-surface; -fx-padding: 3px 8px; -fx-background-radius: 12px; -fx-border-color: -color-surface-hover; -fx-border-radius: 12px;");

        Label label = new Label((doc.isImage() ? "🖼️ " : "📄 ") + doc.getFileName());
        label.setStyle("-fx-font-size: 11px;");

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0 2px; -fx-text-fill: -color-subtext; -fx-cursor: hand; -fx-font-size: 10px;");
        removeBtn.setOnAction(e -> {
            container.getChildren().remove(chip);
            docList.remove(doc);
        });

        chip.getChildren().addAll(label, removeBtn);
        container.getChildren().add(chip);
    }
}
