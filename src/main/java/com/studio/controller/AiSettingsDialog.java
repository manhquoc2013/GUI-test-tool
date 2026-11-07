package com.studio.controller;

import com.studio.model.AiConfig;
import com.studio.service.OpenAiClient;
import com.studio.service.ToastService;
import com.studio.store.SettingsStore;
import com.studio.util.DialogTheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;

/**
 * Dialog to configure OpenAI models, API keys, Base URLs, and test connections.
 */
public class AiSettingsDialog {

    private static final String[] MODEL_PRESETS = {
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "o1",
            "gpt-4-turbo"
    };

    public static void show(SettingsStore settingsStore) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("⚙️ Cài đặt OpenAI Assistant");
        dialog.setHeaderText("Cấu hình mô hình AI và kết nối OpenAI / Compatible Endpoint");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) pane.lookupButton(ButtonType.OK);
        okButton.setText("💾 Lưu cài đặt");
        Button cancelButton = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelButton.setText("Hủy");

        SettingsStore.Settings currentSettings;
        try {
            currentSettings = settingsStore.load();
        } catch (IOException e) {
            currentSettings = new SettingsStore.Settings();
        }
        AiConfig aiConfig = currentSettings.getAiConfig();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(16, 20, 16, 20));
        grid.setPrefWidth(540);

        int row = 0;

        // API Key
        Label keyLabel = new Label("API Key:");
        keyLabel.getStyleClass().add("field-label");
        PasswordField keyField = new PasswordField();
        keyField.setText(aiConfig.getApiKey());
        keyField.setPromptText("sk-proj-... hoặc để trống để dùng biến môi trường OPENAI_API_KEY");
        HBox.setHgrow(keyField, Priority.ALWAYS);

        TextField plainKeyField = new TextField();
        plainKeyField.setText(aiConfig.getApiKey());
        plainKeyField.setPromptText("sk-proj-...");
        plainKeyField.setVisible(false);
        plainKeyField.setManaged(false);
        HBox.setHgrow(plainKeyField, Priority.ALWAYS);

        keyField.textProperty().bindBidirectional(plainKeyField.textProperty());

        Button toggleShowBtn = new Button("👁");
        toggleShowBtn.getStyleClass().add("icon-btn");
        toggleShowBtn.setTooltip(new Tooltip("Hiện / Ẩn API Key"));
        toggleShowBtn.setOnAction(e -> {
            boolean showing = plainKeyField.isVisible();
            plainKeyField.setVisible(!showing);
            plainKeyField.setManaged(!showing);
            keyField.setVisible(showing);
            keyField.setManaged(showing);
            toggleShowBtn.setText(showing ? "👁" : "🔒");
        });

        HBox keyBox = new HBox(6, keyField, plainKeyField, toggleShowBtn);
        keyBox.setAlignment(Pos.CENTER_LEFT);

        Label envHint = new Label(aiConfig.getEffectiveApiKey().isEmpty()
                ? "⚠️ Chưa phát hiện API Key (cả trong cài đặt và biến môi trường OPENAI_API_KEY)."
                : "💡 Đang khả dụng: " + (aiConfig.getApiKey().isEmpty() ? "biến môi trường OPENAI_API_KEY" : "API Key trực tiếp"));
        envHint.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-subtext;");

        VBox keyCol = new VBox(4, keyBox, envHint);

        grid.add(keyLabel, 0, row);
        grid.add(keyCol, 1, row);
        row++;

        // Base URL
        Label urlLabel = new Label("Base URL:");
        urlLabel.getStyleClass().add("field-label");
        TextField urlField = new TextField(aiConfig.getBaseUrl());
        urlField.setPromptText("https://api.openai.com/v1");

        Label urlHint = new Label("💡 Hỗ trợ OpenAI, Azure OpenAI, OpenRouter (https://openrouter.ai/api/v1), LocalAI, Ollama");
        urlHint.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-subtext;");
        VBox urlCol = new VBox(4, urlField, urlHint);

        grid.add(urlLabel, 0, row);
        grid.add(urlCol, 1, row);
        row++;

        // Model
        Label modelLabel = new Label("Model:");
        modelLabel.getStyleClass().add("field-label");
        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.getItems().addAll(MODEL_PRESETS);
        modelCombo.setValue(aiConfig.getModel());
        modelCombo.setMaxWidth(Double.MAX_VALUE);

        grid.add(modelLabel, 0, row);
        grid.add(modelCombo, 1, row);
        row++;

        // Temperature
        Label tempLabel = new Label("Nhiệt độ (0.0 - 1.0):");
        tempLabel.getStyleClass().add("field-label");
        Slider tempSlider = new Slider(0.0, 1.0, aiConfig.getTemperature());
        tempSlider.setShowTickLabels(true);
        tempSlider.setShowTickMarks(true);
        tempSlider.setMajorTickUnit(0.5);
        tempSlider.setBlockIncrement(0.1);
        Label tempValLabel = new Label(String.format("%.2f", aiConfig.getTemperature()));
        tempValLabel.setMinWidth(40);
        tempSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            tempValLabel.setText(String.format("%.2f", newVal.doubleValue()));
        });
        HBox tempBox = new HBox(8, tempSlider, tempValLabel);
        tempBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tempSlider, Priority.ALWAYS);

        grid.add(tempLabel, 0, row);
        grid.add(tempBox, 1, row);
        row++;

        // Test connection
        Label testLabel = new Label("Kiểm tra:");
        testLabel.getStyleClass().add("field-label");

        Button testConnBtn = new Button("🔌 Kiểm tra kết nối");
        testConnBtn.getStyleClass().add("btn-secondary");

        Label testStatusLabel = new Label("");
        testStatusLabel.setWrapText(true);
        testStatusLabel.setStyle("-fx-font-size: 12px;");

        testConnBtn.setOnAction(e -> {
            testConnBtn.setDisable(true);
            testStatusLabel.setText("⏳ Đang kiểm tra kết nối...");
            testStatusLabel.setStyle("-fx-text-fill: -color-subtext;");

            AiConfig probeConfig = new AiConfig();
            probeConfig.setApiKey(keyField.getText());
            probeConfig.setBaseUrl(urlField.getText());
            probeConfig.setModel(modelCombo.getValue());

            Thread thread = new Thread(() -> {
                OpenAiClient client = new OpenAiClient();
                OpenAiClient.ConnectionTestResult res = client.testConnection(probeConfig);
                Platform.runLater(() -> {
                    testConnBtn.setDisable(false);
                    if (res.isSuccess()) {
                        testStatusLabel.setText("✅ " + res.getMessage());
                        testStatusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
                    } else {
                        testStatusLabel.setText("❌ " + res.getMessage());
                        testStatusLabel.setStyle("-fx-text-fill: #f38ba8;");
                    }
                });
            }, "OpenAi-TestConnection");
            thread.setDaemon(true);
            thread.start();
        });

        VBox testBox = new VBox(6, testConnBtn, testStatusLabel);
        grid.add(testLabel, 0, row);
        grid.add(testBox, 1, row);

        pane.setContent(grid);
        DialogTheme.apply(dialog);

        final SettingsStore.Settings finalSettings = currentSettings;
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                AiConfig cfg = finalSettings.getAiConfig();
                cfg.setApiKey(keyField.getText().trim());
                cfg.setBaseUrl(urlField.getText().trim());
                cfg.setModel(modelCombo.getValue() != null ? modelCombo.getValue().trim() : "gpt-4o");
                cfg.setTemperature(tempSlider.getValue());
                try {
                    settingsStore.save(finalSettings);
                    ToastService.success("💾 Đã lưu cấu hình OpenAI Assistant!");
                    return true;
                } catch (IOException ex) {
                    ToastService.error("⚠️ Không thể lưu cấu hình: " + ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait();
    }
}

