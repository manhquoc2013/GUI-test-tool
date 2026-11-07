package com.studio.controller;

import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestGroup;
import com.studio.model.TestSuite;
import com.studio.service.AiTestGeneratorService;
import com.studio.service.DocumentParserService;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.store.SettingsStore;
import com.studio.util.StudioPaths;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the dedicated AI Assistant screen.
 */
public class AiAssistantPageController implements Initializable {

    @FXML private Label modelIndicatorLabel;
    @FXML private Button settingsBtn;
    @FXML private Button clearChatBtn;
    @FXML private ComboBox<String> targetGroupCombo;
    @FXML private ComboBox<String> targetSuiteCombo;
    @FXML private Button refreshTargetsBtn;
    @FXML private ScrollPane chatScroll;
    @FXML private VBox chatMessagesBox;
    @FXML private VBox attachedFilesBox;
    @FXML private FlowPane attachedChipsPane;
    @FXML private TextArea promptInputArea;
    @FXML private Button attachFileBtn;
    @FXML private Button clearAttachmentsBtn;
    @FXML private ProgressIndicator generationProgress;
    @FXML private Button sendBtn;
    @FXML private Label resultSummaryLabel;
    @FXML private Button applyToSuiteBtn;
    @FXML private ListView<AiGeneratedCase> generatedCasesListView;
    @FXML private Label previewCaseNameLabel;
    @FXML private Label previewCaseUrlLabel;
    @FXML private TabPane detailTabPane;
    @FXML private TextArea previewScriptEditor;
    @FXML private Tab previewAssertsTab;
    @FXML private ListView<String> previewAssertsListView;
    @FXML private TextArea rawResponseViewer;

    private final SettingsStore settingsStore = new SettingsStore();
    private final ProjectStore projectStore = new ProjectStore();
    private final DocumentParserService documentParser = new DocumentParserService();
    private final AiTestGeneratorService aiGenerator = new AiTestGeneratorService();

    private final List<DocumentParserService.ParsedDocument> attachedDocuments = new ArrayList<>();
    private final List<AiGeneratedCase> currentGeneratedCases = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refreshModelIndicator();
        reloadTargets();

        targetGroupCombo.setOnAction(e -> onGroupSelected());

        // Setup case list cell factory
        generatedCasesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AiGeneratedCase item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText("🎯 " + item.getName() + " (" + item.getAssertions().size() + " asserts)");
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

