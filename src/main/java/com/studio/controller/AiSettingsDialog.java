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
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to configure OpenAI models, API keys, Base URLs, and test connections.
 */
public class AiSettingsDialog {

    private static final List<String> DEFAULT_OPENAI_PRESETS = List.of(
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "o1",
            "gpt-4-turbo"
    );

    /**
     * Provide presets tailored to the Base URL provider.
     */
    public static List<String> getPresetsForBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_OPENAI_PRESETS;
        }
        String lower = baseUrl.toLowerCase();
        if (lower.contains("openrouter.ai")) {
            return List.of(
                    "openai/gpt-4o",
                    "openai/gpt-4o-mini",
                    "anthropic/claude-3.5-sonnet",
                    "deepseek/deepseek-r1",
                    "deepseek/deepseek-chat",
                    "meta-llama/llama-3.3-70b-instruct",
                    "google/gemini-2.0-flash-exp:free"
            );
        } else if (lower.contains("deepseek.com")) {
            return List.of(
                    "deepseek-chat",
                    "deepseek-reasoner"
            );
        } else if (lower.contains("groq.com")) {
            return List.of(
                    "llama-3.3-70b-versatile",
                    "deepseek-r1-distill-llama-70b",
                    "llama-3.1-8b-instant",
                    "mixtral-8x7b-32768"
            );
        } else if (lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("ollama")) {
            return List.of(
                    "llama3.2",
                    "qwen2.5-coder",
                    "deepseek-r1",
                    "mistral",
                    "codellama"
            );
        } else if (lower.contains("googleapis.com") || lower.contains("gemini")) {
            return List.of(
                    "gemini-2.0-flash",
                    "gemini-1.5-flash",
                    "gemini-1.5-pro",
                    "gemini-3.8-flash"
            );
        }
        return DEFAULT_OPENAI_PRESETS;
    }

    public static void show(SettingsStore settingsStore) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Cài đặt OpenAI Assistant");
        dialog.setHeaderText("Cấu hình mô hình AI và kết nối OpenAI / Compatible Endpoint");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) pane.lookupButton(ButtonType.OK);
        okButton.setText("Lưu cài đặt");
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

        Button toggleShowBtn = new Button();
        toggleShowBtn.getStyleClass().add("icon-btn");
        toggleShowBtn.setTooltip(new Tooltip("Hiện / Ẩn API Key"));
        com.studio.util.UiIcons.setGraphic(toggleShowBtn, com.studio.util.UiIcons.EYE, 13);
        toggleShowBtn.setOnAction(e -> {
            boolean showing = plainKeyField.isVisible();
            plainKeyField.setVisible(!showing);
            plainKeyField.setManaged(!showing);
            keyField.setVisible(showing);
            keyField.setManaged(showing);
            com.studio.util.UiIcons.setGraphic(toggleShowBtn, showing ? com.studio.util.UiIcons.EYE : com.studio.util.UiIcons.EYE_OFF, 13);
        });

        HBox keyBox = new HBox(6, keyField, plainKeyField, toggleShowBtn);
        keyBox.setAlignment(Pos.CENTER_LEFT);

        Label envHint = new Label(aiConfig.getEffectiveApiKey().isEmpty()
                ? "Chưa phát hiện API Key (cả trong cài đặt và biến môi trường OPENAI_API_KEY)."
                : "Đang khả dụng: " + (aiConfig.getApiKey().isEmpty() ? "biến môi trường OPENAI_API_KEY" : "API Key trực tiếp"));
        envHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa5ce;");

        VBox keyCol = new VBox(4, keyBox, envHint);

        grid.add(keyLabel, 0, row);
        grid.add(keyCol, 1, row);
        row++;

        // Base URL
        Label urlLabel = new Label("Base URL:");
        urlLabel.getStyleClass().add("field-label");
        TextField urlField = new TextField(aiConfig.getBaseUrl());
        urlField.setPromptText("https://api.openai.com/v1");

        Label urlHint = new Label("Hỗ trợ OpenAI, Azure OpenAI, OpenRouter (https://openrouter.ai/api/v1), LocalAI, Ollama");
        urlHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa5ce;");
        VBox urlCol = new VBox(4, urlField, urlHint);

        grid.add(urlLabel, 0, row);
        grid.add(urlCol, 1, row);
        row++;

        // Model
        Label modelLabel = new Label("Model:");
        modelLabel.getStyleClass().add("field-label");
        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.setPromptText("Chọn hoặc nhập tên model...");
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modelCombo, Priority.ALWAYS);

        // Populate presets matching initial Base URL
        List<String> initialPresets = new ArrayList<>(getPresetsForBaseUrl(aiConfig.getBaseUrl()));
        if (aiConfig.getModel() != null && !aiConfig.getModel().isBlank() && !initialPresets.contains(aiConfig.getModel())) {
            initialPresets.add(0, aiConfig.getModel());
        }
        modelCombo.getItems().setAll(initialPresets);
        modelCombo.setValue(aiConfig.getModel());
        modelCombo.getEditor().setText(aiConfig.getModel());

        // Dynamic update presets when Base URL changes
        urlField.textProperty().addListener((obs, oldVal, newVal) -> {
            String currentText = modelCombo.getEditor().getText() != null && !modelCombo.getEditor().getText().isBlank()
                    ? modelCombo.getEditor().getText().trim()
                    : modelCombo.getValue();
            List<String> presets = getPresetsForBaseUrl(newVal);
            modelCombo.getItems().setAll(presets);
            if (currentText != null && !currentText.isBlank()) {
                if (!modelCombo.getItems().contains(currentText)) {
                    modelCombo.getItems().add(0, currentText);
                }
                modelCombo.setValue(currentText);
                modelCombo.getEditor().setText(currentText);
            } else if (!presets.isEmpty()) {
                modelCombo.setValue(presets.get(0));
                modelCombo.getEditor().setText(presets.get(0));
            }
        });

        Button fetchModelsBtn = new Button("Lấy từ URL");
        fetchModelsBtn.getStyleClass().add("btn-secondary");
        com.studio.util.UiIcons.setGraphic(fetchModelsBtn, com.studio.util.UiIcons.REFRESH, 12);
        fetchModelsBtn.setTooltip(new Tooltip("Gửi yêu cầu tới endpoint /models của Base URL để lấy danh sách model thực tế"));

        HBox modelBox = new HBox(6, modelCombo, fetchModelsBtn);
        modelBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(modelLabel, 0, row);
        grid.add(modelBox, 1, row);
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

        Button testConnBtn = new Button("Kiểm tra kết nối");
        testConnBtn.getStyleClass().add("btn-secondary");
        com.studio.util.UiIcons.setGraphic(testConnBtn, com.studio.util.UiIcons.CHECK, 12);

        Label testStatusLabel = new Label("");
        testStatusLabel.setWrapText(true);
        testStatusLabel.setStyle("-fx-font-size: 12px;");

        fetchModelsBtn.setOnAction(e -> {
            fetchModelsBtn.setDisable(true);
            testStatusLabel.setText("Đang tải danh sách model từ Base URL...");
            testStatusLabel.setStyle("-fx-text-fill: #9aa5ce;");

            AiConfig probeConfig = new AiConfig();
            probeConfig.setApiKey(keyField.getText().trim());
            probeConfig.setBaseUrl(urlField.getText().trim());

            Thread thread = new Thread(() -> {
                OpenAiClient client = new OpenAiClient();
                try {
                    List<String> models = client.fetchModels(probeConfig);
                    Platform.runLater(() -> {
                        fetchModelsBtn.setDisable(false);
                        if (!models.isEmpty()) {
                            String current = modelCombo.getEditor().getText();
                            modelCombo.getItems().setAll(models);
                            if (current != null && !current.isBlank()) {
                                modelCombo.setValue(current);
                                modelCombo.getEditor().setText(current);
                            } else {
                                modelCombo.setValue(models.get(0));
                                modelCombo.getEditor().setText(models.get(0));
                            }
                            testStatusLabel.setText("Đã tìm thấy " + models.size() + " models từ " + probeConfig.getEffectiveBaseUrl());
                            testStatusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
                        } else {
                            testStatusLabel.setText("Endpoint /models không trả về danh sách model.");
                            testStatusLabel.setStyle("-fx-text-fill: #f9e2af;");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        fetchModelsBtn.setDisable(false);
                        testStatusLabel.setText("Không thể lấy models: " + ex.getMessage());
                        testStatusLabel.setStyle("-fx-text-fill: #f38ba8;");
                    });
                }
            }, "OpenAi-FetchModels");
            thread.setDaemon(true);
            thread.start();
        });

        testConnBtn.setOnAction(e -> {
            testConnBtn.setDisable(true);
            testStatusLabel.setText("Đang kiểm tra kết nối...");
            testStatusLabel.setStyle("-fx-text-fill: #9aa5ce;");

            AiConfig probeConfig = new AiConfig();
            probeConfig.setApiKey(keyField.getText().trim());
            probeConfig.setBaseUrl(urlField.getText().trim());
            String probeModel = modelCombo.getEditor().getText() != null && !modelCombo.getEditor().getText().isBlank()
                    ? modelCombo.getEditor().getText().trim()
                    : modelCombo.getValue();
            probeConfig.setModel(probeModel);

            Thread thread = new Thread(() -> {
                OpenAiClient client = new OpenAiClient();
                OpenAiClient.ConnectionTestResult res = client.testConnection(probeConfig);
                Platform.runLater(() -> {
                    testConnBtn.setDisable(false);
                    if (res.isSuccess()) {
                        testStatusLabel.setText(res.getMessage());
                        testStatusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
                    } else {
                        testStatusLabel.setText(res.getMessage());
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
                String enteredModel = modelCombo.getEditor().getText() != null && !modelCombo.getEditor().getText().isBlank()
                        ? modelCombo.getEditor().getText().trim()
                        : (modelCombo.getValue() != null ? modelCombo.getValue().trim() : "gpt-4o");
                cfg.setModel(enteredModel);
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

