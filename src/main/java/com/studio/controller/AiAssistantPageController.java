package com.studio.controller;

import com.studio.model.*;
import com.studio.service.AiLoggingService;
import com.studio.service.AiTestGeneratorService;
import com.studio.service.AiTokenOptimizer;
import com.studio.service.DocumentParserService;
import com.studio.service.TestCaseExcelExportService;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import javafx.beans.property.SimpleStringProperty;

/**
 * Controller for the dedicated AI Assistant screen.
 * Supports persistent chat history, 1-click message copy, full undo/rollback of
 * generated test cases and suite file changes, and token usage optimization.
 */
public class AiAssistantPageController implements Initializable {

    @FXML
    private Button toggleSidebarBtn;
    @FXML
    private Button newChatToolbarBtn;
    @FXML
    private VBox historySidebar;
    @FXML
    private Separator sidebarSeparator;
    @FXML
    private Button newChatBtn;
    @FXML
    private TextField sessionSearchField;
    @FXML
    private ListView<AiChatSessionMeta> sessionsListView;

    @FXML
    private Label tokenUsageBadge;
    @FXML
    private Label modelIndicatorLabel;
    @FXML
    private Button settingsBtn;
    @FXML
    private Button clearChatBtn;
    @FXML
    private ComboBox<String> targetGroupCombo;
    @FXML
    private ComboBox<String> targetSuiteCombo;
    @FXML
    private Button refreshTargetsBtn;
    @FXML
    private ComboBox<com.studio.model.AiSkill> skillComboBox;
    @FXML
    private ScrollPane chatScroll;
    @FXML
    private VBox chatMessagesBox;
    @FXML
    private VBox attachedFilesBox;
    @FXML
    private FlowPane attachedChipsPane;
    @FXML
    private TextArea promptInputArea;
    @FXML
    private Button attachFileBtn;
    @FXML
    private Button scanDomBtn;
    @FXML
    private Button clearAttachmentsBtn;
    @FXML
    private CheckBox autoGenerateScriptCheck;
    @FXML
    private ProgressIndicator generationProgress;
    @FXML
    private Button sendBtn;
    @FXML
    private Label resultSummaryLabel;
    @FXML
    private MenuButton exportCasesMenuBtn;
    @FXML
    private Button applyToSuiteBtn;
    @FXML
    private ListView<AiGeneratedCase> generatedCasesListView;
    @FXML
    private Label previewCaseNameLabel;
    @FXML
    private Label previewCaseUrlLabel;
    @FXML
    private TabPane detailTabPane;
    @FXML
    private Tab previewStepsTab;
    @FXML
    private ListView<String> previewStepsListView;
    @FXML
    private TextArea previewScriptEditor;
    @FXML
    private Tab previewAssertsTab;
    @FXML
    private ListView<String> previewAssertsListView;
    @FXML
    private Tab previewTestDataTab;
    @FXML
    private ListView<String> previewTestDataListView;
    @FXML
    private TextArea rawResponseViewer;

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
    private final ObservableList<AiChatSessionMeta> allSessions = FXCollections.observableArrayList();
    private final FilteredList<AiChatSessionMeta> filteredSessions = new FilteredList<>(allSessions, p -> true);
    private boolean isSwitchingSession = false;
    private boolean isReloadingTargets = false;
    private String liveDomJson = null;