        // Add welcome message in chat
        addChatBubble("assistant", "Xin chào! Tôi là Trợ lý AI của Playwright Automation Studio.\n\n"
                + "Bạn có thể gửi yêu cầu viết test case, hoặc đính kèm tài liệu yêu cầu (.txt, .md, Word .docx, PDF .pdf) hoặc ảnh chụp màn hình UI (.png, .jpg) để tôi tự động phân tích và sinh kịch bản Playwright runnable kèm các Assertions kiểm thử!");
    }

    public void refreshModelIndicator() {
        try {
            SettingsStore.Settings s = settingsStore.load();
            modelIndicatorLabel.setText("Mô hình: " + s.getAiConfig().getEffectiveModel());
        } catch (Exception ignored) {
            modelIndicatorLabel.setText("Mô hình: gpt-4o");
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

    @FXML
    public void openSettings() {
        AiSettingsDialog.show(settingsStore);
        refreshModelIndicator();
    }

    @FXML
    public void clearChat() {
        chatMessagesBox.getChildren().clear();
        addChatBubble("assistant", "Đoạn hội thoại đã được xóa. Hãy bắt đầu yêu cầu mới!");
    }

    @FXML
    public void attachFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tài liệu hoặc ảnh chụp giao diện");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả tài liệu hỗ trợ", "*.txt", "*.md", "*.json", "*.csv", "*.docx", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("Văn bản & Specs (*.txt, *.md, *.docx, *.pdf)", "*.txt", "*.md", "*.json", "*.csv", "*.docx", "*.pdf"),
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
        chip.setStyle("-fx-background-color: -color-surface; -fx-padding: 3px 8px; -fx-background-radius: 12px; -fx-border-color: -color-surface-hover; -fx-border-radius: 12px;");

        Label label = new Label((doc.isImage() ? "🖼️ " : "📄 ") + doc.getFileName());
        label.setStyle("-fx-font-size: 11px;");

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0 2px; -fx-text-fill: -color-subtext; -fx-cursor: hand; -fx-font-size: 10px;");
        removeBtn.setOnAction(e -> {
            attachedChipsPane.getChildren().remove(chip);
            attachedDocuments.remove(doc);
            updateAttachmentsVisibility();
        });

        chip.getChildren().addAll(label, removeBtn);
        attachedChipsPane.getChildren().add(chip);
    }

    @FXML
    public void applyQuickLogin() {
        promptInputArea.setText("Viết kịch bản kiểm thử cho trang đăng nhập: Nhập email hợp lệ và mật khẩu, bấm nút Đăng nhập, xác minh người dùng được chuyển hướng vào Dashboard và hiển thị lời chào.");
    }

    @FXML
    public void applyQuickCart() {
        promptInputArea.setText("Viết kịch bản kiểm thử quy trình mua hàng: Thêm sản phẩm vào giỏ, kiểm tra số lượng và tổng tiền cập nhật chính xác, tiến hành thanh toán.");
    }

    @FXML
    public void applyQuickDoc() {
        promptInputArea.setText("Phân tích kỹ các yêu cầu trong tài liệu đính kèm và sinh bộ Test Cases bao quát các trường hợp chính (Happy path, Negative path, và Edge cases) kèm đầy đủ Assertions kiểm thử.");
    }

    @FXML
    public void sendPrompt() {
        String prompt = promptInputArea.getText() != null ? promptInputArea.getText().trim() : "";
        if (prompt.isEmpty() && attachedDocuments.isEmpty()) {
            ToastService.warn("⚠️ Vui lòng nhập nội dung yêu cầu hoặc đính kèm tài liệu.");
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
            ToastService.warn("⚠️ Chưa có OpenAI API Key. Vui lòng mở Cài đặt OpenAI để điền API Key.");
            openSettings();
            return;
        }

        // Add user bubble in chat
        StringBuilder userChatText = new StringBuilder(prompt);
        if (!attachedDocuments.isEmpty()) {
            userChatText.append("\n\n📎 Đã đính kèm: ");
            for (var d : attachedDocuments) {
                userChatText.append(d.getFileName()).append(" ");
            }
        }
        addChatBubble("user", userChatText.toString());

        // Snapshot attached docs and clear input
        List<DocumentParserService.ParsedDocument> docsSnapshot = new ArrayList<>(attachedDocuments);
        promptInputArea.clear();
        clearAttachments();

        sendBtn.setDisable(true);
        generationProgress.setVisible(true);
        generationProgress.setManaged(true);

        String context = "Nhóm đích: " + targetGroupCombo.getValue() + ", Suite đích: " + targetSuiteCombo.getValue();

        Thread thread = new Thread(() -> {
            try {
                AiTestGeneratorService.GenerationResult result = aiGenerator.generateTestCases(
                        aiConfig, prompt, docsSnapshot, context
                );

                Platform.runLater(() -> {
                    sendBtn.setDisable(false);
                    generationProgress.setVisible(false);
                    generationProgress.setManaged(false);

                    addChatBubble("assistant", result.getSummary() + "\n\n(Đã sinh " + result.getCases().size() + " test cases. Xem chi tiết ở bảng bên phải)");
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
                    sendBtn.setDisable(false);
                    generationProgress.setVisible(false);
                    generationProgress.setManaged(false);
                    addChatBubble("assistant", "❌ Lỗi: " + ex.getMessage());
                    ToastService.error("Lỗi AI: " + ex.getMessage());
                });
            }
        }, "AiAssistant-Worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void showCaseDetail(AiGeneratedCase c) {
        if (c == null) {
            previewCaseNameLabel.setText("—");
            previewCaseUrlLabel.setText("");
            previewScriptEditor.clear();
            previewAssertsListView.getItems().clear();
            previewAssertsTab.setText("☑️ Assertions (0)");
            return;
        }

        previewCaseNameLabel.setText("🎯 " + c.getName());
        previewCaseUrlLabel.setText("🔗 " + (c.getStartUrl().isBlank() ? "(Chưa có URL)" : c.getStartUrl()));
        previewScriptEditor.setText(c.getScriptCode());

        previewAssertsListView.getItems().clear();
        for (Assertion a : c.getAssertions()) {
            String loc = a.getLocator() != null && !a.getLocator().isBlank() ? " | " + a.getLocator() : "";
            String val = a.getValue() != null && !a.getValue().isBlank() ? " | " + a.getValue() : "";
            previewAssertsListView.getItems().add(a.getType() + loc + val);
        }
        previewAssertsTab.setText("☑️ Assertions (" + c.getAssertions().size() + ")");
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
        if (!Files.exists(suiteDir)) {
            ToastService.error("Không tìm thấy thư mục Suite: " + suiteDir);
            return;
        }

        // Sync edited script back to current item if user changed it in editor
        AiGeneratedCase selected = generatedCasesListView.getSelectionModel().getSelectedItem();
        if (selected != null && previewScriptEditor.getText() != null) {
            selected.setScriptCode(previewScriptEditor.getText());
        }

        try {
            List<TestCase> applied = aiGenerator.applyCasesToSuite(suiteDir, currentGeneratedCases, projectStore);
            ToastService.success("⚡ Đã nhập " + applied.size() + " test cases vào suite '" + suite + "'!");

            // Re-open workspace for this suite
            ViewNavigator.navigateToWorkspace(group, suite, suiteDir);
        } catch (Exception e) {
            ToastService.error("Lỗi khi nhập test cases vào Suite: " + e.getMessage());
        }
    }

    private void addChatBubble(String role, String text) {
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(500);

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Label headerLabel = new Label(("user".equals(role) ? "👤 Bạn" : "🤖 Trợ lý AI") + "  •  " + time);
        headerLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-subtext;");

        Label contentLabel = new Label(text);
        contentLabel.setWrapText(true);

        bubble.getChildren().addAll(headerLabel, contentLabel);

        HBox wrapper = new HBox(bubble);
        if ("user".equals(role)) {
            wrapper.setAlignment(Pos.CENTER_RIGHT);
            bubble.setStyle("-fx-background-color: -color-primary-dim; -fx-padding: 10px 14px; -fx-background-radius: 12px 12px 2px 12px;");
        } else {
            wrapper.setAlignment(Pos.CENTER_LEFT);
            bubble.setStyle("-fx-background-color: -color-surface; -fx-padding: 10px 14px; -fx-background-radius: 12px 12px 12px 2px; -fx-border-color: -color-surface-hover; -fx-border-radius: 12px 12px 12px 2px;");
        }

        chatMessagesBox.getChildren().add(wrapper);
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }
}

