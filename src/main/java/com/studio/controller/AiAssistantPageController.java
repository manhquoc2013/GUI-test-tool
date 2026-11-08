package com.studio.controller;

import com.studio.model.*;
import com.studio.service.AiTestGeneratorService;
import com.studio.service.AiTokenOptimizer;
import com.studio.service.DocumentParserService;
import com.studio.service.ToastService;
import com.studio.store.AiChatHistoryStore;
import com.studio.store.ProjectStore;
import com.studio.store.SettingsStore;
import com.studio.util.DialogTheme;
import com.studio.util.StudioPaths;
import com.studio.util.UiIcons;
import com.studio.util.UiTooltips;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the dedicated AI Assistant screen.
 * Supports persistent chat history, 1-click message copy, full undo/rollback of
 * generated test cases and suite file changes, and token usage optimization.
 */
public class AiAssistantPageController implements Initializable {

    @FXML private Label tokenUsageBadge;
    @FXML private Label modelIndicatorLabel;
    @FXML private Button settingsBtn;
    @FXML private Button clearChatBtn;
    @FXML private ComboBox<String> targetGroupCombo;
    @FXML private ComboBox<String> targetSuiteCombo;
    @FXML private Button refreshTargetsBtn;
    @FXML private ComboBox<com.studio.model.AiSkill> skillComboBox;
    @FXML private ScrollPane chatScroll;
    @FXML private VBox chatMessagesBox;
    @FXML private VBox attachedFilesBox;
    @FXML private FlowPane attachedChipsPane;
    @FXML private TextArea promptInputArea;
    @FXML private Button attachFileBtn;
    @FXML private Button scanDomBtn;
    @FXML private Button clearAttachmentsBtn;
    @FXML private ProgressIndicator generationProgress;
    @FXML private Button sendBtn;
    @FXML private Label resultSummaryLabel;
    @FXML private Button applyToSuiteBtn;
    @FXML private ListView<AiGeneratedCase> generatedCasesListView;
    @FXML private Label previewCaseNameLabel;
    @FXML private Label previewCaseUrlLabel;
    @FXML private TabPane detailTabPane;
    @FXML private Tab previewStepsTab;
    @FXML private ListView<String> previewStepsListView;
    @FXML private TextArea previewScriptEditor;
    @FXML private Tab previewAssertsTab;
    @FXML private ListView<String> previewAssertsListView;
    @FXML private Tab previewTestDataTab;
    @FXML private ListView<String> previewTestDataListView;
    @FXML private TextArea rawResponseViewer;

    private final SettingsStore settingsStore = new SettingsStore();
    private final ProjectStore projectStore = new ProjectStore();
    private final DocumentParserService documentParser = new DocumentParserService();
    private final AiTestGeneratorService aiGenerator = new AiTestGeneratorService();
    private final AiChatHistoryStore chatHistoryStore = new AiChatHistoryStore();
    private final AiTokenOptimizer tokenOptimizer = new AiTokenOptimizer();
    private final com.studio.service.LiveDomScanner domScanner = new com.studio.service.LiveDomScanner();

    private final List<DocumentParserService.ParsedDocument> attachedDocuments = new ArrayList<>();
    private final List<AiGeneratedCase> currentGeneratedCases = new ArrayList<>();
    private AiChatSession currentSession = new AiChatSession("global");
    private String liveDomJson = null;

    private HBox thinkingBubbleWrapper = null;
    private Timeline thinkingTimeline = null;
    private Label thinkingStatusLabel = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refreshModelIndicator();
        reloadTargets();

        if (settingsBtn != null) {
            UiIcons.setGraphic(settingsBtn, UiIcons.SETTINGS, 12);
            UiTooltips.install(settingsBtn, "Cấu hình OpenAI API & Mô hình AI");
        }
        if (clearChatBtn != null) {
            UiIcons.setGraphic(clearChatBtn, UiIcons.TRASH, 12);
            UiTooltips.install(clearChatBtn, "Xóa toàn bộ lịch sử chat của Suite này");
        }
        if (sendBtn != null) {
            UiIcons.setGraphic(sendBtn, UiIcons.PLAY, 12);
            UiTooltips.install(sendBtn, "Gửi yêu cầu sinh test case (Ctrl+Enter / Cmd+Enter)");
        }
        if (attachFileBtn != null) {
            UiIcons.setGraphic(attachFileBtn, UiIcons.PLUS, 12);
            UiTooltips.install(attachFileBtn, "Đính kèm tài liệu (.txt, .md, Word, PDF) hoặc ảnh UI (.png, .jpg)");
        }
        if (scanDomBtn != null) {
            UiIcons.setGraphic(scanDomBtn, UiIcons.SEARCH, 12);
            UiTooltips.install(scanDomBtn, "Quét cấu trúc tương tác DOM từ trang web thực tế");
        }
        if (applyToSuiteBtn != null) {
            UiIcons.setGraphic(applyToSuiteBtn, UiIcons.CHECK, 12);
            UiTooltips.install(applyToSuiteBtn, "Nhập các test cases vừa sinh vào Test Suite đã chọn");
        }
        if (refreshTargetsBtn != null) {
            UiIcons.setGraphic(refreshTargetsBtn, UiIcons.REFRESH, 12);
            UiTooltips.install(refreshTargetsBtn, "Làm mới danh sách Nhóm & Suite");
        }

