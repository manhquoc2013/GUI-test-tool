package com.studio.controller;

import com.studio.model.AiConfig;
import com.studio.service.OpenAiClient;
import com.studio.service.ThemeService;
import com.studio.service.ToastService;
import com.studio.store.SettingsStore;
import com.studio.util.DialogTheme;
import com.studio.util.UiIcons;
import com.studio.util.UiTooltips;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified Studio & AI Settings Dialog with tabbed configuration:
 * Tab 1: General & Browser Execution Settings
 * Tab 2: AI Assistant & LLM Endpoint Configuration
 */
public class AiSettingsDialog {

    private static final List<String> DEFAULT_OPENAI_PRESETS = List.of(
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "o1",
            "gpt-4-turbo");

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
                    "google/gemini-2.0-flash-exp:free");
        } else if (lower.contains("deepseek.com")) {
            return List.of(
                    "deepseek-chat",
                    "deepseek-reasoner");
        } else if (lower.contains("groq.com")) {
            return List.of(
                    "llama-3.3-70b-versatile",
                    "deepseek-r1-distill-llama-70b",
                    "llama-3.1-8b-instant",
                    "mixtral-8x7b-32768");
        } else if (lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("ollama")) {
            return List.of(
                    "llama3.2",
                    "qwen2.5-coder",
                    "deepseek-r1",
                    "mistral",
                    "codellama");
        } else if (lower.contains("googleapis.com") || lower.contains("gemini")) {
            return List.of(
                    "gemini-2.0-flash",
                    "gemini-1.5-flash",
                    "gemini-1.5-pro",
                    "gemini-3.8-flash");
        }
        return DEFAULT_OPENAI_PRESETS;
    }

    public static boolean show(SettingsStore settingsStore) {
        return show(settingsStore, "AI");
    }

    public static boolean show(SettingsStore settingsStore, String initialTab) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Cài đặt Hệ thống & Trợ lý AI");
        dialog.setHeaderText("Tùy chỉnh cấu hình trình duyệt, môi trường thực thi và kết nối AI");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) pane.lookupButton(ButtonType.OK);
        okButton.setText("Lưu cài đặt");
        Button cancelButton = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelButton.setText("Hủy");

        SettingsStore store = settingsStore != null ? settingsStore : new SettingsStore();
        SettingsStore.Settings currentSettings;
        try {
            currentSettings = store.load();
        } catch (IOException e) {
            currentSettings = new SettingsStore.Settings();
        }
        final SettingsStore.Settings finalSettings = currentSettings;
        AiConfig aiConfig = finalSettings.getAiConfig();

        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("settings-tab-pane");
        tabPane.setStyle("-fx-tab-min-width: 150px;");

        // ══════════════════════════════════════════════════════════
        // TAB 1: Cài đặt Chung & Trình duyệt (General Settings)
        // ══════════════════════════════════════════════════════════
        GridPane generalGrid = new GridPane();
        generalGrid.setHgap(12);
        generalGrid.setVgap(14);
        generalGrid.setPadding(new Insets(16, 20, 16, 20));
        generalGrid.setPrefWidth(620);

        int genRow = 0;

        // Default Browser
        Label browserLabel = new Label("Trình duyệt mặc định:");
        browserLabel.getStyleClass().add("field-label");
        ComboBox<String> browserCombo = new ComboBox<>();
        browserCombo.getItems().addAll("CHROMIUM", "FIREFOX", "WEBKIT", "CHROME", "EDGE");
        browserCombo.setValue(finalSettings.getBrowser() != null ? finalSettings.getBrowser().toUpperCase() : "CHROMIUM");
        browserCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(browserCombo, Priority.ALWAYS);
        generalGrid.add(browserLabel, 0, genRow);
        generalGrid.add(browserCombo, 1, genRow);
        genRow++;

        // Headless Checkbox
        Label headlessLabel = new Label("Chế độ hiển thị:");
        headlessLabel.getStyleClass().add("field-label");
        CheckBox headlessCheck = new CheckBox("Chạy trình duyệt ẩn (Headless mode)");
        headlessCheck.setSelected(finalSettings.isHeadlessDefault());
        generalGrid.add(headlessLabel, 0, genRow);
        generalGrid.add(headlessCheck, 1, genRow);
        genRow++;

        // Share Context Checkbox
        Label shareLabel = new Label("Phiên làm việc:");
        shareLabel.getStyleClass().add("field-label");
        CheckBox shareCheck = new CheckBox("Dùng chung BrowserContext giữa các Test Cases trong Suite");
        shareCheck.setSelected(finalSettings.isShareContextDefault());
        generalGrid.add(shareLabel, 0, genRow);
        generalGrid.add(shareCheck, 1, genRow);
        genRow++;

        // Stop on first failure
        Label stopLabel = new Label("Quy tắc dừng:");
        stopLabel.getStyleClass().add("field-label");
        CheckBox stopCheck = new CheckBox("Dừng toàn bộ lượt chạy ngay khi gặp lỗi đầu tiên (Fail-Fast)");
        stopCheck.setSelected(finalSettings.isStopOnFirstFailureDefault());
        generalGrid.add(stopLabel, 0, genRow);
        generalGrid.add(stopCheck, 1, genRow);
        genRow++;

        // Theme
        Label themeLabel = new Label("Giao diện (Theme):");
        themeLabel.getStyleClass().add("field-label");
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Tối (Tokyo Night)", "Sáng (Clean Light)");
        themeCombo.setValue(finalSettings.isDarkTheme() ? "Tối (Tokyo Night)" : "Sáng (Clean Light)");
        themeCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(themeCombo, Priority.ALWAYS);
        generalGrid.add(themeLabel, 0, genRow);
        generalGrid.add(themeCombo, 1, genRow);
        genRow++;

        // Backup / Restore JSON
        Label backupLabel = new Label("Sao lưu cấu hình:");
        backupLabel.getStyleClass().add("field-label");

        Button exportBtn = new Button("Xuất file JSON");
        exportBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(exportBtn, UiIcons.COPY, 12);
        exportBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Xuất cấu hình Studio ra file JSON");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            fc.setInitialFileName("studio-settings-backup.json");
            File target = fc.showSaveDialog(dialog.getOwner());
            if (target != null) {
                try {
                    store.exportSettings(target.toPath());
                    ToastService.success("Đã xuất file cấu hình thành công!");
                } catch (Exception ex) {
                    ToastService.error("Lỗi xuất file: " + ex.getMessage());
                }
            }
        });

        Button importBtn = new Button("Nhập từ file JSON");
        importBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(importBtn, UiIcons.REFRESH, 12);
        importBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Nhập cấu hình Studio từ file JSON");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            File src = fc.showOpenDialog(dialog.getOwner());
            if (src != null) {
                try {
                    SettingsStore.Settings imported = store.importSettings(src.toPath());
                    browserCombo.setValue(imported.getBrowser());
                    headlessCheck.setSelected(imported.isHeadlessDefault());
                    shareCheck.setSelected(imported.isShareContextDefault());
                    stopCheck.setSelected(imported.isStopOnFirstFailureDefault());
                    themeCombo.setValue(imported.isDarkTheme() ? "Tối (Tokyo Night)" : "Sáng (Clean Light)");
                    ToastService.success("Đã nhập cấu hình thành công! Hãy bấm 'Lưu cài đặt'.");
                } catch (Exception ex) {
                    ToastService.error("Lỗi nhập file: " + ex.getMessage());
                }
            }
        });

        HBox backupBox = new HBox(8, exportBtn, importBtn);
        generalGrid.add(backupLabel, 0, genRow);
        generalGrid.add(backupBox, 1, genRow);

        Tab generalTab = new Tab("Chung & Trình duyệt", generalGrid);
        generalTab.setClosable(false);

        // ══════════════════════════════════════════════════════════
        // TAB 2: Cấu hình Trợ lý AI (AI & LLM Configuration)
        // ══════════════════════════════════════════════════════════
        GridPane aiGrid = new GridPane();
        aiGrid.setHgap(12);
        aiGrid.setVgap(14);
        aiGrid.setPadding(new Insets(16, 20, 16, 20));
        aiGrid.setPrefWidth(620);

        int aiRow = 0;

        // API Key
        Label keyLabel = new Label("API Key:");
        keyLabel.getStyleClass().add("field-label");

        String originalApiKey = aiConfig.getApiKey() != null ? aiConfig.getApiKey().trim() : "";
        final String[] actualKeyHolder = new String[]{ originalApiKey };
        final boolean[] isShowingPlain = new boolean[]{ false };

        TextField keyField = new TextField();
        // Hiển thị mặc định dạng mask (sk-••••••••XYZW) theo đúng phong cách của label bên dưới
        if (!originalApiKey.isEmpty()) {
            keyField.setText(com.studio.util.ApiKeyCipher.mask(originalApiKey));
        }
        keyField.setPromptText("sk-proj-... hoặc để trống để dùng biến môi trường");
        HBox.setHgrow(keyField, Priority.ALWAYS);

        Button toggleShowBtn = new Button();
        toggleShowBtn.getStyleClass().add("icon-btn");
        UiTooltips.install(toggleShowBtn, "Hiện đầy đủ API Key");
        UiIcons.setGraphic(toggleShowBtn, UiIcons.EYE, 13);

        Label envHint = new Label();
        envHint.getStyleClass().add("settings-hint-label");

        Runnable updateHint = () -> {
            String current = actualKeyHolder[0];
            String env = System.getenv("OPENAI_API_KEY");
            if (current != null && !current.isBlank()) {
                envHint.setText("Đang khả dụng: API Key trực tiếp (" + com.studio.util.ApiKeyCipher.mask(current) + ")");
            } else if (env != null && !env.isBlank()) {
                envHint.setText("Đang khả dụng: biến môi trường OPENAI_API_KEY (" + com.studio.util.ApiKeyCipher.mask(env) + ")");
            } else {
                envHint.setText("Chưa phát hiện API Key (cả trong cài đặt và biến môi trường OPENAI_API_KEY).");
            }
        };
        updateHint.run();

        keyField.textProperty().addListener((obs, oldVal, newVal) -> {
            String trimmed = newVal != null ? newVal.trim() : "";
            if (trimmed.isEmpty()) {
                actualKeyHolder[0] = "";
            } else if (com.studio.util.ApiKeyCipher.isMasked(trimmed)) {
                if (!originalApiKey.isEmpty() && (trimmed.equals(com.studio.util.ApiKeyCipher.mask(originalApiKey)) || trimmed.contains("•"))) {
                    actualKeyHolder[0] = originalApiKey;
                }
            } else {
                actualKeyHolder[0] = trimmed;
            }
            updateHint.run();
        });

        keyField.focusedProperty().addListener((obs, oldVal, focused) -> {
            if (focused && !isShowingPlain[0] && com.studio.util.ApiKeyCipher.isMasked(keyField.getText())) {
                Platform.runLater(keyField::selectAll);
            }
        });

        toggleShowBtn.setOnAction(e -> {
            isShowingPlain[0] = !isShowingPlain[0];
            if (isShowingPlain[0]) {
                keyField.setText(actualKeyHolder[0]);
                UiIcons.setGraphic(toggleShowBtn, UiIcons.EYE_OFF, 13);
                UiTooltips.install(toggleShowBtn, "Che mờ API Key");
            } else {
                if (actualKeyHolder[0] != null && !actualKeyHolder[0].isBlank()) {
                    keyField.setText(com.studio.util.ApiKeyCipher.mask(actualKeyHolder[0]));
                } else {
                    keyField.setText("");
                }
                UiIcons.setGraphic(toggleShowBtn, UiIcons.EYE, 13);
                UiTooltips.install(toggleShowBtn, "Hiện đầy đủ API Key");
            }
        });

        HBox keyBox = new HBox(6, keyField, toggleShowBtn);
        keyBox.setAlignment(Pos.CENTER_LEFT);

        Label securityBadge = new Label("🔒 Mã hóa AES-256-GCM bảo vệ an toàn theo phần cứng thiết bị.");
        securityBadge.setStyle("-fx-font-size: 11px; -fx-text-fill: #10b981;");

        VBox keyCol = new VBox(4, keyBox, envHint, securityBadge);
        aiGrid.add(keyLabel, 0, aiRow);
        aiGrid.add(keyCol, 1, aiRow);
        aiRow++;

        // Base URL
        Label urlLabel = new Label("Base URL:");
        urlLabel.getStyleClass().add("field-label");
        TextField urlField = new TextField(aiConfig.getBaseUrl());
        urlField.setPromptText("https://api.openai.com/v1");

        Label urlHint = new Label("Hỗ trợ OpenAI, Azure, OpenRouter (https://openrouter.ai/api/v1), Gemini, Ollama");
        urlHint.getStyleClass().add("settings-hint-label");
        VBox urlCol = new VBox(4, urlField, urlHint);

        aiGrid.add(urlLabel, 0, aiRow);
        aiGrid.add(urlCol, 1, aiRow);
        aiRow++;

        // Model
        Label modelLabel = new Label("Model:");
        modelLabel.getStyleClass().add("field-label");
        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.setEditable(true);
        modelCombo.setPromptText("Chọn hoặc nhập tên model...");
        modelCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modelCombo, Priority.ALWAYS);

        List<String> initialPresets = new ArrayList<>(getPresetsForBaseUrl(aiConfig.getBaseUrl()));
        if (aiConfig.getModel() != null && !aiConfig.getModel().isBlank() && !initialPresets.contains(aiConfig.getModel())) {
            initialPresets.add(0, aiConfig.getModel());
        }
        modelCombo.getItems().setAll(initialPresets);
        modelCombo.setValue(aiConfig.getModel());
        modelCombo.getEditor().setText(aiConfig.getModel());

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
        UiIcons.setGraphic(fetchModelsBtn, UiIcons.REFRESH, 12);
        UiTooltips.install(fetchModelsBtn, "Gửi yêu cầu tới endpoint /models của Base URL để lấy danh sách model thực tế");

        HBox modelBox = new HBox(6, modelCombo, fetchModelsBtn);
        modelBox.setAlignment(Pos.CENTER_LEFT);

        aiGrid.add(modelLabel, 0, aiRow);
        aiGrid.add(modelBox, 1, aiRow);
        aiRow++;

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

        aiGrid.add(tempLabel, 0, aiRow);
        aiGrid.add(tempBox, 1, aiRow);
        aiRow++;

        // Max Tokens
        Label maxTokensLabel = new Label("Max Tokens:");
        maxTokensLabel.getStyleClass().add("field-label");
        Spinner<Integer> maxTokensSpinner = new Spinner<>(512, 32768, aiConfig.getMaxTokens() > 0 ? aiConfig.getMaxTokens() : 4096, 512);
        maxTokensSpinner.setEditable(true);
        HBox.setHgrow(maxTokensSpinner, Priority.ALWAYS);
        aiGrid.add(maxTokensLabel, 0, aiRow);
        aiGrid.add(maxTokensSpinner, 1, aiRow);
        aiRow++;

        // Test connection
        Label testLabel = new Label("Kiểm tra:");
        testLabel.getStyleClass().add("field-label");

        Button testConnBtn = new Button("Kiểm tra kết nối");
        testConnBtn.getStyleClass().add("btn-secondary");
        UiIcons.setGraphic(testConnBtn, UiIcons.CHECK, 12);

        Label testStatusLabel = new Label("");
        testStatusLabel.setWrapText(true);
        testStatusLabel.getStyleClass().add("settings-hint-label");

        fetchModelsBtn.setOnAction(e -> {
            fetchModelsBtn.setDisable(true);
            testStatusLabel.setText("Đang tải danh sách model từ Base URL...");
            testStatusLabel.getStyleClass().setAll("settings-hint-label");

            AiConfig probeConfig = new AiConfig();
            probeConfig.setApiKey(actualKeyHolder[0]);
            probeConfig.setBaseUrl(urlField.getText().trim());

            Thread thread = new Thread(() -> {
                try {
                    OpenAiClient client = new OpenAiClient();
                    List<String> models = client.fetchModels(probeConfig);
                    Platform.runLater(() -> {
                        fetchModelsBtn.setDisable(false);
                        if (!models.isEmpty()) {
                            String current = modelCombo.getEditor().getText();
                            modelCombo.getItems().setAll(models);
                            if (current != null && !current.isBlank()) {
                                if (!modelCombo.getItems().contains(current)) {
                                    modelCombo.getItems().add(0, current);
                                }
                                modelCombo.setValue(current);
                                modelCombo.getEditor().setText(current);
                            } else {
                                modelCombo.setValue(models.get(0));
                            }
                            testStatusLabel.setText("Đã nạp " + models.size() + " models từ server!");
                            testStatusLabel.getStyleClass().setAll("settings-status-success");
                        } else {
                            testStatusLabel.setText("Không tìm thấy model nào từ server.");
                            testStatusLabel.getStyleClass().setAll("settings-status-error");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        fetchModelsBtn.setDisable(false);
                        testStatusLabel.setText("Không tải được danh sách: " + ex.getMessage());
                        testStatusLabel.getStyleClass().setAll("settings-status-error");
                    });
                }
            }, "OpenAi-FetchModels");
            thread.setDaemon(true);
            thread.start();
        });

        testConnBtn.setOnAction(e -> {
            testConnBtn.setDisable(true);
            testStatusLabel.setText("Đang kiểm tra kết nối...");
            testStatusLabel.getStyleClass().setAll("settings-hint-label");

            AiConfig probeConfig = new AiConfig();
            probeConfig.setApiKey(actualKeyHolder[0]);
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
                        testStatusLabel.getStyleClass().setAll("settings-status-success");
                    } else {
                        testStatusLabel.setText(res.getMessage());
                        testStatusLabel.getStyleClass().setAll("settings-status-error");
                    }
                });
            }, "OpenAi-TestConnection");
            thread.setDaemon(true);
            thread.start();
        });

        VBox testBox = new VBox(6, testConnBtn, testStatusLabel);
        aiGrid.add(testLabel, 0, aiRow);
        aiGrid.add(testBox, 1, aiRow);

        Tab aiTab = new Tab("Trợ lý AI & Mô hình", aiGrid);
        aiTab.setClosable(false);

        tabPane.getTabs().addAll(generalTab, aiTab);
        if ("GENERAL".equalsIgnoreCase(initialTab)) {
            tabPane.getSelectionModel().select(generalTab);
        } else {
            tabPane.getSelectionModel().select(aiTab);
        }

        pane.setContent(tabPane);
        pane.setPrefWidth(660);
        DialogTheme.apply(dialog);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    // Save General Settings
                    if (browserCombo.getValue() != null && !browserCombo.getValue().isBlank()) {
                        finalSettings.setBrowser(browserCombo.getValue().trim());
                    }
                    finalSettings.setHeadlessDefault(headlessCheck.isSelected());
                    finalSettings.setShareContextDefault(shareCheck.isSelected());
                    finalSettings.setStopOnFirstFailureDefault(stopCheck.isSelected());
                    String selectedTheme = "Sáng (Clean Light)".equals(themeCombo.getValue()) ? "light" : "dark";
                    finalSettings.setTheme(selectedTheme);

                    // Save AI Settings
                    AiConfig cfg = finalSettings.getAiConfig();
                    String key = actualKeyHolder[0] != null ? actualKeyHolder[0].trim() : "";
                    cfg.setApiKey(key);

                    String url = urlField.getText() != null ? urlField.getText().trim() : "";
                    cfg.setBaseUrl(url);

                    String enteredModel = modelCombo.getEditor() != null && modelCombo.getEditor().getText() != null && !modelCombo.getEditor().getText().isBlank()
                            ? modelCombo.getEditor().getText().trim()
                            : (modelCombo.getValue() != null ? modelCombo.getValue().trim() : "gpt-4o");
                    cfg.setModel(enteredModel);
                    cfg.setTemperature(tempSlider.getValue());

                    try {
                        maxTokensSpinner.commitValue();
                    } catch (Exception ignored) {}
                    Integer tokens = maxTokensSpinner.getValue();
                    cfg.setMaxTokens(tokens != null && tokens > 0 ? tokens : 4096);

                    store.save(finalSettings);
                    if (!selectedTheme.equalsIgnoreCase(ThemeService.getCurrentTheme())) {
                        ThemeService.setTheme(selectedTheme);
                    }
                    ToastService.success("💾 Đã lưu toàn bộ cấu hình hệ thống & AI!");
                    return true;
                } catch (Exception ex) {
                    ToastService.error("⚠️ Không thể lưu cấu hình: " + ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        java.util.Optional<Boolean> result = dialog.showAndWait();
        return result.orElse(false);
    }
}