    private HBox thinkingBubbleWrapper = null;
    private Timeline thinkingTimeline = null;
    private Label thinkingStatusLabel = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refreshModelIndicator();
        setupSessionsSidebar();
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
        if (exportCasesMenuBtn != null) {
            UiIcons.setGraphic(exportCasesMenuBtn, UiIcons.DOWNLOAD, 12);
            UiTooltips.install(exportCasesMenuBtn, "Tải về bộ Test Case đã sinh (Excel .xlsx, Markdown, JSON)");
        }
        if (autoGenerateScriptCheck != null) {
            UiTooltips.install(autoGenerateScriptCheck, "Tự động sinh mã Playwright Java test script khi có link trang web cần test");
        }
        if (promptInputArea != null && autoGenerateScriptCheck != null) {
            promptInputArea.textProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && (newV.contains("http://") || newV.contains("https://"))) {
                    autoGenerateScriptCheck.setSelected(true);
                }
            });
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

        // Key listener for prompt area: Ctrl+Enter (send) and Ctrl+H (toggle history sidebar)
        promptInputArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && (event.isControlDown() || event.isMetaDown())) {
                sendPrompt();
                event.consume();
            } else if (event.getCode() == KeyCode.H && (event.isControlDown() || event.isMetaDown())) {
                toggleHistorySidebar();
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
        if (isReloadingTargets) return;
        isReloadingTargets = true;
        try {
            String prevGroup = targetGroupCombo.getValue();
            String prevSuite = targetSuiteCombo.getValue();
            String activeSessionId = (currentSession != null) ? currentSession.getId() : null;
            Path prevSuiteDir = getSelectedSuiteDir();

            Path projDir = StudioPaths.getProjectDir();
            List<String> groups = new ArrayList<>();
            if (Files.exists(projDir)) {
                try (var stream = Files.list(projDir)) {
                    stream.filter(Files::isDirectory)
                          .map(d -> d.getFileName().toString())
                          .sorted(String.CASE_INSENSITIVE_ORDER)
                          .forEach(groups::add);
                } catch (IOException ignored) {}
            }

            if (!groups.equals(new ArrayList<>(targetGroupCombo.getItems()))) {
                targetGroupCombo.getItems().setAll(groups);
            }

            String targetGroup = null;
            if (prevGroup != null && groups.contains(prevGroup)) {
                targetGroup = prevGroup;
            } else if (!groups.isEmpty()) {
                targetGroup = groups.get(0);
            }
            targetGroupCombo.setValue(targetGroup);

            List<String> suites = new ArrayList<>();
            if (targetGroup != null && !targetGroup.isBlank()) {
                Path groupDir = projDir.resolve(targetGroup);
                if (Files.exists(groupDir)) {
                    try (var stream = Files.list(groupDir)) {
                        stream.filter(Files::isDirectory)
                              .map(d -> d.getFileName().toString())
                              .sorted(String.CASE_INSENSITIVE_ORDER)
                              .forEach(suites::add);
                    } catch (IOException ignored) {}
                }
            }

            if (!suites.equals(new ArrayList<>(targetSuiteCombo.getItems()))) {
                targetSuiteCombo.getItems().setAll(suites);
            }

            String targetSuite = null;
            if (prevSuite != null && suites.contains(prevSuite)) {
                targetSuite = prevSuite;
            } else if (!suites.isEmpty()) {
                targetSuite = suites.get(0);
            }
            targetSuiteCombo.setValue(targetSuite);

            Path newSuiteDir = getSelectedSuiteDir();
            boolean suiteSame = (prevSuiteDir == null && newSuiteDir == null) ||
                                (prevSuiteDir != null && prevSuiteDir.equals(newSuiteDir));

            if (suiteSame && currentSession != null && (!currentSession.getMessages().isEmpty() || activeSessionId != null)) {
                refreshSessionsList(activeSessionId);
            } else {
                loadChatHistoryForSuiteInternal(activeSessionId);
            }
        } finally {
            isReloadingTargets = false;
        }
    }

    private void onGroupSelected() {
        if (isReloadingTargets) return;
        String groupName = targetGroupCombo.getValue();
        if (groupName == null || groupName.isBlank()) {
            targetSuiteCombo.getItems().clear();
            loadChatHistoryForSuite();
            return;
        }

        List<String> suites = new ArrayList<>();
        Path groupDir = StudioPaths.getProjectDir().resolve(groupName);
        if (Files.exists(groupDir)) {
            try (var stream = Files.list(groupDir)) {
                stream.filter(Files::isDirectory)
                      .map(d -> d.getFileName().toString())
                      .sorted(String.CASE_INSENSITIVE_ORDER)
                      .forEach(suites::add);
            } catch (IOException ignored) {}
        }
        targetSuiteCombo.getItems().setAll(suites);
        if (!suites.isEmpty()) {
            targetSuiteCombo.getSelectionModel().select(0);
        } else {
            loadChatHistoryForSuite();
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
        if (isReloadingTargets) return;
        String preserveSessionId = (currentSession != null) ? currentSession.getId() : null;
        loadChatHistoryForSuiteInternal(preserveSessionId);
    }

    private void loadChatHistoryForSuiteInternal(String preferredSessionId) {
        Path suiteDir = getSelectedSuiteDir();
        refreshSessionsList(preferredSessionId);
        if (sessionsListView != null && sessionsListView.getSelectionModel().getSelectedItem() != null) {
            currentSession = chatHistoryStore.loadSession(suiteDir, sessionsListView.getSelectionModel().getSelectedItem().getId());
        } else {
            currentSession = chatHistoryStore.loadSession(suiteDir, preferredSessionId);
        }
        renderChatHistory();
        updateTokenUsageDisplay();
    }

    private void setupSessionsSidebar() {
        if (toggleSidebarBtn != null) {
            UiIcons.setGraphic(toggleSidebarBtn, UiIcons.FOLDER, 12);
            UiTooltips.install(toggleSidebarBtn, "Đóng/mở thanh lịch sử cuộc trò chuyện (Ctrl+H / Cmd+H)");
        }
        if (newChatBtn != null) {
            UiIcons.setGraphic(newChatBtn, UiIcons.PLUS, 12);
            UiTooltips.install(newChatBtn, "Bắt đầu cuộc trò chuyện mới");
        }
        if (newChatToolbarBtn != null) {
            UiIcons.setGraphic(newChatToolbarBtn, UiIcons.PLUS, 12);
            UiTooltips.install(newChatToolbarBtn, "Bắt đầu cuộc trò chuyện mới");
        }

        if (sessionsListView != null) {
            sessionsListView.setItems(filteredSessions);
            sessionsListView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(AiChatSessionMeta item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                        setOnMouseClicked(null);
                    } else {
                        VBox box = new VBox(2);
                        box.setAlignment(Pos.CENTER_LEFT);

                        HBox topRow = new HBox(6);
                        topRow.setAlignment(Pos.CENTER_LEFT);

                        if (item.isPinned()) {
                            Label pinIcon = new Label("📌");
                            pinIcon.setStyle("-fx-font-size: 10px;");
                            topRow.getChildren().add(pinIcon);
                        }

                        Label titleLabel = new Label(item.getTitle());
                        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                        titleLabel.setMaxWidth(160);
                        topRow.getChildren().add(titleLabel);

                        HBox bottomRow = new HBox(6);
                        bottomRow.setAlignment(Pos.CENTER_LEFT);

                        String timeStr = formatSessionTime(item.getUpdatedAt());
                        Label timeLabel = new Label(timeStr + " • " + item.getMessageCount() + " tin");
                        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-text-base-color; -fx-opacity: 0.6;");
                        bottomRow.getChildren().add(timeLabel);

                        box.getChildren().addAll(topRow, bottomRow);
                        setGraphic(box);
                        setText(null);

                        setOnMouseClicked(event -> {
                            if (event.getClickCount() == 2 && !isEmpty() && getItem() != null) {
                                handleRenameSession(getItem());
                            }
                        });

                        ContextMenu menu = new ContextMenu();
                        MenuItem pinItem = new MenuItem(item.isPinned() ? "Bỏ ghim" : "Ghim lên đầu");
                        pinItem.setOnAction(e -> handleTogglePin(item));

                        MenuItem renameItem = new MenuItem("Đổi tên...");
                        renameItem.setOnAction(e -> handleRenameSession(item));

                        MenuItem deleteItem = new MenuItem("Xóa cuộc trò chuyện");
                        deleteItem.setStyle("-fx-text-fill: #f7768e;");
                        deleteItem.setOnAction(e -> handleDeleteSession(item));

                        menu.getItems().addAll(pinItem, renameItem, new SeparatorMenuItem(), deleteItem);
                        setContextMenu(menu);
                    }
                }
            });

            sessionsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !isSwitchingSession) {
                    switchToSession(newVal.getId());
                }
            });
        }

        if (sessionSearchField != null) {
            sessionSearchField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    sessionSearchField.clear();
                }
            });
            sessionSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                String query = newVal != null ? newVal.trim().toLowerCase() : "";
                filteredSessions.setPredicate(session -> {
                    if (query.isEmpty()) return true;
                    return session.getTitle() != null && session.getTitle().toLowerCase().contains(query);
                });
            });
        }
    }

    public void refreshSessionsList(String selectSessionId) {
        Path suiteDir = getSelectedSuiteDir();
        List<AiChatSessionMeta> list = chatHistoryStore.listSessions(suiteDir);
        allSessions.setAll(list);

        if (sessionsListView != null) {
            isSwitchingSession = true;
            try {
                String targetId = selectSessionId != null ? selectSessionId : (currentSession != null ? currentSession.getId() : null);
                if (targetId != null) {
                    for (AiChatSessionMeta meta : allSessions) {
                        if (targetId.equals(meta.getId())) {
                            sessionsListView.getSelectionModel().select(meta);
                            return;
                        }
                    }
                }
                if (!allSessions.isEmpty()) {
                    sessionsListView.getSelectionModel().select(0);
                }
            } finally {
                isSwitchingSession = false;
            }
        }
    }

    private void switchToSession(String sessionId) {
        if (isSwitchingSession || sessionId == null) return;
        if (currentSession != null && sessionId.equals(currentSession.getId())) return;

        isSwitchingSession = true;
        try {
            Path suiteDir = getSelectedSuiteDir();
            currentSession = chatHistoryStore.loadSession(suiteDir, sessionId);
            renderChatHistory();
            updateTokenUsageDisplay();
        } finally {
            isSwitchingSession = false;
        }
    }

    @FXML
    public void toggleHistorySidebar() {
        if (historySidebar == null) return;
        boolean visible = !historySidebar.isVisible();
        historySidebar.setVisible(visible);
        historySidebar.setManaged(visible);
        if (sidebarSeparator != null) {
            sidebarSeparator.setVisible(visible);
            sidebarSeparator.setManaged(visible);
        }
        if (toggleSidebarBtn != null) {
            toggleSidebarBtn.setText(visible ? "☰ Thu gọn" : "☰ Lịch sử");
        }
    }

    @FXML
    public void createNewChat() {
        Path suiteDir = getSelectedSuiteDir();
        currentSession = chatHistoryStore.createNewSession(suiteDir, "Cuộc trò chuyện mới");
        refreshSessionsList(currentSession.getId());
        renderChatHistory();
        updateTokenUsageDisplay();
        ToastService.info("Đã tạo cuộc trò chuyện mới.");
    }

    private void handleTogglePin(AiChatSessionMeta item) {
        if (item == null) return;
        Path suiteDir = getSelectedSuiteDir();
        try {
            chatHistoryStore.setSessionPinned(suiteDir, item.getId(), !item.isPinned());
            refreshSessionsList(item.getId());
        } catch (IOException ignored) {}
    }

    private void handleRenameSession(AiChatSessionMeta item) {
        if (item == null) return;
        TextInputDialog dialog = new TextInputDialog(item.getTitle());
        dialog.setTitle("Đổi tên cuộc trò chuyện");
        dialog.setHeaderText("Nhập tiêu đề mới:");
        dialog.setContentText("Tiêu đề:");
        DialogTheme.apply(dialog);
        Optional<String> res = dialog.showAndWait();
        if (res.isPresent() && !res.get().isBlank()) {
            Path suiteDir = getSelectedSuiteDir();
            try {
                String newTitle = res.get().trim();
                chatHistoryStore.renameSession(suiteDir, item.getId(), newTitle);
                if (currentSession != null && item.getId().equals(currentSession.getId())) {
                    currentSession.setTitle(newTitle);
                }
                refreshSessionsList(item.getId());
                ToastService.info("Đã đổi tên cuộc trò chuyện.");
            } catch (IOException ignored) {}
        }
    }

    private void handleDeleteSession(AiChatSessionMeta item) {
        if (item == null) return;
        if (generationProgress != null && generationProgress.isVisible()
                && currentSession != null && item.getId().equals(currentSession.getId())) {
            ToastService.warn("Không thể xóa cuộc trò chuyện khi AI đang sinh kịch bản. Vui lòng đợi hoàn thành.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa");
        alert.setHeaderText("Xóa cuộc trò chuyện \"" + item.getTitle() + "\"?");
        alert.setContentText("Thao tác này không thể hoàn tác.");
        DialogTheme.apply(alert);
        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Path suiteDir = getSelectedSuiteDir();
            try {
                chatHistoryStore.deleteSession(suiteDir, item.getId());
                ToastService.info("Đã xóa cuộc trò chuyện.");
                if (currentSession != null && item.getId().equals(currentSession.getId())) {
                    currentSession = null;
                }
                loadChatHistoryForSuite();
            } catch (IOException ignored) {}
        }
    }

    private String formatSessionTime(long timestamp) {
        if (timestamp <= 0) return "";
        try {
            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            LocalDateTime now = LocalDateTime.now();
            if (dt.toLocalDate().equals(now.toLocalDate())) {
                return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
            } else {
                return dt.format(DateTimeFormatter.ofPattern("dd/MM"));
            }
        } catch (Exception e) {
            return "";
        }
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
                if (exportCasesMenuBtn != null) {
                    exportCasesMenuBtn.setDisable(false);
                }
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
        if (exportCasesMenuBtn != null) {
            exportCasesMenuBtn.setDisable(true);
        }
        showCaseDetail(null);
    }

    private void updateTokenUsageDisplay() {
        if (tokenUsageBadge == null)
            return;
        int promptTokens = tokenOptimizer.estimateTokens(promptInputArea.getText());
        int historyTokens = currentSession != null ? currentSession.calculateTotalTokens() : 0;
        int total = promptTokens + historyTokens;
        int maxLimit = 0;
        try {
            AiConfig aiConfig = settingsStore != null ? settingsStore.load().getAiConfig() : null;
            if (aiConfig != null) {
                maxLimit = aiConfig.getMaxTokens();
            }
        } catch (Exception ignored) {
        }
        String limitStr = maxLimit > 0 ? " / " + String.format("%,d", maxLimit) : " / ∞";
        tokenUsageBadge.setText("Tokens: ~" + total + limitStr + " (Prompt: ~" + promptTokens + ")");
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
        alert.setHeaderText("Xóa tin nhắn trong cuộc trò chuyện");
        alert.setContentText("Bạn có chắc chắn muốn xóa tin nhắn trong cuộc trò chuyện \"" + currentSession.getTitle() + "\" không?");
        DialogTheme.apply(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            removeThinkingBubble();
            Path suiteDir = getSelectedSuiteDir();
            currentSession.getMessages().clear();
            try {
                chatHistoryStore.saveSession(suiteDir, currentSession);
            } catch (IOException ignored) {
            }
            refreshSessionsList(currentSession.getId());
            renderChatHistory();
            updateTokenUsageDisplay();
            ToastService.info("Nội dung cuộc trò chuyện đã được xóa.");
        }
    }

    @FXML
    public void attachFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tài liệu hoặc ảnh chụp giao diện");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả tài liệu hỗ trợ", "*.txt", "*.md", "*.json", "*.csv", "*.tsv",
                        "*.docx", "*.xlsx", "*.xls", "*.pptx", "*.pdf", "*.html", "*.xml", "*.png", "*.jpg", "*.jpeg",
                        "*.webp"),
                new FileChooser.ExtensionFilter(
                        "Văn bản, Bảng tính & Specs (*.docx, *.xlsx, *.pptx, *.pdf, *.md, *.txt, *.csv)", "*.docx",
                        "*.xlsx", "*.xls", "*.pptx", "*.pdf", "*.txt", "*.md", "*.json", "*.csv", "*.tsv", "*.html",
                        "*.xml"),
                new FileChooser.ExtensionFilter("Hình ảnh / UI Screenshots (*.png, *.jpg)", "*.png", "*.jpg", "*.jpeg",
                        "*.webp"));
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
            if (AiTestGeneratorService.containsTargetUrl(
                    promptInputArea != null ? promptInputArea.getText() : "", attachedDocuments, liveDomJson, null)) {
                if (autoGenerateScriptCheck != null) {
                    autoGenerateScriptCheck.setSelected(true);
                }
            }
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
            previewMdBtn.setStyle(
                    "-fx-font-size: 10px; -fx-padding: 1 5; -fx-background-color: -color-primary-dim; -fx-text-fill: -color-primary; -fx-background-radius: 3; -fx-cursor: hand;");
            previewMdBtn.setOnAction(e -> MarkdownPreviewDialog.show(doc));
            chip.getChildren().add(previewMdBtn);
        }

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("ai-file-chip-remove");
        removeBtn.setOnAction(e -> {
            attachedChipsPane.getChildren().remove(chip);
            attachedDocuments.remove(doc);
            updateAttachmentsVisibility();
            if (AiTestGeneratorService.containsTargetUrl(
                    promptInputArea != null ? promptInputArea.getText() : "", attachedDocuments, liveDomJson, null)) {
                if (autoGenerateScriptCheck != null) {
                    autoGenerateScriptCheck.setSelected(true);
                }
            }
        });

        chip.getChildren().add(removeBtn);
        attachedChipsPane.getChildren().add(chip);
    }

    @FXML
    public void applyQuickBva() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.EXHAUSTIVE_UNIT_TEST);
        }
        promptInputArea.setText(
                "Phân tích luồng điều kiện (Control Flow), xác định tập giá trị biên (BVA) và sinh mã nguồn Unit Test có tham số hóa cho đoạn logic sau:\n\n");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickLogin() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.PLAYWRIGHT_E2E);
        }
        promptInputArea.setText(
                "Viết kịch bản Playwright E2E cho trang đăng nhập: Nhập email hợp lệ và mật khẩu, bấm nút Đăng nhập, xác minh người dùng được chuyển hướng vào Dashboard và hiển thị lời chào.");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickRca() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.FAILURE_RCA);
        }
        promptInputArea.setText(
                "Phân tích nguyên nhân gốc rễ (RCA) và đưa ra mã sửa lỗi drop-in cho log lỗi kiểm thử sau:\n\n");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickSecurity() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.SECURITY_FUZZING);
        }
        promptInputArea.setText(
                "Sinh ma trận vector kiểm thử bảo mật & fuzzing dữ liệu đầu vào (XSS, SQLi, Buffer Overflow, Unicode injection) cho form sau:\n\n");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickCart() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.PLAYWRIGHT_E2E);
        }
        promptInputArea.setText(
                "Viết kịch bản kiểm thử quy trình mua hàng: Thêm sản phẩm vào giỏ, kiểm tra số lượng và tổng tiền cập nhật chính xác, tiến hành thanh toán.");
        promptInputArea.positionCaret(promptInputArea.getText().length());
    }

    @FXML
    public void applyQuickDoc() {
        if (skillComboBox != null) {
            skillComboBox.setValue(com.studio.model.AiSkill.UNIVERSAL_DOCUMENT_COMPILER);
        }
        promptInputArea.setText(
                "Phân tích kỹ các yêu cầu trong tài liệu đính kèm và sinh bộ Test Cases bao quát các trường hợp chính (Happy path, Negative path, và Edge cases) kèm đầy đủ Assertions kiểm thử.");
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
                } catch (Exception ignored) {
                }
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

        if ("Cuộc trò chuyện mới".equalsIgnoreCase(currentSession.getTitle())) {
            String cleanPrompt = prompt.replace("\n", " ").trim();
            if (cleanPrompt.length() > 28) {
                cleanPrompt = cleanPrompt.substring(0, 28) + "...";
            }
            if (!cleanPrompt.isBlank()) {
                currentSession.setTitle(cleanPrompt);
            }
        }

        Path suiteDir = getSelectedSuiteDir();
        try {
            chatHistoryStore.saveSession(suiteDir, currentSession);
            refreshSessionsList(currentSession.getId());
        } catch (IOException ignored) {}

        renderMessageBubble(userMsg, true);

        // Snapshot attached docs and clear input
        List<DocumentParserService.ParsedDocument> docsSnapshot = new ArrayList<>(attachedDocuments);
        promptInputArea.clear();
        clearAttachments();
        updateTokenUsageDisplay();

        executePromptInternal(prompt, docsSnapshot, activeSkill);
    }

    private void executePromptInternal(String prompt, List<DocumentParserService.ParsedDocument> docsSnapshot,
            com.studio.model.AiSkill activeSkill) {
        SettingsStore.Settings settings;
        try {
            settings = settingsStore.load();
        } catch (IOException e) {
            settings = new SettingsStore.Settings();
        }
        AiConfig aiConfig = settings.getAiConfig();

        sendBtn.setDisable(true);
        sendBtn.setText("Đang sinh kịch bản...");
        generationProgress.setVisible(true);
        generationProgress.setManaged(true);
        showThinkingBubble(aiConfig.getEffectiveModel() + " — " + activeSkill.getDisplayName());

        Path suiteDir = getSelectedSuiteDir();
        String groupVal = (targetGroupCombo != null && targetGroupCombo.getValue() != null && !targetGroupCombo.getValue().isBlank())
                ? targetGroupCombo.getValue()
                : "(Tự động tạo khi lưu)";
        String suiteVal = (targetSuiteCombo != null && targetSuiteCombo.getValue() != null && !targetSuiteCombo.getValue().isBlank())
                ? targetSuiteCombo.getValue()
                : "(Tự động tạo khi lưu)";
        String context = "Nhóm đích: " + groupVal + ", Suite đích: " + suiteVal;

        // Tự động phát hiện URL từ prompt hoặc từ tài liệu đính kèm nếu chưa có Live DOM
        String promptUrl = com.studio.service.LiveDomScanner.extractFirstUrl(prompt);
        if (promptUrl == null && !attachedDocuments.isEmpty()) {
            for (DocumentParserService.ParsedDocument doc : attachedDocuments) {
                if (doc.getContent() != null) {
                    promptUrl = com.studio.service.LiveDomScanner.extractFirstUrl(doc.getContent());
                    if (promptUrl != null)
                        break;
                }
            }
        }
        final String detectedTargetUrl = promptUrl;
        if (detectedTargetUrl != null && autoGenerateScriptCheck != null && !autoGenerateScriptCheck.isSelected()) {
            autoGenerateScriptCheck.setSelected(true);
        }
        boolean generateScript = (autoGenerateScriptCheck != null)
                ? autoGenerateScriptCheck.isSelected()
                : (detectedTargetUrl != null || (liveDomJson != null && !liveDomJson.isBlank() && !"[]".equals(liveDomJson)));
        final boolean effectiveGenerateScript = generateScript;
        String domContext = liveDomJson;
        final AiConfig effectiveAiConfig = aiConfig;
        final AiChatSession targetSession = currentSession;
        final String targetSessionId = targetSession.getId();

        Thread thread = new Thread(() -> {
            try {
                String activeDomContext = domContext;
                // Nếu chưa có DOM context mà phát hiện URL hợp lệ -> Tự động quét Live DOM
                // ngầm!
                if ((activeDomContext == null || activeDomContext.isBlank() || "[]".equals(activeDomContext))
                        && detectedTargetUrl != null) {
                    Platform.runLater(() -> updateThinkingStatus(
                            "Đang tự động quét cấu trúc Live DOM từ " + detectedTargetUrl + "..."));
                    try {
                        String autoScannedDom = domScanner.scanUrl(detectedTargetUrl, 10000);
                        if (autoScannedDom != null && !autoScannedDom.isBlank() && !"[]".equals(autoScannedDom)) {
                            activeDomContext = autoScannedDom;
                            liveDomJson = autoScannedDom; // Cache cho phiên làm việc
                            Platform.runLater(
                                     () -> ToastService.info("🌐 Đã tự động nạp Live DOM từ " + detectedTargetUrl));
                        }
                    } catch (Exception ex) {
                        System.err.println("[AiAssistant] Tự động quét DOM ngầm thất bại: " + ex.getMessage());
                    }
                }

                AiTestGeneratorService.GenerationResult result = aiGenerator.generateTestCases(
                        effectiveAiConfig, prompt, docsSnapshot, context, activeDomContext,
                        targetSession.getMessages(),
                        status -> Platform.runLater(() -> updateThinkingStatus(status)),
                        activeSkill,
                        suiteDir,
                        effectiveGenerateScript);

                Platform.runLater(() -> {
                    removeThinkingBubble();
                    sendBtn.setDisable(false);
                    sendBtn.setText("Gửi (Enter)");
                    generationProgress.setVisible(false);
                    generationProgress.setManaged(false);

                    AiChatMessage assistantMsg = new AiChatMessage("assistant", result.getSummary());
                    if (result.isClarification()) {
                        assistantMsg.setClarificationQuestions(result.getQuestions());
                    }
                    assistantMsg.setGeneratedCasesSnapshot(result.getCases());
                    assistantMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(assistantMsg));
                    targetSession.addMessage(assistantMsg);

                    // Persist updated history
                    try {
                        chatHistoryStore.saveSession(suiteDir, targetSession);
                        refreshSessionsList(targetSession.getId());
                    } catch (IOException ignored) {
                    }

                    boolean isCurrentActive = currentSession != null && targetSessionId.equals(currentSession.getId());
                    if (isCurrentActive) {
                        renderMessageBubble(assistantMsg, true);
                        updateTokenUsageDisplay();

                        resultSummaryLabel.setText(result.getSummary());

                        currentGeneratedCases.clear();
                        currentGeneratedCases.addAll(result.getCases());

                        generatedCasesListView.getItems().setAll(currentGeneratedCases);
                        rawResponseViewer.setText(result.getRawResponse());

                        if (!currentGeneratedCases.isEmpty()) {
                            applyToSuiteBtn.setDisable(false);
                            if (exportCasesMenuBtn != null) {
                                exportCasesMenuBtn.setDisable(false);
                            }
                            generatedCasesListView.getSelectionModel().select(0);
                            ToastService.success("✨ Đã sinh " + currentGeneratedCases.size() + " test cases thành công!");
                        } else {
                            applyToSuiteBtn.setDisable(true);
                            if (exportCasesMenuBtn != null) {
                                exportCasesMenuBtn.setDisable(true);
                            }
                            showCaseDetail(null);
                            if (!result.isClarification()) {
                                ToastService.info("💬 Trợ lý AI: " + ("SEARCH_TESTS".equals(result.getIntent())
                                        ? "Đã tìm thấy test cases phù hợp."
                                        : "EVALUATE_TEST".equals(result.getIntent()) ? "Đã phân tích đánh giá test case."
                                                : "CHAT_ADVICE".equals(result.getIntent()) ? "Đã phản hồi tư vấn kỹ thuật."
                                                        : "Đã phản hồi yêu cầu."));
                            }
                        }
                    } else {
                        ToastService.success("✨ Phiên \"" + targetSession.getTitle() + "\" đã hoàn thành sinh kịch bản!");
                    }
                    refreshSessionsList(currentSession != null ? currentSession.getId() : null);
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
                    targetSession.addMessage(errorMsg);
                    try {
                        chatHistoryStore.saveSession(suiteDir, targetSession);
                    } catch (IOException ignored) {
                    }

                    if (currentSession != null && targetSessionId.equals(currentSession.getId())) {
                        renderMessageBubble(errorMsg, true);
                        updateTokenUsageDisplay();
                        ToastService.error("Lỗi AI: " + ex.getMessage());
                    } else {
                        ToastService.error("Lỗi AI ở phiên \"" + targetSession.getTitle() + "\": " + ex.getMessage());
                    }
                    refreshSessionsList(currentSession != null ? currentSession.getId() : null);
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
        String[] stages = new String[] {
                "Đang kết nối tới mô hình " + (modelName != null ? modelName : "AI") + "...",
                "Đang phân tích yêu cầu kiểm thử & ngữ cảnh tài liệu...",
                "Đang thiết kế kịch bản Playwright & Assertions kiểm thử...",
                "Đang tổng hợp các bước thực thi (Test Steps) & Dữ liệu...",
                "Đang hoàn thiện và định dạng mã nguồn runnable..."
        };

        final int[] stageIdx = { 0 };
        final int[] dotStep = { 0 };

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
            if (previewStepsListView != null)
                previewStepsListView.getItems().clear();
            if (previewStepsTab != null)
                previewStepsTab.setText("Các bước kiểm thử (0)");
            if (previewTestDataListView != null)
                previewTestDataListView.getItems().clear();
            if (previewTestDataTab != null)
                previewTestDataTab.setText("Dữ liệu kiểm thử (0)");
            return;
        }

        previewCaseNameLabel.setText(c.getName());
        previewCaseUrlLabel.setText(c.getStartUrl().isBlank() ? "" : "URL: " + c.getStartUrl());
        if (c.getScriptCode() != null && !c.getScriptCode().isBlank()) {
            previewScriptEditor.setText(c.getScriptCode());
        } else {
            previewScriptEditor.setText("""
                // [THÔNG BÁO] Không tạo test script tự động vì yêu cầu không có URL/link trang web cần test.
                // Bộ test case đã được thiết kế đầy đủ các bước kiểm thử, dữ liệu và tiêu chí nghiệm thu tại các tab bên cạnh:
                // - Tab 'Các bước': Danh sách bước thao tác, đối tượng và kết quả kỳ vọng
                // - Tab 'Assertions': Tiêu chí xác thực kết quả
                // - Tab 'Dữ liệu': Bộ tham số kiểm thử biên & phân vùng tương đương
                """);
            if (detailTabPane != null && previewStepsTab != null) {
                detailTabPane.getSelectionModel().select(previewStepsTab);
            }
        }

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
                previewTestDataListView.getItems().add(
                        item.getScenario() + " -> " + item.getParameters() + " [" + item.getExpectedOutcome() + "]");
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
                } catch (Exception ignored) {
                }
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
                        if (autoGenerateScriptCheck != null) {
                            autoGenerateScriptCheck.setSelected(true);
                        }
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
        if (text == null)
            return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+").matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    @FXML
    public void applyCasesToSelectedSuite() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case nào để nhập vào Suite.");
            return;
        }

        String group = targetGroupCombo.getValue();
        String suite = targetSuiteCombo.getValue();

        // Tự động xác định hoặc tạo Nhóm nếu chưa có
        if (group == null || group.isBlank()) {
            if (!targetGroupCombo.getItems().isEmpty()) {
                group = targetGroupCombo.getItems().get(0);
            } else {
                group = "AI-Tests";
            }
        }

        // Tự động xác định hoặc tạo Suite nếu chưa có
        if (suite == null || suite.isBlank()) {
            if (!targetSuiteCombo.getItems().isEmpty()) {
                suite = targetSuiteCombo.getItems().get(0);
            } else {
                suite = resolveAutoSuiteName();
            }
        }

        group = group.trim();
        suite = suite.trim();

        Path projectDir = StudioPaths.getProjectDir();
        Path groupDir = projectDir.resolve(group);
        boolean isGroupNew = false;
        try {
            Files.createDirectories(groupDir);
            Path groupJson = groupDir.resolve("group.json");
            if (!Files.exists(groupJson)) {
                projectStore.createGroup(projectDir, group, "Nhóm kiểm thử tự động tạo bởi Trợ lý AI");
                isGroupNew = true;
            }
        } catch (IOException e) {
            ToastService.error("Không thể tạo thư mục Nhóm: " + e.getMessage());
            return;
        }

        Path suiteDir = groupDir.resolve(suite);
        boolean isSuiteNew = false;
        try {
            Files.createDirectories(suiteDir);
            Path suiteJson = suiteDir.resolve("suite.json");
            if (!Files.exists(suiteJson)) {
                projectStore.createSuite(groupDir, suite, "Suite kiểm thử tự động tạo bởi Trợ lý AI", "READY", "", null);
                isSuiteNew = true;
            }
        } catch (IOException e) {
            ToastService.error("Không thể tạo thư mục Suite: " + e.getMessage());
            return;
        }

        // Cập nhật lại UI ComboBoxes nếu vừa tự động tạo
        if (!targetGroupCombo.getItems().contains(group)) {
            targetGroupCombo.getItems().add(group);
        }
        targetGroupCombo.setValue(group);

        if (!targetSuiteCombo.getItems().contains(suite)) {
            targetSuiteCombo.getItems().add(suite);
        }
        targetSuiteCombo.setValue(suite);

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

            String successMsg;
            if (isGroupNew || isSuiteNew) {
                successMsg = "⚡ Đã tự động tạo và nhập " + applied.size() + " test cases vào '" + group + " / " + suite + "'!";
            } else {
                successMsg = "⚡ Đã nhập " + applied.size() + " test cases vào suite '" + suite
                        + "'! Bạn có thể bấm 'Hoàn tác' trên tin nhắn bất kỳ lúc nào.";
            }
            ToastService.success(successMsg);

            // Refresh Explorer if cached so new group/suite appears in the explorer tree
            ExplorerPageController explorer = ViewNavigator.getController(ViewNavigator.Page.EXPLORER, ExplorerPageController.class);
            if (explorer != null) {
                explorer.reloadFromDisk();
            }

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

    private String resolveAutoSuiteName() {
        if (currentSession != null && currentSession.getTitle() != null) {
            String title = currentSession.getTitle().trim();
            if (!title.isBlank() && !"Cuộc trò chuyện mới".equalsIgnoreCase(title)) {
                // Remove invalid filesystem characters
                String safe = title.replaceAll("[\\\\/:*?\"<>|]", " ")
                                   .replaceAll("\\s+", " ")
                                   .trim();
                if (safe.length() > 36) {
                    safe = safe.substring(0, 36).trim();
                }
                if (!safe.isBlank()) {
                    return safe;
                }
            }
        }
        return "AI-Generated-Suite";
    }

    @FXML
    public void exportCasesToExcel() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case nào để tải về.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Bộ Test Cases ra Excel (.xlsx)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("TestCases_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".xlsx");

        File file = chooser.showSaveDialog(promptInputArea != null && promptInputArea.getScene() != null ? promptInputArea.getScene().getWindow() : null);
        if (file == null) return;

        try {
            TestCaseExcelExportService.exportAiGeneratedCases(
                    currentGeneratedCases,
                    resultSummaryLabel != null ? resultSummaryLabel.getText() : "Sinh bởi Trợ lý AI",
                    file.toPath(),
                    ExportLanguage.VIETNAMESE
            );
            ToastService.success("✅ Đã xuất " + currentGeneratedCases.size() + " test cases ra file Excel: " + file.getName());
        } catch (Exception ex) {
            AiLoggingService.error("EXPORT", "Xuất file Excel thất bại: " + ex.getMessage());
            ToastService.error("❌ Xuất file Excel thất bại: " + ex.getMessage());
        }
    }

    @FXML
    public void exportCasesToMarkdown() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case nào để tải về.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Bộ Test Cases ra Markdown (.md)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Document (*.md)", "*.md"));
        chooser.setInitialFileName("TestCases_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".md");

        File file = chooser.showSaveDialog(promptInputArea != null && promptInputArea.getScene() != null ? promptInputArea.getScene().getWindow() : null);
        if (file == null) return;

        try {
            TestCaseExcelExportService.exportToMarkdown(
                    currentGeneratedCases,
                    resultSummaryLabel != null ? resultSummaryLabel.getText() : "Sinh bởi Trợ lý AI",
                    file.toPath()
            );
            ToastService.success("✅ Đã xuất tài liệu Markdown: " + file.getName());
        } catch (Exception ex) {
            AiLoggingService.error("EXPORT", "Xuất Markdown thất bại: " + ex.getMessage());
            ToastService.error("❌ Xuất Markdown thất bại: " + ex.getMessage());
        }
    }

    @FXML
    public void exportCasesToJson() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case nào để tải về.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Bộ Test Cases ra JSON (.json)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Data (*.json)", "*.json"));
        chooser.setInitialFileName("TestCases_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".json");

        File file = chooser.showSaveDialog(promptInputArea != null && promptInputArea.getScene() != null ? promptInputArea.getScene().getWindow() : null);
        if (file == null) return;

        try {
            TestCaseExcelExportService.exportToJson(
                    currentGeneratedCases,
                    resultSummaryLabel != null ? resultSummaryLabel.getText() : "Sinh bởi Trợ lý AI",
                    file.toPath()
            );
            ToastService.success("✅ Đã xuất dữ liệu JSON: " + file.getName());
        } catch (Exception ex) {
            AiLoggingService.error("EXPORT", "Xuất JSON thất bại: " + ex.getMessage());
            ToastService.error("❌ Xuất JSON thất bại: " + ex.getMessage());
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

        String title = ("user".equalsIgnoreCase(role) ? "Bạn" : "Trợ lý AI") + "  •  " + formattedTime + "  •  ~"
                + msg.getEstimatedTokens() + " tokens";
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

        if (msg.hasClarificationQuestions()) {
            VBox clarificationCard = buildClarificationCard(msg);
            bubble.getChildren().add(clarificationCard);
        }

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

    private VBox buildClarificationCard(AiChatMessage msg) {
        VBox card = new VBox(8);
        card.getStyleClass().add("clarification-card");

        Label cardTitle = new Label("💡 Cần làm rõ một số điểm mờ để sinh Test Case tối ưu:");
        cardTitle.getStyleClass().add("clarification-card-title");
        card.getChildren().add(cardTitle);

        boolean isAnswered = msg.isClarificationAnswered();
        Map<String, String> answers = msg.getClarificationAnswers() != null
                ? new LinkedHashMap<>(msg.getClarificationAnswers())
                : new LinkedHashMap<>();

        Map<String, Supplier<String>> valueSuppliers = new LinkedHashMap<>();

        for (AiClarificationQuestion q : msg.getClarificationQuestions()) {
            VBox qBox = new VBox(6);
            qBox.getStyleClass().add("clarification-qbox");

            Label qLabel = new Label(q.getQuestion());
            qLabel.getStyleClass().add("clarification-qlabel");
            qLabel.setWrapText(true);
            qBox.getChildren().add(qLabel);

            FlowPane chipsPane = new FlowPane(6, 6);
            List<Button> chipButtons = new ArrayList<>();
            String initialAnswer = answers.getOrDefault(q.getId(), q.getDefaultOption());
            SimpleStringProperty selectedVal = new SimpleStringProperty(initialAnswer);

            TextField customInput = new TextField();
            customInput.setPromptText("Hoặc tự nhập yêu cầu...");
            customInput.getStyleClass().add("clarification-custom-input");

            for (String opt : q.getOptions()) {
                Button chip = new Button(opt);
                chip.getStyleClass().add("clarification-chip");
                if (opt.equals(selectedVal.get())) {
                    chip.getStyleClass().add("clarification-chip-selected");
                }
                if (!isAnswered) {
                    chip.setOnAction(e -> {
                        selectedVal.set(opt);
                        customInput.clear();
                        for (Button b : chipButtons) {
                            b.getStyleClass().remove("clarification-chip-selected");
                        }
                        chip.getStyleClass().add("clarification-chip-selected");
                    });
                } else {
                    chip.setDisable(true);
                }
                chipButtons.add(chip);
                chipsPane.getChildren().add(chip);
            }

            if (!isAnswered) {
                customInput.textProperty().addListener((obs, oldV, newV) -> {
                    if (newV != null && !newV.isBlank()) {
                        for (Button b : chipButtons) {
                            b.getStyleClass().remove("clarification-chip-selected");
                        }
                        selectedVal.set(newV.trim());
                    }
                });
            } else {
                customInput.setDisable(true);
                String ans = answers.get(q.getId());
                if (ans != null && !q.getOptions().contains(ans)) {
                    customInput.setText(ans);
                }
            }

            qBox.getChildren().addAll(chipsPane, customInput);
            card.getChildren().add(qBox);

            valueSuppliers.put(q.getId(), () -> {
                String typed = customInput.getText() != null ? customInput.getText().trim() : "";
                if (!typed.isEmpty()) {
                    return typed;
                }
                return selectedVal.get();
            });
        }

        if (!isAnswered) {
            HBox actionRow = new HBox(8);
            actionRow.setAlignment(Pos.CENTER_RIGHT);

            Button confirmBtn = new Button("⚡ Xác nhận & Sinh Test Cases chi tiết");
            confirmBtn.getStyleClass().add("clarification-confirm-btn");
            confirmBtn.setOnAction(e -> {
                Map<String, String> collected = new LinkedHashMap<>();
                valueSuppliers.forEach((id, sup) -> {
                    String v = sup.get();
                    if (v != null && !v.isBlank()) {
                        collected.put(id, v);
                    }
                });

                msg.setClarificationAnswers(collected);
                msg.setClarificationAnswered(true);

                // Find original prompt
                String originalPrompt = "";
                List<AiChatMessage> msgs = currentSession.getMessages();
                int idx = msgs.indexOf(msg);
                for (int i = (idx >= 0 ? idx - 1 : msgs.size() - 1); i >= 0; i--) {
                    if ("user".equalsIgnoreCase(msgs.get(i).getRole())) {
                        originalPrompt = msgs.get(i).getContent();
                        break;
                    }
                }

                Path suiteDir = getSelectedSuiteDir();
                try {
                    chatHistoryStore.saveSession(suiteDir, currentSession);
                    refreshSessionsList(currentSession.getId());
                } catch (IOException ignored) {
                }

                String augmented = aiGenerator.getAmbiguityAnalyzer().buildAugmentedPrompt(originalPrompt, collected);

                // Refresh UI to show chips disabled and badge
                renderChatHistory();

                // Add user confirmation bubble and execute
                AiChatMessage userConfirm = new AiChatMessage("user", "Đã xác nhận lựa chọn chi tiết:\n" + augmented);
                userConfirm.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(userConfirm));
                currentSession.addMessage(userConfirm);
                renderMessageBubble(userConfirm, true);

                executePromptInternal(augmented, List.of(), com.studio.model.AiSkill.AUTO);
            });

            actionRow.getChildren().add(confirmBtn);
            card.getChildren().add(actionRow);
        } else {
            Label answeredLbl = new Label("✓ Đã xác nhận làm rõ và đang xử lý theo các lựa chọn trên.");
            answeredLbl.getStyleClass().add("clarification-answered-badge");
            card.getChildren().add(answeredLbl);
        }

        return card;
    }

    private void handleUndoMessage(AiChatMessage targetMsg) {
        if (targetMsg == null)
            return;

        // 1. If applied to suite, ask for confirmation to rollback files
        if (targetMsg.isAppliedToSuite() && targetMsg.getSuiteSnapshotJson() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xác nhận hoàn tác Suite");
            alert.setHeaderText("Hoàn tác toàn bộ thay đổi trong Suite");
            alert.setContentText(
                    "Tin nhắn này đã thêm/sửa test cases trên đĩa. Bạn có muốn hoàn tác và khôi phục các file của Suite về trạng thái ban đầu không?");
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
                // If user message is undone, also remove the subsequent assistant reply if
                // present
                if (idx + 1 < msgs.size() && "assistant".equalsIgnoreCase(msgs.get(idx + 1).getRole())) {
                    msgs.remove(idx + 1);
                }
                msgs.remove(idx);
            } else {
                // If assistant message is undone, remove this assistant message and prior user
                // prompt
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
            refreshSessionsList(currentSession.getId());
        } catch (IOException ignored) {
        }

        renderChatHistory();
        updateTokenUsageDisplay();
        ToastService.info("Đã hoàn tác tin nhắn thành công.");
    }

    public AiChatSession getCurrentSession() {
        return currentSession;
    }

    public void setCurrentSession(AiChatSession session) {
        this.currentSession = session;
    }

    public ComboBox<String> getTargetGroupCombo() {
        return targetGroupCombo;
    }

    public ComboBox<String> getTargetSuiteCombo() {
        return targetSuiteCombo;
    }

    public ListView<AiChatSessionMeta> getSessionsListView() {
        return sessionsListView;
    }

    public VBox getChatMessagesBox() {
        return chatMessagesBox;
    }

    public List<AiGeneratedCase> getCurrentGeneratedCases() {
        return currentGeneratedCases;
    }
}