        targetGroupCombo.setOnAction(e -> onGroupSelected());
        targetSuiteCombo.setOnAction(e -> loadChatHistoryForSuite());

        if (skillComboBox != null) {
            skillComboBox.getItems().setAll(com.studio.model.AiSkill.values());
            skillComboBox.setValue(com.studio.model.AiSkill.AUTO);
            skillComboBox.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(com.studio.model.AiSkill item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getDisplayName());
                    }
                }
            });
            skillComboBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(com.studio.model.AiSkill item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getDisplayName());
                    }
                }
            });
            UiTooltips.install(skillComboBox, "Chọn kỹ năng chuyên sâu của AI hoặc để Tự động nhận diện");
        }

        // Setup case list cell factory
        generatedCasesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AiGeneratedCase item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    int stepsCount = item.getTestSteps() != null ? item.getTestSteps().size() : 0;
                    int assertsCount = item.getAssertions() != null ? item.getAssertions().size() : 0;
                    setText(item.getName() + " (" + stepsCount + " steps, " + assertsCount + " asserts)");
                }
            }
        });

        generatedCasesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showCaseDetail(newVal);
        });

        // Key listener for prompt area: Ctrl+Enter or Cmd+Enter to send
        promptInputArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && (event.isControlDown() || event.isMetaDown())) {
                sendPrompt();
                event.consume();
            }
        });

        // Dynamic token estimation listener as user types
        promptInputArea.textProperty().addListener((obs, oldVal, newVal) -> updateTokenUsageDisplay());

        // Load initial chat history for the default target suite
        loadChatHistoryForSuite();
    }

    public void refreshModelIndicator() {
        try {
            SettingsStore.Settings s = settingsStore.load();
            modelIndicatorLabel.setText("Mô hình: " + s.getAiConfig().getEffectiveModel());
        } catch (Exception ignored) {
            modelIndicatorLabel.setText("Mô hình: " + AiConfig.DEFAULT_MODEL);
        }
    }

    @FXML
    public void reloadTargets() {
        String prevGroup = targetGroupCombo.getValue();
        String prevSuite = targetSuiteCombo.getValue();

        targetGroupCombo.getItems().clear();
        targetSuiteCombo.getItems().clear();

        Path projDir = StudioPaths.getProjectDir();
        if (Files.exists(projDir)) {
            try (var stream = Files.list(projDir)) {
                stream.filter(Files::isDirectory).forEach(dir -> {
                    targetGroupCombo.getItems().add(dir.getFileName().toString());
                });
            } catch (IOException ignored) {}
        }

        if (prevGroup != null && targetGroupCombo.getItems().contains(prevGroup)) {
            targetGroupCombo.setValue(prevGroup);
            onGroupSelected();
            if (prevSuite != null && targetSuiteCombo.getItems().contains(prevSuite)) {
                targetSuiteCombo.setValue(prevSuite);
            }
        } else if (!targetGroupCombo.getItems().isEmpty()) {
            targetGroupCombo.getSelectionModel().select(0);
            onGroupSelected();
        }
    }

    private void onGroupSelected() {
        String groupName = targetGroupCombo.getValue();
        targetSuiteCombo.getItems().clear();
        if (groupName == null || groupName.isBlank()) return;

        Path groupDir = StudioPaths.getProjectDir().resolve(groupName);
        if (Files.exists(groupDir)) {
            try (var stream = Files.list(groupDir)) {
                stream.filter(Files::isDirectory).forEach(suiteDir -> {
                    targetSuiteCombo.getItems().add(suiteDir.getFileName().toString());
                });
            } catch (IOException ignored) {}
        }
        if (!targetSuiteCombo.getItems().isEmpty()) {
            targetSuiteCombo.getSelectionModel().select(0);
        }
    }

    public Path getSelectedSuiteDir() {
        String group = targetGroupCombo.getValue();
        String suite = targetSuiteCombo.getValue();
        if (group != null && suite != null && !group.isBlank() && !suite.isBlank()) {
            Path p = StudioPaths.getProjectDir().resolve(group).resolve(suite);
            if (Files.exists(p) && Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    public void loadChatHistoryForSuite() {
        Path suiteDir = getSelectedSuiteDir();
        currentSession = chatHistoryStore.loadSession(suiteDir);
        renderChatHistory();
        updateTokenUsageDisplay();
    }

    private void renderChatHistory() {
        chatMessagesBox.getChildren().clear();

        if (currentSession.getMessages().isEmpty()) {
            // Add default welcome message
            AiChatMessage welcomeMsg = new AiChatMessage("assistant",
                    "Xin chào! Tôi là Trợ lý AI của Test Studio.\n\n"
                    + "Bạn có thể gửi yêu cầu viết test case, hoặc đính kèm tài liệu yêu cầu (.txt, .md, Word .docx, PDF .pdf) hoặc ảnh chụp màn hình UI (.png, .jpg) để tôi tự động phân tích và sinh kịch bản Playwright runnable kèm các Assertions kiểm thử!");
            welcomeMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(welcomeMsg));
            renderMessageBubble(welcomeMsg, false);
            clearPreviewArea();
        } else {
            for (AiChatMessage msg : currentSession.getMessages()) {
                renderMessageBubble(msg, false);
            }
            // Restore preview cases from the latest assistant response
            restoreLatestPreviewCases();
        }

        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    private void restoreLatestPreviewCases() {
        List<AiChatMessage> msgs = currentSession.getMessages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            AiChatMessage m = msgs.get(i);
            if ("assistant".equalsIgnoreCase(m.getRole()) && !m.getGeneratedCasesSnapshot().isEmpty()) {
                currentGeneratedCases.clear();
                currentGeneratedCases.addAll(m.getGeneratedCasesSnapshot());
                generatedCasesListView.getItems().setAll(currentGeneratedCases);
                resultSummaryLabel.setText(m.getContent());
                applyToSuiteBtn.setDisable(false);
                generatedCasesListView.getSelectionModel().select(0);
                return;
            }
        }
        clearPreviewArea();
    }

    private void clearPreviewArea() {
        currentGeneratedCases.clear();
        generatedCasesListView.getItems().clear();
        resultSummaryLabel.setText("Chưa có kết quả. Hãy nhập yêu cầu ở khung bên trái.");
        applyToSuiteBtn.setDisable(true);
        showCaseDetail(null);
    }

    private void updateTokenUsageDisplay() {
        if (tokenUsageBadge == null) return;
        int promptTokens = tokenOptimizer.estimateTokens(promptInputArea.getText());
        int historyTokens = currentSession != null ? currentSession.calculateTotalTokens() : 0;
        int total = promptTokens + historyTokens;
        tokenUsageBadge.setText("Tokens: ~" + total + " / 4,000 (Prompt: ~" + promptTokens + ")");
    }

    @FXML
    public void openSettings() {
        boolean updated = AiSettingsDialog.show(settingsStore, "AI");
        if (updated) {
            refreshModelIndicator();
            updateTokenUsageDisplay();
        }
    }

    @FXML
    public void clearChat() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa lịch sử");
        alert.setHeaderText("Xóa toàn bộ đoạn hội thoại");
        alert.setContentText("Bạn có chắc chắn muốn xóa toàn bộ lịch sử chat của Suite này không?");
        DialogTheme.apply(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            removeThinkingBubble();
            Path suiteDir = getSelectedSuiteDir();
            try {
                chatHistoryStore.clearSession(suiteDir);
            } catch (IOException ignored) {}
            currentSession = new AiChatSession(suiteDir != null ? suiteDir.getFileName().toString() : "global");
            renderChatHistory();
            updateTokenUsageDisplay();
            ToastService.info("Đoạn hội thoại đã được xóa.");
        }
    }

    @FXML
    public void attachFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tài liệu hoặc ảnh chụp giao diện");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả tài liệu hỗ trợ", "*.txt", "*.md", "*.json", "*.csv", "*.tsv", "*.docx", "*.xlsx", "*.xls", "*.pptx", "*.pdf", "*.html", "*.xml", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("Văn bản, Bảng tính & Specs (*.docx, *.xlsx, *.pptx, *.pdf, *.md, *.txt, *.csv)", "*.docx", "*.xlsx", "*.xls", "*.pptx", "*.pdf", "*.txt", "*.md", "*.json", "*.csv", "*.tsv", "*.html", "*.xml"),
                new FileChooser.ExtensionFilter("Hình ảnh / UI Screenshots (*.png, *.jpg)", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );
        List<File> files = chooser.showOpenMultipleDialog(promptInputArea.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            for (File f : files) {
                try {
                    DocumentParserService.ParsedDocument doc = documentParser.parse(f.toPath());
                    attachedDocuments.add(doc);
                    addFileChip(doc);
                } catch (Exception e) {
                    ToastService.error("Không thể đọc file: " + f.getName());
                }
            }
            updateAttachmentsVisibility();
        }
    }

    @FXML
    public void clearAttachments() {
        attachedDocuments.clear();
        attachedChipsPane.getChildren().clear();
        updateAttachmentsVisibility();
    }

    private void updateAttachmentsVisibility() {
        boolean hasFiles = !attachedDocuments.isEmpty();
        attachedFilesBox.setVisible(hasFiles);
        attachedFilesBox.setManaged(hasFiles);
        clearAttachmentsBtn.setVisible(hasFiles);
        clearAttachmentsBtn.setManaged(hasFiles);
    }

    private void addFileChip(DocumentParserService.ParsedDocument doc) {
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("ai-file-chip");

        Label label = new Label((doc.isImage() ? "[Ảnh] " : "[Tài liệu] ") + doc.getFileName());
        label.getStyleClass().add("ai-file-chip-label");

        chip.getChildren().add(label);

        if (!doc.isImage() && doc.getContent() != null && !doc.getContent().isBlank()) {
            Button previewMdBtn = new Button("👁️ Xem MD");
            previewMdBtn.setStyle("-fx-font-size: 10px; -fx-padding: 1 5; -fx-background-color: -color-primary-dim; -fx-text-fill: -color-primary; -fx-background-radius: 3; -fx-cursor: hand;");
            previewMdBtn.setOnAction(e -> MarkdownPreviewDialog.show(doc));
            chip.getChildren().add(previewMdBtn);
        }

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("ai-file-chip-remove");
        removeBtn.setOnAction(e -> {
            attachedChipsPane.getChildren().remove(chip);
            attachedDocuments.remove(doc);
            updateAttachmentsVisibility();
        });

        chip.getChildren().add(removeBtn);
        attachedChipsPane.getChildren().add(chip);
    }

    @FXML
    public void applyQuickBva() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.EXHAUSTIVE_UNIT_TEST);
        }
        promptInputArea.setText("Phân tích luồng điều kiện (Control Flow), xác định tập giá trị biên (BVA) và sinh mã nguồn Unit Test có tham số hóa cho đoạn logic sau:\n\n");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickLogin() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.PLAYWRIGHT_E2E);
        }
        promptInputArea.setText("Viết kịch bản Playwright E2E cho trang đăng nhập: Nhập email hợp lệ và mật khẩu, bấm nút Đăng nhập, xác minh người dùng được chuyển hướng vào Dashboard và hiển thị lời chào.");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickRca() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.FAILURE_RCA);
        }
        promptInputArea.setText("Phân tích nguyên nhân gốc rễ (RCA) và đưa ra mã sửa lỗi drop-in cho log lỗi kiểm thử sau:\n\n");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickSecurity() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.SECURITY_FUZZING);
        }
        promptInputArea.setText("Sinh ma trận vector kiểm thử bảo mật & fuzzing dữ liệu đầu vào (XSS, SQLi, Buffer Overflow, Unicode injection) cho form sau:\n\n");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickCart() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.PLAYWRIGHT_E2E);
        }
        promptInputArea.setText("Viết kịch bản kiểm thử quy trình mua hàng: Thêm sản phẩm vào giỏ, kiểm tra số lượng và tổng tiền cập nhật chính xác, tiến hành thanh toán.");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickDoc() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.UNIVERSAL_DOCUMENT_COMPILER);
        }
        promptInputArea.setText("Phân tích kỹ các yêu cầu trong tài liệu đính kèm và sinh bộ Test Cases bao quát các trường hợp chính (Happy path, Negative path, và Edge cases) kèm đầy đủ Assertions kiểm thử.");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void sendPrompt() {
        String prompt = promptInputArea.getText() != null ? promptInputArea.getText().trim() : "";
        if (prompt.isEmpty() && attachedDocuments.isEmpty()) {
            ToastService.warn("⚠️ Vui lòng nhập nội dung yêu cầu hoặc đính kèm tài liệu.");
            return;
        }

        // Auto-detect or override skill from slash command if user typed it
        com.studio.model.AiSkill chosenSkill = (skillComboBox != null && skillComboBox.getValue() != null)
                ? skillComboBox.getValue()
                : com.studio.model.AiSkill.AUTO;

        com.studio.model.AiSkill slashSkill = com.studio.model.AiSkill.fromSlashCommand(prompt);
        if (slashSkill != null) {
            chosenSkill = slashSkill;
            if (skillComboBox != null) {
                skillComboBox.setValue(slashSkill);
            }
        }
        final com.studio.model.AiSkill activeSkill = chosenSkill;

        SettingsStore.Settings settings;
        try {
            settings = settingsStore.load();
        } catch (IOException e) {
            settings = new SettingsStore.Settings();
        }
        AiConfig aiConfig = settings.getAiConfig();

        if (!aiConfig.hasApiKey()) {
            ToastService.warn("⚠️ Chưa có OpenAI API Key. Vui lòng mở Cài đặt OpenAI để điền API Key.");
            boolean updated = AiSettingsDialog.show(settingsStore, "AI");
            if (updated) {
                refreshModelIndicator();
                updateTokenUsageDisplay();
                try {
                    settings = settingsStore.load();
                    aiConfig = settings.getAiConfig();
                } catch (Exception ignored) {}
            }
            if (!aiConfig.hasApiKey()) {
                return;
            }
        }

        // Create User Message
        StringBuilder userChatText = new StringBuilder();
        if (activeSkill != com.studio.model.AiSkill.AUTO) {
            userChatText.append("[").append(activeSkill.getDisplayName()).append("]\n");
        }
        userChatText.append(prompt);
        List<String> fileNames = new ArrayList<>();
        if (!attachedDocuments.isEmpty()) {
            userChatText.append("\n\nĐã đính kèm: ");
            for (var d : attachedDocuments) {
                userChatText.append(d.getFileName()).append(" ");
                fileNames.add(d.getFileName());
            }
        }

        AiChatMessage userMsg = new AiChatMessage("user", userChatText.toString());
        userMsg.setAttachedFiles(fileNames);
        userMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(userMsg));
        currentSession.addMessage(userMsg);
        renderMessageBubble(userMsg, true);

        // Snapshot attached docs and clear input
        List<DocumentParserService.ParsedDocument> docsSnapshot = new ArrayList<>(attachedDocuments);
        promptInputArea.clear();
        clearAttachments();
        updateTokenUsageDisplay();

        sendBtn.setDisable(true);
        sendBtn.setText("Đang sinh kịch bản...");
        generationProgress.setVisible(true);
        generationProgress.setManaged(true);
        showThinkingBubble(aiConfig.getEffectiveModel() + " — " + activeSkill.getDisplayName());

        Path suiteDir = getSelectedSuiteDir();
        String context = "Nhóm đích: " + targetGroupCombo.getValue() + ", Suite đích: " + targetSuiteCombo.getValue();

        // Tự động phát hiện URL từ prompt hoặc từ Suite hiện tại nếu chưa có Live DOM
        String promptUrl = com.studio.service.LiveDomScanner.extractFirstUrl(prompt);
        if (promptUrl == null && suiteDir != null) {
            try {
                TestSuite s = projectStore.loadAndMigrateSuite(suiteDir);
                if (s != null && s.getCases() != null) {
                    for (TestCase tc : s.getCases()) {
                        if (tc.getStartUrl() != null && !tc.getStartUrl().isBlank()) {
                            promptUrl = com.studio.service.LiveDomScanner.extractFirstUrl(tc.getStartUrl());
                            if (promptUrl != null) break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        final String detectedTargetUrl = promptUrl;
        String domContext = liveDomJson;
        final AiConfig effectiveAiConfig = aiConfig;

        Thread thread = new Thread(() -> {
            try {
                String activeDomContext = domContext;
                // Nếu chưa có DOM context mà phát hiện URL hợp lệ -> Tự động quét Live DOM ngầm!
                if ((activeDomContext == null || activeDomContext.isBlank() || "[]".equals(activeDomContext)) && detectedTargetUrl != null) {
                    Platform.runLater(() -> updateThinkingStatus("Đang tự động quét cấu trúc Live DOM từ " + detectedTargetUrl + "..."));
                    try {
                        String autoScannedDom = domScanner.scanUrl(detectedTargetUrl, 10000);
                        if (autoScannedDom != null && !autoScannedDom.isBlank() && !"[]".equals(autoScannedDom)) {
                            activeDomContext = autoScannedDom;
                            liveDomJson = autoScannedDom; // Cache cho phiên làm việc
                            Platform.runLater(() -> ToastService.info("🌐 Đã tự động nạp Live DOM từ " + detectedTargetUrl));
                        }
                    } catch (Exception ex) {
                        System.err.println("[AiAssistant] Tự động quét DOM ngầm thất bại: " + ex.getMessage());
                    }
                }

                AiTestGeneratorService.GenerationResult result = aiGenerator.generateTestCases(
                        effectiveAiConfig, prompt, docsSnapshot, context, activeDomContext, currentSession.getMessages(),
                        status -> Platform.runLater(() -> updateThinkingStatus(status)),
                        activeSkill
                );

                Platform.runLater(() -> {
                    removeThinkingBubble();
                    sendBtn.setDisable(false);
                    sendBtn.setText("Gửi (Enter)");
                    generationProgress.setVisible(false);
                    generationProgress.setManaged(false);

                    AiChatMessage assistantMsg = new AiChatMessage("assistant", result.getSummary());
                    assistantMsg.setGeneratedCasesSnapshot(result.getCases());
                    assistantMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(assistantMsg));
                    currentSession.addMessage(assistantMsg);

                    // Persist updated history
                    try {
                        chatHistoryStore.saveSession(suiteDir, currentSession);
                    } catch (IOException ignored) {}

                    renderMessageBubble(assistantMsg, true);
                    updateTokenUsageDisplay();

                    resultSummaryLabel.setText(result.getSummary());

                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(result.getCases());

                    generatedCasesListView.getItems().setAll(currentGeneratedCases);
                    rawResponseViewer.setText(result.getRawResponse());

                    if (!currentGeneratedCases.isEmpty()) {
                        applyToSuiteBtn.setDisable(false);
                        generatedCasesListView.getSelectionModel().select(0);
                        ToastService.success("✨ Đã sinh " + currentGeneratedCases.size() + " test cases thành công!");
                    } else {
                        applyToSuiteBtn.setDisable(true);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    removeThinkingBubble();
                    sendBtn.setDisable(false);
                    sendBtn.setText("Gửi (Enter)");
                    generationProgress.setVisible(false);
                    generationProgress.setManaged(false);

                    AiChatMessage errorMsg = new AiChatMessage("assistant", "❌ Lỗi: " + ex.getMessage());
                    errorMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(errorMsg));
                    currentSession.addMessage(errorMsg);
                    renderMessageBubble(errorMsg, true);
                    updateTokenUsageDisplay();

                    ToastService.error("Lỗi AI: " + ex.getMessage());
                });
            }
        }, "AiAssistant-Worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void showThinkingBubble(String modelName) {
        removeThinkingBubble();

        VBox bubble = new VBox(8);
        bubble.setMaxWidth(520);
        bubble.getStyleClass().addAll("ai-chat-bubble", "ai-chat-bubble-assistant", "ai-thinking-bubble");

        // Header: Trợ lý AI • Đang suy nghĩ...
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label headerLabel = new Label("Trợ lý AI  •  Đang suy nghĩ...");
        headerLabel.getStyleClass().add("ai-chat-bubble-header");
        HBox.setHgrow(headerLabel, Priority.ALWAYS);

        // Animated dots indicator: ● ● ●
        HBox dotsBox = new HBox(4);
        dotsBox.setAlignment(Pos.CENTER_LEFT);
        Label dot1 = new Label("●");
        Label dot2 = new Label("●");
        Label dot3 = new Label("●");
        dot1.getStyleClass().add("ai-thinking-dot");
        dot2.getStyleClass().add("ai-thinking-dot");
        dot3.getStyleClass().add("ai-thinking-dot");
        dotsBox.getChildren().addAll(dot1, dot2, dot3);

        header.getChildren().addAll(headerLabel, dotsBox);

        // Body: Spinner + Dynamic Status Label
        HBox body = new HBox(10);
        body.setAlignment(Pos.CENTER_LEFT);
        body.setPadding(new Insets(4, 0, 4, 0));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        spinner.setMinSize(18, 18);
        spinner.getStyleClass().add("ai-thinking-spinner");

        thinkingStatusLabel = new Label("Đang kết nối tới mô hình " + (modelName != null ? modelName : "AI") + "...");
        thinkingStatusLabel.getStyleClass().add("ai-thinking-status-text");
        HBox.setHgrow(thinkingStatusLabel, Priority.ALWAYS);

        body.getChildren().addAll(spinner, thinkingStatusLabel);
        bubble.getChildren().addAll(header, body);

        thinkingBubbleWrapper = new HBox(bubble);
        thinkingBubbleWrapper.setAlignment(Pos.CENTER_LEFT);
        chatMessagesBox.getChildren().add(thinkingBubbleWrapper);
        Platform.runLater(() -> chatScroll.setVvalue(1.0));

        // Animated progression stages
        String[] stages = new String[]{
                "Đang kết nối tới mô hình " + (modelName != null ? modelName : "AI") + "...",
                "Đang phân tích yêu cầu kiểm thử & ngữ cảnh tài liệu...",
                "Đang thiết kế kịch bản Playwright & Assertions kiểm thử...",
                "Đang tổng hợp các bước thực thi (Test Steps) & Dữ liệu...",
                "Đang hoàn thiện và định dạng mã nguồn runnable..."
        };

        final int[] stageIdx = {0};
        final int[] dotStep = {0};

        thinkingTimeline = new Timeline(new KeyFrame(Duration.millis(500), ev -> {
            dotStep[0]++;
            int activeDot = dotStep[0] % 3;
            dot1.setOpacity(activeDot == 0 ? 1.0 : 0.3);
            dot2.setOpacity(activeDot == 1 ? 1.0 : 0.3);
            dot3.setOpacity(activeDot == 2 ? 1.0 : 0.3);

            // Rotate text every ~2.5s (every 5 ticks of 500ms)
            if (dotStep[0] % 5 == 0 && stageIdx[0] < stages.length - 1) {
                stageIdx[0]++;
                if (thinkingStatusLabel != null) {
                    thinkingStatusLabel.setText(stages[stageIdx[0]]);
                }
            }
        }));
        thinkingTimeline.setCycleCount(Animation.INDEFINITE);
        thinkingTimeline.play();
    }

    private void updateThinkingStatus(String text) {
        if (thinkingStatusLabel != null && text != null && !text.isBlank()) {
            thinkingStatusLabel.setText(text);
        }
    }

    private void removeThinkingBubble() {
        if (thinkingTimeline != null) {
            thinkingTimeline.stop();
            thinkingTimeline = null;
        }
        if (thinkingBubbleWrapper != null) {
            chatMessagesBox.getChildren().remove(thinkingBubbleWrapper);
            thinkingBubbleWrapper = null;
        }
        thinkingStatusLabel = null;
    }

    private void showCaseDetail(AiGeneratedCase c) {
        if (c == null) {
            previewCaseNameLabel.setText("—");
            previewCaseUrlLabel.setText("");
            previewScriptEditor.clear();
            previewAssertsListView.getItems().clear();
            previewAssertsTab.setText("Assertions (0)");
            if (previewStepsListView != null) previewStepsListView.getItems().clear();
            if (previewStepsTab != null) previewStepsTab.setText("Các bước kiểm thử (0)");
            if (previewTestDataListView != null) previewTestDataListView.getItems().clear();
            if (previewTestDataTab != null) previewTestDataTab.setText("Dữ liệu kiểm thử (0)");
            return;
        }

        previewCaseNameLabel.setText(c.getName());
        previewCaseUrlLabel.setText(c.getStartUrl().isBlank() ? "" : "URL: " + c.getStartUrl());
        previewScriptEditor.setText(c.getScriptCode());

        previewAssertsListView.getItems().clear();
        for (Assertion a : c.getAssertions()) {
            String loc = a.getLocator() != null && !a.getLocator().isBlank() ? " | " + a.getLocator() : "";
            String val = a.getValue() != null && !a.getValue().isBlank() ? " | " + a.getValue() : "";
            previewAssertsListView.getItems().add(a.getType() + loc + val);
        }
        previewAssertsTab.setText("Assertions (" + c.getAssertions().size() + ")");

        if (previewStepsListView != null) {
            previewStepsListView.getItems().clear();
            for (com.studio.model.TestStep step : c.getTestSteps()) {
                previewStepsListView.getItems().add(step.toString());
            }
            if (previewStepsTab != null) {
                previewStepsTab.setText("Các bước (" + c.getTestSteps().size() + ")");
            }
        }

        if (previewTestDataListView != null) {
            previewTestDataListView.getItems().clear();
            for (com.studio.model.TestDataItem item : c.getTestDataSets()) {
                previewTestDataListView.getItems().add(item.getScenario() + " -> " + item.getParameters() + " [" + item.getExpectedOutcome() + "]");
            }
            if (previewTestDataTab != null) {
                previewTestDataTab.setText("Dữ liệu (" + c.getTestDataSets().size() + ")");
            }
        }
    }

    @FXML
    public void scanLiveDom() {
        String prompt = promptInputArea != null ? promptInputArea.getText() : "";
        String targetUrl = extractUrl(prompt);
        if (targetUrl == null || targetUrl.isBlank()) {
            String defaultUrl = "https://example.com";
            Path suiteDir = getSelectedSuiteDir();
            if (suiteDir != null) {
                try {
                    TestSuite s = projectStore.loadAndMigrateSuite(suiteDir);
                    if (s != null && s.getCases() != null) {
                        for (TestCase tc : s.getCases()) {
                            if (tc.getStartUrl() != null && !tc.getStartUrl().isBlank()) {
                                defaultUrl = tc.getStartUrl();
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            TextInputDialog dialog = new TextInputDialog(defaultUrl);
            dialog.setTitle("Quét Live DOM Trang Web");
            dialog.setHeaderText("Nhập URL trang web cần quét các phần tử DOM tương tác:");
            dialog.setContentText("URL:");
            DialogTheme.apply(dialog);
            Optional<String> res = dialog.showAndWait();
            if (res.isPresent() && !res.get().isBlank()) {
                targetUrl = res.get().trim();
            } else {
                return;
            }
        }

        final String finalUrl = targetUrl;
        ToastService.info("🌐 Đang quét cấu trúc DOM tương tác từ " + finalUrl + "...");
        if (generationProgress != null) {
            generationProgress.setVisible(true);
            generationProgress.setManaged(true);
        }

        Thread thread = new Thread(() -> {
            try {
                String dom = domScanner.scanUrl(finalUrl, 10000);
                Platform.runLater(() -> {
                    if (generationProgress != null) {
                        generationProgress.setVisible(false);
                        generationProgress.setManaged(false);
                    }
                    if (dom != null && !dom.equals("[]") && !dom.isBlank()) {
                        liveDomJson = dom;
                        ToastService.success("✨ Đã quét và nạp ngữ cảnh DOM cho " + finalUrl + "!");
                    } else {
                        ToastService.warn("Không trích xuất được phần tử tương tác nào từ URL đã cho.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (generationProgress != null) {
                        generationProgress.setVisible(false);
                        generationProgress.setManaged(false);
                    }
                    ToastService.error("Lỗi khi quét DOM: " + e.getMessage());
                });
            }
        }, "DomScanner-Worker");
        thread.setDaemon(true);
        thread.start();
    }

    private String extractUrl(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+").matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    @FXML
    public void applyCasesToSelectedSuite() {
        String group = targetGroupCombo.getValue();
        String suite = targetSuiteCombo.getValue();

        if (group == null || suite == null || group.isBlank() || suite.isBlank()) {
            ToastService.warn("⚠️ Vui lòng chọn Nhóm và Suite đích trước khi nhập.");
            return;
        }

        Path suiteDir = StudioPaths.getProjectDir().resolve(group).resolve(suite);
        try {
            Files.createDirectories(suiteDir);
        } catch (IOException e) {
            ToastService.error("Không thể tạo thư mục Suite: " + e.getMessage());
            return;
        }

        // Sync edited script back to current item if user changed it in editor
        AiGeneratedCase selected = generatedCasesListView.getSelectionModel().getSelectedItem();
        if (selected != null && previewScriptEditor.getText() != null) {
            selected.setScriptCode(previewScriptEditor.getText());
        }

        try {
            // Capture snapshot of suite files before applying
            String suiteSnapshotJson = chatHistoryStore.createSuiteSnapshot(suiteDir);

            List<TestCase> applied = aiGenerator.applyCasesToSuite(suiteDir, currentGeneratedCases, projectStore);

            // Record snapshot on the latest assistant message so user can undo it later
            List<AiChatMessage> msgs = currentSession.getMessages();
            for (int i = msgs.size() - 1; i >= 0; i--) {
                AiChatMessage m = msgs.get(i);
                if ("assistant".equalsIgnoreCase(m.getRole())) {
                    m.setSuiteSnapshotJson(suiteSnapshotJson);
                    m.setSuiteSnapshotDir(suiteDir.toString());
                    m.setAppliedToSuite(true);
                    break;
                }
            }

            chatHistoryStore.saveSession(suiteDir, currentSession);
            renderChatHistory();

            ToastService.success("⚡ Đã nhập " + applied.size() + " test cases vào suite '" + suite + "'! Bạn có thể bấm 'Hoàn tác' trên tin nhắn bất kỳ lúc nào.");

            // Re-open workspace for this suite
            SuiteWorkspacePageController ctrl = ViewNavigator.navigateToWorkspace(group, suite, suiteDir);
            if (ctrl != null) {
                TestSuite reloadedSuite = projectStore.loadAndMigrateSuite(suiteDir);
                ctrl.loadSuite(reloadedSuite, suiteDir, null);
            }
        } catch (Exception e) {
            ToastService.error("Lỗi khi nhập test cases vào Suite: " + e.getMessage());
        }
    }

    private void renderMessageBubble(AiChatMessage msg, boolean scrollToBottom) {
        String role = msg.getRole();
        VBox bubble = new VBox(6);
        bubble.setMaxWidth(520);
        bubble.getStyleClass().add("ai-chat-bubble");
        bubble.getStyleClass().add("user".equalsIgnoreCase(role) ? "ai-chat-bubble-user" : "ai-chat-bubble-assistant");

        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(msg.getTimestamp()), ZoneId.systemDefault());
        String formattedTime = time.format(DateTimeFormatter.ofPattern("HH:mm"));

        // Header Row: Label + Spacer + Copy Button + Undo Button
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        String title = ("user".equalsIgnoreCase(role) ? "Bạn" : "Trợ lý AI") + "  •  " + formattedTime + "  •  ~" + msg.getEstimatedTokens() + " tokens";
        Label headerLabel = new Label(title);
        headerLabel.getStyleClass().add("ai-chat-bubble-header");
        HBox.setHgrow(headerLabel, Priority.ALWAYS);

        // Copy Button
        Button copyBtn = new Button("Sao chép");
        copyBtn.getStyleClass().add("chat-action-btn");
        UiIcons.setGraphic(copyBtn, UiIcons.COPY, 10);
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(msg.getContent());
            Clipboard.getSystemClipboard().setContent(content);
            ToastService.success("Đã sao chép nội dung tin nhắn!");
        });

        // Undo Button
        Button undoBtn = new Button("Hoàn tác");
        undoBtn.getStyleClass().addAll("chat-action-btn", "chat-action-btn-danger");
        UiIcons.setGraphic(undoBtn, UiIcons.UNDO, 10);
        undoBtn.setOnAction(e -> handleUndoMessage(msg));

        header.getChildren().addAll(headerLabel, copyBtn, undoBtn);

        // Content
        Label contentLabel = new Label(msg.getContent());
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("ai-chat-bubble-content");

        bubble.getChildren().addAll(header, contentLabel);

        // If applied to suite, show indicator badge
        if (msg.isAppliedToSuite()) {
            HBox appliedBadge = new HBox(4);
            appliedBadge.setAlignment(Pos.CENTER_LEFT);
            appliedBadge.getStyleClass().add("ai-chat-applied-badge");
            Label appliedLbl = new Label("✓ Đã áp dụng vào Suite (Bấm Hoàn tác để khôi phục)");
            appliedLbl.getStyleClass().add("ai-chat-applied-label");
            appliedBadge.getChildren().add(appliedLbl);
            bubble.getChildren().add(appliedBadge);
        }

        HBox wrapper = new HBox(bubble);
        if ("user".equalsIgnoreCase(role)) {
            wrapper.setAlignment(Pos.CENTER_RIGHT);
        } else {
            wrapper.setAlignment(Pos.CENTER_LEFT);
        }

        chatMessagesBox.getChildren().add(wrapper);
        if (scrollToBottom) {
            Platform.runLater(() -> chatScroll.setVvalue(1.0));
        }
    }

    private void handleUndoMessage(AiChatMessage targetMsg) {
        if (targetMsg == null) return;

        // 1. If applied to suite, ask for confirmation to rollback files
        if (targetMsg.isAppliedToSuite() && targetMsg.getSuiteSnapshotJson() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xác nhận hoàn tác Suite");
            alert.setHeaderText("Hoàn tác toàn bộ thay đổi trong Suite");
            alert.setContentText("Tin nhắn này đã thêm/sửa test cases trên đĩa. Bạn có muốn hoàn tác và khôi phục các file của Suite về trạng thái ban đầu không?");
            DialogTheme.apply(alert);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }

            Path suiteDir = getSelectedSuiteDir();
            try {
                chatHistoryStore.rollbackSuiteSnapshot(suiteDir, targetMsg.getSuiteSnapshotJson());
                ToastService.success("⚡ Đã khôi phục Suite về trạng thái trước khi áp dụng!");
            } catch (Exception ex) {
                ToastService.error("Lỗi khi khôi phục Suite: " + ex.getMessage());
            }
        }

        // 2. Remove the message and its pair from the session
        List<AiChatMessage> msgs = currentSession.getMessages();
        int idx = msgs.indexOf(targetMsg);
        if (idx >= 0) {
            if ("user".equalsIgnoreCase(targetMsg.getRole())) {
                // If user message is undone, also remove the subsequent assistant reply if present
                if (idx + 1 < msgs.size() && "assistant".equalsIgnoreCase(msgs.get(idx + 1).getRole())) {
                    msgs.remove(idx + 1);
                }
                msgs.remove(idx);
            } else {
                // If assistant message is undone, remove this assistant message and prior user prompt
                msgs.remove(idx);
                if (idx - 1 >= 0 && "user".equalsIgnoreCase(msgs.get(idx - 1).getRole())) {
                    msgs.remove(idx - 1);
                }
            }
        }

        // 3. Persist and re-render
        Path suiteDir = getSelectedSuiteDir();
        try {
            chatHistoryStore.saveSession(suiteDir, currentSession);
        } catch (IOException ignored) {}

        renderChatHistory();
        updateTokenUsageDisplay();
        ToastService.info("Đã hoàn tác tin nhắn thành công.");
    }
}
