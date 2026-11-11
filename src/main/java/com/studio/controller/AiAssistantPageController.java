package com.studio.controller;

import com.studio.model.*;
import com.studio.service.AiAmbiguityAnalyzer;
import com.studio.service.AiChatRunRegistry;
import com.studio.service.AiContinuation;
import com.studio.service.AiDelegationPolicy;
import com.studio.service.AiCoordinatorAgent;
import com.studio.service.AiLlmAgentExecutor;
import com.studio.service.AiSubAgentOrchestrator;
import com.studio.service.OpenAiClient;
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
import javafx.util.Callback;
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
import java.util.Locale;
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
    private ComboBox<com.studio.model.ChatDisplayMode> chatDisplayModeCombo;
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
    /** Guards programmatic target updates: ComboBox.setValue also fires onAction. */
    private boolean isApplyingSessionTarget = false;
    /**
     * One worker per conversation. Runs are keyed by session id, so switching tabs, switching
     * conversations or choosing another save target never stops or hijacks a running agent.
     */
    private final AiChatRunRegistry runRegistry = new AiChatRunRegistry();
    private String liveDomJson = null;
    private String currentSuggestedGroup = null;
    private String currentSuggestedSuite = null;
    /** Density used to render assistant/agent bubbles; user prompts are always shown in full. */
    private ChatDisplayMode chatDisplayMode = ChatDisplayMode.SUMMARY;

    private HBox thinkingBubbleWrapper = null;
    private Timeline thinkingTimeline = null;
    private Label thinkingStatusLabel = null;

    /** Toolbar labels of the collapsible history rail. */
    private static final String HISTORY_TOGGLE_OPEN_LABEL = "Lịch sử";
    private static final String HISTORY_TOGGLE_COLLAPSE_LABEL = "Thu gọn";
    /** Badges shown both on the rail rows and on the toolbar toggle. */
    private static final String RUNNING_GLYPH = "⏳";
    private static final String UNREAD_GLYPH = "●";
    private static final String RUNNING_BADGE_LABEL = RUNNING_GLYPH + " Đang chạy";
    private static final String UNREAD_BADGE_LABEL = UNREAD_GLYPH + " Kết quả mới";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        refreshModelIndicator();
        setupSessionsSidebar();
        collapseHistorySidebarByDefault();
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
            UiTooltips.install(sendBtn, "Gửi yêu cầu sinh test case (Enter để gửi, Shift+Enter để xuống dòng)");
        }
        if (promptInputArea != null) {
            UiTooltips.install(promptInputArea, "Nhập nội dung (Enter để gửi, Shift+Enter để xuống dòng)");
        }
        if (attachFileBtn != null) {
            UiIcons.setGraphic(attachFileBtn, UiIcons.PLUS, 12);
            UiTooltips.install(attachFileBtn, "Đính kèm tài liệu (.txt, .md, Word, PDF) hoặc ảnh UI (.png, .jpg)");
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

        // Both target pickers leave an empty item so Đích lưu can be cleared to
        // "(Tự động tạo khi lưu)" — without it selecting a group/suite was one-way.
        targetGroupCombo.setButtonCell(placeholderCell("Chọn Nhóm..."));
        targetSuiteCombo.setButtonCell(placeholderCell("Chọn Suite..."));
        targetGroupCombo.setOnAction(e -> onGroupSelected());
        // Picking a target never reloads the transcript: history is workspace-wide, and a swap here
        // was what hid the conversation (and its running agent) when the user chose a suite.
        targetSuiteCombo.setOnAction(e -> updateTargetControlsLock());
        String targetTooltip = "Đích lưu cho Test Case sinh ra. Chỉ chọn được trước khi bắt đầu trò chuyện;"
                + " mặc định để trống và tự động tạo khi lưu.";
        UiTooltips.install(targetGroupCombo, targetTooltip);
        UiTooltips.install(targetSuiteCombo, targetTooltip);

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

        setupChatDisplayModeCombo();

        // Setup case list cell factory
        // Fixed row height: single-line cells, so VirtualFlow can skip per-cell
        // measurement while scrolling.
        generatedCasesListView.setFixedCellSize(30);
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
                    String line = item.getName() + " (" + stepsCount + " steps, "
                            + assertsCount + " asserts)";
                    setText(line);
                    // The cell ellipsizes by default on a narrow panel; the tooltip
                    // keeps the full case name reachable.
                    UiTooltips.install(this, line);
                }
            }
        });

        generatedCasesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showCaseDetail(newVal);
        });

        // Key listener for prompt area: Enter (send), Shift+Enter (new line), and Ctrl+H (toggle history sidebar)
        promptInputArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown() || event.isAltDown()) {
                    // Shift+Enter / Alt+Enter: insert newline at cursor
                    promptInputArea.replaceSelection("\n");
                    event.consume();
                } else {
                    // Plain Enter (or Ctrl+Enter / Cmd+Enter): send message
                    if (currentSession == null || !isSessionRunning(currentSession.getId())) {
                        sendPrompt();
                    }
                    event.consume();
                }
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

    /**
     * Wires the transcript density selector (Gọn / Tổng hợp / Chi tiết). The choice is persisted in
     * settings and re-renders the open transcript, so a dense agent answer can be collapsed to its gist
     * without losing the full text.
     */
    private void setupChatDisplayModeCombo() {
        if (chatDisplayModeCombo == null) {
            return;
        }
        chatDisplayModeCombo.getItems().setAll(ChatDisplayMode.values());
        chatDisplayMode = loadChatDisplayMode();
        chatDisplayModeCombo.setValue(chatDisplayMode);
        Callback<ListView<ChatDisplayMode>, ListCell<ChatDisplayMode>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatDisplayMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getLabel());
            }
        };
        chatDisplayModeCombo.setCellFactory(cellFactory);
        chatDisplayModeCombo.setButtonCell(cellFactory.call(null));
        UiTooltips.install(chatDisplayModeCombo,
                "Mức hiển thị tin nhắn trong khung chat. Nội dung dài luôn xem được đầy đủ bằng nút \"Xem thêm\".");
        chatDisplayModeCombo.setOnAction(e -> {
            ChatDisplayMode selected = chatDisplayModeCombo.getValue();
            if (selected == null || selected == chatDisplayMode) {
                return;
            }
            chatDisplayMode = selected;
            persistChatDisplayMode(selected);
            // Never wipe the live agent bubbles of a turn that is still running; the end-of-turn
            // render already applies the new mode.
            if (currentSession == null || !isSessionRunning(currentSession.getId())) {
                renderChatHistory();
            }
        });
    }

    private ChatDisplayMode loadChatDisplayMode() {
        try {
            return ChatDisplayMode.fromKey(settingsStore.load().getAiChatDisplayMode());
        } catch (Exception e) {
            AiLoggingService.warn("AI-CHAT", "Không đọc được chế độ hiển thị tin nhắn: " + e.getMessage());
            return ChatDisplayMode.SUMMARY;
        }
    }

    private void persistChatDisplayMode(ChatDisplayMode mode) {
        try {
            SettingsStore.Settings settings = settingsStore.load();
            settings.setAiChatDisplayMode(mode.key());
            settingsStore.save(settings);
        } catch (Exception e) {
            AiLoggingService.warn("AI-CHAT", "Không lưu được chế độ hiển thị tin nhắn: " + e.getMessage());
        }
    }

    @FXML
    public void reloadTargets() {
        if (isReloadingTargets) return;
        isReloadingTargets = true;
        try {
            String prevGroup = targetGroupCombo.getValue();
            String prevSuite = targetSuiteCombo.getValue();

            List<String> groups = listGroupNames();
            targetGroupCombo.getItems().setAll(withBlankChoice(groups));
            // A blank choice is a real state ("tự động tạo khi lưu"), never replaced by the first group.
            String targetGroup = groups.contains(prevGroup) ? prevGroup : "";
            targetGroupCombo.setValue(targetGroup);

            List<String> suites = targetGroup.isBlank() ? List.of() : listSuiteNames(targetGroup);
            targetSuiteCombo.getItems().setAll(withBlankChoice(suites));
            String targetSuite = prevSuite != null && !prevSuite.isBlank() && suites.contains(prevSuite)
                    ? prevSuite
                    : "";
            targetSuiteCombo.setValue(targetSuite);
        } finally {
            isReloadingTargets = false;
        }
        updateTargetControlsLock();
        // History is workspace-wide; refreshing the target list must never reload or replace the open
        // transcript (that swap used to hide a conversation whose agent was still running).
        refreshSessionsList(currentSession != null ? currentSession.getId() : null);
    }

    private static List<String> listGroupNames() {
        List<String> groups = new ArrayList<>();
        Path projDir = StudioPaths.getProjectDir();
        if (!Files.exists(projDir)) {
            return groups;
        }
        try (var stream = Files.list(projDir)) {
            stream.filter(Files::isDirectory)
                    .map(d -> d.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(groups::add);
        } catch (IOException e) {
            AiLoggingService.warn("AI-CHAT", "Không đọc được danh sách Nhóm: " + e.getMessage());
        }
        return groups;
    }

    private static List<String> listSuiteNames(String groupName) {
        List<String> suites = new ArrayList<>();
        if (groupName == null || groupName.isBlank()) {
            return suites;
        }
        Path groupDir = StudioPaths.getProjectDir().resolve(groupName);
        if (!Files.exists(groupDir)) {
            return suites;
        }
        try (var stream = Files.list(groupDir)) {
            stream.filter(Files::isDirectory)
                    .map(d -> d.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(suites::add);
        } catch (IOException e) {
            AiLoggingService.warn("AI-CHAT", "Không đọc được danh sách Suite của " + groupName + ": "
                    + e.getMessage());
        }
        return suites;
    }

    private void onGroupSelected() {
        if (isReloadingTargets || isApplyingSessionTarget) return;
        populateSuiteChoices(targetGroupCombo.getValue(), null);
        updateTargetControlsLock();
    }

    private void populateSuiteChoices(String groupName, String preferredSuite) {
        List<String> suites = groupName == null || groupName.isBlank()
                ? new ArrayList<>()
                : listSuiteNames(groupName);
        targetSuiteCombo.getItems().setAll(withBlankChoice(suites));
        String nextSuite = preferredSuite != null && !preferredSuite.isBlank() && suites.contains(preferredSuite)
                ? preferredSuite
                : "";
        targetSuiteCombo.setValue(nextSuite);
    }

    /**
     * The save target belongs to the conversation: once the user has sent a prompt it is locked, so the
     * transcript can never be re-bucketed under a different suite while a worker is running.
     */
    private void updateTargetControlsLock() {
        boolean locked = currentSession != null && currentSession.hasUserMessages();
        if (targetGroupCombo != null) {
            targetGroupCombo.setDisable(locked);
        }
        if (targetSuiteCombo != null) {
            targetSuiteCombo.setDisable(locked);
        }
    }

    /** Restores the target captured by a conversation into the (guarded) pickers. */
    private void applySessionTarget(AiChatSession session) {
        if (session == null || targetGroupCombo == null || targetSuiteCombo == null) {
            return;
        }
        isApplyingSessionTarget = true;
        try {
            String group = session.getTargetGroup() != null ? session.getTargetGroup() : "";
            String suite = session.getTargetSuite() != null ? session.getTargetSuite() : "";

            List<String> groups = listGroupNames();
            if (!group.isBlank() && !groups.contains(group)) {
                groups.add(group);
            }
            targetGroupCombo.getItems().setAll(withBlankChoice(groups));
            targetGroupCombo.setValue(group);

            List<String> suites = group.isBlank() ? new ArrayList<>() : listSuiteNames(group);
            if (!suite.isBlank() && !suites.contains(suite)) {
                suites.add(suite);
            }
            targetSuiteCombo.getItems().setAll(withBlankChoice(suites));
            targetSuiteCombo.setValue(suites.contains(suite) ? suite : "");
        } finally {
            isApplyingSessionTarget = false;
        }
        updateTargetControlsLock();
    }

    /** Prepends the "no selection" entry so both pickers can be cleared again. */
    private static List<String> withBlankChoice(List<String> values) {
        List<String> choices = new ArrayList<>(values.size() + 1);
        choices.add("");
        choices.addAll(values);
        return choices;
    }

    /** Renders the empty choice as a prompt instead of an unexplained blank line. */
    private static ListCell<String> placeholderCell(String prompt) {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null || item.isBlank() ? prompt : item);
                setStyle(item == null || item.isBlank() ? "-fx-text-fill: derive(-fx-text-background-color, 45%);" : null);
            }
        };
    }

    public Path getSelectedSuiteDir() {
        if (targetGroupCombo == null || targetSuiteCombo == null) return null;
        return resolveSuiteDir(targetGroupCombo.getValue(), targetSuiteCombo.getValue());
    }

    /** Resolves a stored target pair to an existing suite directory, or null when it is not created yet. */
    private static Path resolveSuiteDir(String group, String suite) {
        if (group == null || suite == null || group.isBlank() || suite.isBlank()) {
            return null;
        }
        Path p = StudioPaths.getProjectDir().resolve(group).resolve(suite);
        return Files.exists(p) && Files.isDirectory(p) ? p : null;
    }

    public void loadChatHistoryForSuite() {
        if (isReloadingTargets) return;
        refreshSessionsList(currentSession != null ? currentSession.getId() : null);
        refreshOpenSession();
    }

    /** Reloads the selected conversation from the workspace history and re-renders it. */
    private void refreshOpenSession() {
        AiChatSessionMeta selected = sessionsListView != null
                && sessionsListView.getSelectionModel().getSelectedItem() != null
                ? sessionsListView.getSelectionModel().getSelectedItem()
                : null;
        String preferredId = selected != null
                ? selected.getId()
                : (currentSession != null ? currentSession.getId() : null);

        AiChatSession loaded = loadLiveSession(preferredId);
        if (loaded == null) {
            loaded = chatHistoryStore.loadSession(null, null);
        }
        currentSession = loaded;
        runRegistry.markRead(currentSession.getId());
        applySessionTarget(currentSession);
        renderChatHistory();
        updateTokenUsageDisplay();
        updateComposerRunState();
        refreshActivityIndicators();
    }

    /**
     * While a conversation is running, the in-memory session is the only complete copy: its agent turns
     * are persisted only at end of turn. Returning the live instance keeps the transcript continuous when
     * the user switches away and back instead of showing a stale copy read from disk.
     */
    private AiChatSession loadLiveSession(String sessionId) {
        AiChatSession live = runRegistry.getLiveSession(sessionId);
        return live != null ? live : chatHistoryStore.loadSessionById(sessionId);
    }

    private void setupSessionsSidebar() {
        if (toggleSidebarBtn != null) {
            UiIcons.setGraphic(toggleSidebarBtn, UiIcons.FOLDER, 12);
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
            sessionsListView.setFixedCellSize(30);
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

                        if (isSessionRunning(item.getId())) {
                            Label runLabel = new Label(RUNNING_BADGE_LABEL);
                            runLabel.getStyleClass().add("ai-session-running");
                            bottomRow.getChildren().add(runLabel);
                            UiTooltips.install(this, "Tác nhân AI đang xử lý trong cuộc trò chuyện này");
                        } else if (runRegistry.isUnread(item.getId())) {
                            Label unreadLabel = new Label(UNREAD_BADGE_LABEL);
                            unreadLabel.getStyleClass().add("ai-session-unread");
                            bottomRow.getChildren().add(unreadLabel);
                            UiTooltips.install(this,
                                    "Cuộc trò chuyện này có kết quả mới từ tác nhân AI mà bạn chưa mở");
                        } else {
                            UiTooltips.clear(this);
                        }

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
                String query = newVal != null ? newVal.trim().toLowerCase(Locale.ROOT) : "";
                filteredSessions.setPredicate(session -> session == null || session.matches(query));
            });
        }
        refreshHistoryToggleBadge();
    }

    public void refreshSessionsList(String selectSessionId) {
        // One workspace-wide history: the save target no longer decides which conversations are visible.
        List<AiChatSessionMeta> list = chatHistoryStore.listAllSessions();
        allSessions.setAll(list);
        selectSessionRow(selectSessionId);
        refreshHistoryToggleBadge();
    }

    /** Keeps the rail selection on the open conversation without switching the transcript. */
    private void selectSessionRow(String selectSessionId) {
        if (sessionsListView == null) {
            return;
        }
        isSwitchingSession = true;
        try {
            String targetId = selectSessionId != null
                    ? selectSessionId
                    : (currentSession != null ? currentSession.getId() : null);
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

    /**
     * Repaints the activity badges: the history rows (running / new results) plus the toolbar
     * toggle, so work happening in a conversation the user is not reading stays visible.
     */
    private void refreshActivityIndicators() {
        if (sessionsListView != null) {
            sessionsListView.refresh();
        }
        refreshHistoryToggleBadge();
    }

    /** The rail can be collapsed, so background runs and unseen results are mirrored on the toggle. */
    private void refreshHistoryToggleBadge() {
        if (toggleSidebarBtn == null) {
            return;
        }
        boolean sidebarVisible = historySidebar != null && historySidebar.isVisible();
        String viewedSessionId = currentSession != null ? currentSession.getId() : null;
        int backgroundRuns = runRegistry.countBackgroundRuns(viewedSessionId);
        int unread = runRegistry.countUnread();
        toggleSidebarBtn.setText(buildHistoryToggleLabel(sidebarVisible, backgroundRuns, unread));
        UiTooltips.install(toggleSidebarBtn,
                buildHistoryToggleTooltip(sidebarVisible, backgroundRuns, unread));
    }

    /** Toolbar label of the history rail with one badge per activity kind, e.g. "Lịch sử ⏳2 ●1". */
    static String buildHistoryToggleLabel(boolean sidebarVisible, int backgroundRuns, int unread) {
        StringBuilder label = new StringBuilder(
                sidebarVisible ? HISTORY_TOGGLE_COLLAPSE_LABEL : HISTORY_TOGGLE_OPEN_LABEL);
        if (backgroundRuns > 0) {
            label.append(' ').append(RUNNING_GLYPH).append(backgroundRuns);
        }
        if (unread > 0) {
            label.append(' ').append(UNREAD_GLYPH).append(unread);
        }
        return label.toString();
    }

    private static String buildHistoryToggleTooltip(boolean sidebarVisible, int backgroundRuns, int unread) {
        StringBuilder tip = new StringBuilder(sidebarVisible
                ? "Thu gọn thanh lịch sử cuộc trò chuyện (Ctrl+H / Cmd+H)"
                : "Mở thanh lịch sử cuộc trò chuyện (Ctrl+H / Cmd+H)");
        if (backgroundRuns > 0) {
            tip.append("\n").append(RUNNING_GLYPH).append(' ').append(backgroundRuns)
                    .append(" cuộc trò chuyện đang chạy ngầm");
        }
        if (unread > 0) {
            tip.append("\n").append(UNREAD_GLYPH).append(' ').append(unread)
                    .append(" cuộc trò chuyện có kết quả mới chưa mở");
        }
        return tip.toString();
    }

    private void switchToSession(String sessionId) {
        if (isSwitchingSession || sessionId == null) return;
        if (currentSession != null && sessionId.equals(currentSession.getId())) return;

        isSwitchingSession = true;
        try {
            AiChatSession loaded = loadLiveSession(sessionId);
            if (loaded == null) {
                return;
            }
            currentSession = loaded;
            runRegistry.markRead(sessionId);
            applySessionTarget(currentSession);
            renderChatHistory();
            updateTokenUsageDisplay();
            updateComposerRunState();
            refreshActivityIndicators();
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
        refreshHistoryToggleBadge();
    }

    /**
     * Collapses the history rail on first load: the chat column is the work area,
     * and the rail is opt-in via the toolbar toggle.
     */
    private void collapseHistorySidebarByDefault() {
        if (historySidebar == null) {
            return;
        }
        historySidebar.setVisible(false);
        historySidebar.setManaged(false);
        if (sidebarSeparator != null) {
            sidebarSeparator.setVisible(false);
            sidebarSeparator.setManaged(false);
        }
        if (toggleSidebarBtn != null) {
            refreshHistoryToggleBadge();
        }
    }

    @FXML
    public void createNewChat() {
        // New conversations always start with a blank target, and the history stays workspace-wide.
        currentSession = chatHistoryStore.createNewSession(null, "Cuộc trò chuyện mới");
        currentSession.setTargetGroup("");
        currentSession.setTargetSuite("");
        runRegistry.markRead(currentSession.getId());
        try {
            chatHistoryStore.saveSession(currentSession);
        } catch (IOException e) {
            AiLoggingService.warn("AI-CHAT", "Không lưu được cuộc trò chuyện mới: " + e.getMessage());
        }
        refreshSessionsList(currentSession.getId());
        applySessionTarget(currentSession);
        renderChatHistory();
        updateTokenUsageDisplay();
        updateComposerRunState();
        ToastService.info("Đã tạo cuộc trò chuyện mới.");
    }

    private void handleTogglePin(AiChatSessionMeta item) {
        if (item == null) return;
        try {
            chatHistoryStore.setSessionPinned(item.getId(), !item.isPinned());
            refreshSessionsList(item.getId());
        } catch (IOException e) {
            AiLoggingService.warn("AI-CHAT", "Không ghim được cuộc trò chuyện: " + e.getMessage());
        }
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
            try {
                String newTitle = res.get().trim();
                chatHistoryStore.renameSession(item.getId(), newTitle);
                if (currentSession != null && item.getId().equals(currentSession.getId())) {
                    currentSession.setTitle(newTitle);
                }
                refreshSessionsList(item.getId());
                ToastService.info("Đã đổi tên cuộc trò chuyện.");
            } catch (IOException e) {
                AiLoggingService.warn("AI-CHAT", "Không đổi tên được cuộc trò chuyện: " + e.getMessage());
            }
        }
    }

    private void handleDeleteSession(AiChatSessionMeta item) {
        if (item == null) return;
        if (isSessionRunning(item.getId())) {
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
            try {
                chatHistoryStore.deleteSession(item.getId());
                runRegistry.forget(item.getId());
                ToastService.info("Đã xóa cuộc trò chuyện.");
                if (currentSession != null && item.getId().equals(currentSession.getId())) {
                    currentSession = null;
                }
                refreshActivityIndicators();
                loadChatHistoryForSuite();
            } catch (IOException e) {
                AiLoggingService.warn("AI-CHAT", "Không xóa được cuộc trò chuyện: " + e.getMessage());
            }
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

    /**
     * Delegates a request to rule-bound sub-agents and then still produces the artefact the user asked
     * for: each agent's turn lands in the transcript as it finishes, and the structured generator runs
     * afterwards so the single aggregated answer carries real test cases instead of prose alone.
     *
     * @return the generation result attached to the aggregated answer, or {@code null} when the request
     *         should keep the single-assistant path (not delegable, or no plan could be produced) —
     *         delegation must never swallow a request a user could otherwise have answered
     */
    private AiTestGeneratorService.GenerationResult delegateToAgents(AiChatSession session, String prompt,
                                     AiConfig aiConfig, boolean generateScript,
                                     List<DocumentParserService.ParsedDocument> docs,
                                     String domJson, String suiteContext,
                                     AiAmbiguityAnalyzer.Intent intent,
                                     AiSkill skill,
                                     Path suiteDir) throws IOException, InterruptedException {
        boolean hasDocs = docs != null && !docs.isEmpty();
        boolean hasDom = domJson != null && !domJson.isBlank() && !"[]".equals(domJson);
        if (!AiDelegationPolicy.shouldDelegate(intent, hasDocs, hasDom, prompt)) {
            AiLoggingService.info("AI-COORDINATOR", "Bỏ qua điều phối: intent=" + intent
                    + ", docs=" + hasDocs + ", dom=" + hasDom + " (dùng trợ lý đơn có grounding)");
            return null;
        }
        String grounding = aiGenerator.buildSubAgentGrounding(prompt, docs, domJson, suiteContext,
                aiConfig.getMaxTokens());
        OpenAiClient client = new OpenAiClient();

        AiCoordinatorAgent coordinator = new AiCoordinatorAgent(userRequest -> {
            try {
                return completeUntilDone(client, aiConfig, List.of(
                                new OpenAiClient.ChatMessage("system", new AiCoordinatorAgent(
                                        r -> "{}").plannerSystemPrompt()),
                                new OpenAiClient.ChatMessage("user", userRequest + "\n\n" + grounding)),
                        OpenAiClient.defaultOutputTokensFor(false),
                        AiContinuation.DocumentShape.JSON,
                        "Bộ lập kế hoạch",
                        new ArrayList<>()).content();
            } catch (Exception e) {
                // Returning null makes the coordinator surface an invalid plan, which the caller turns
                // into the single-assistant fallback instead of losing the user's request.
                AiLoggingService.warn("AI-COORDINATOR",
                        "Không gọi được bộ lập kế hoạch: " + e.getMessage());
                return null;
            }
        });

        List<AiSubAgentOrchestrator.SubAgentTask> tasks;
        try {
            tasks = coordinator.toTasks(coordinator.plan(prompt));
        } catch (Exception e) {
            AiLoggingService.info("AI-COORDINATOR", "Bỏ qua điều phối, dùng trợ lý đơn: " + e.getMessage());
            return null;
        }
        if (tasks.isEmpty()) {
            return null;
        }

        AiLlmAgentExecutor agentExecutor = new AiLlmAgentExecutor(
                aiConfig,
                (AiConfig cfg, List<AiChatMessage> messages) -> {
                    List<String> continuationLog = new ArrayList<>();
                    OpenAiClient.Completion reply = completeUntilDone(client, cfg, List.of(
                                    new OpenAiClient.ChatMessage(messages.get(0).getRole(), messages.get(0).getContent()),
                                    new OpenAiClient.ChatMessage(messages.get(1).getRole(), messages.get(1).getContent())),
                            OpenAiClient.defaultOutputTokensFor(generateScript),
                            AiContinuation.DocumentShape.PROSE,
                            "Sub-agent",
                            continuationLog);
                    // Report the real call count and summed provider usage, not a fixed "1 call, unknown tokens".
                    return new AiLlmAgentExecutor.MeasuredReply(
                            reply.content(),
                            new AiLlmAgentExecutor.AgentUsage(1 + continuationLog.size(),
                                    reply.promptTokens(), reply.completionTokens()));
                },
                grounding);
        AiSubAgentOrchestrator.AgentExecutor executor = agentExecutor;

        // The user's turn is already recorded by sendPrompt; adding it again duplicated every prompt.
        publishRunStatus(session, "🤖 Coordinator đang giao việc cho " + tasks.size() + " tác nhân...");
        AiSubAgentOrchestrator orchestrator = new AiSubAgentOrchestrator(
                executor,
                status -> publishRunStatus(session, status),
                msg -> appendLiveTranscriptMessage(session, msg));
        AiSubAgentOrchestrator.OrchestrationResult orchestration = orchestrator.orchestrate(session, tasks);

        // The agents design the solution; the artefact still has to be produced. Without this pass the
        // turn ended in prose and the user received no test case at all.
        publishRunStatus(session,
                "🧪 [Test Architect] Đang sinh bộ Test Case có cấu trúc từ kết quả phân tích...");
        AiTestGeneratorService.GenerationResult result = aiGenerator.generateTestCases(
                aiConfig, prompt, docs, suiteContext, domJson,
                historyWithoutAgentTurns(session),
                status -> publishRunStatus(session, status),
                skill, suiteDir, generateScript);
        completeAggregateWithArtifacts(session, orchestration, result);
        return result;
    }

    /**
     * Publishes a progress line to the run of one conversation. Background workers of different
     * conversations update only their own run state and never touch another session's transcript.
     */
    private void publishRunStatus(AiChatSession session, String status) {
        if (session == null || status == null || status.isBlank()) {
            return;
        }
        String sessionId = session.getId();
        Platform.runLater(() -> {
            runRegistry.updateStatus(sessionId, status);
            if (isCurrentSession(session)) {
                updateThinkingStatus(status);
            }
        });
    }

    /**
     * Shows one agent message as soon as it is recorded. The final aggregated answer is skipped: it is
     * still being completed with the generated cases and is rendered once at end of turn. A message from
     * a conversation the user is not looking at is recorded on disk without touching this transcript.
     */
    private void appendLiveTranscriptMessage(AiChatSession session, AiChatMessage msg) {
        if (session == null || msg == null || msg.isAggregatedResult()) {
            return;
        }
        Platform.runLater(() -> {
            if (!isCurrentSession(session) || !session.getMessages().contains(msg)) {
                return;
            }
            // Keep the thinking indicator last so progress stays visible under the new bubble.
            boolean bubbleAttached = thinkingBubbleWrapper != null
                    && chatMessagesBox.getChildren().contains(thinkingBubbleWrapper);
            if (bubbleAttached) {
                chatMessagesBox.getChildren().remove(thinkingBubbleWrapper);
            }
            renderMessageBubble(msg, true);
            if (bubbleAttached) {
                chatMessagesBox.getChildren().add(thinkingBubbleWrapper);
            }
        });
    }

    /** True when the given session is the one currently rendered in the chat pane. */
    private boolean isCurrentSession(AiChatSession session) {
        return session != null && currentSession != null && session.getId().equals(currentSession.getId());
    }

    /** History handed to the generator: the agent transcript is evidence, not conversation context. */
    private static List<AiChatMessage> historyWithoutAgentTurns(AiChatSession session) {
        List<AiChatMessage> history = new ArrayList<>();
        if (session == null) {
            return history;
        }
        for (AiChatMessage m : session.getMessages()) {
            if (m != null && !m.isAgentMessage() && !m.isAggregatedResult()) {
                history.add(m);
            }
        }
        return history;
    }

    /**
     * Replaces the aggregated answer's prose summary with the real deliverable: the generated cases, the
     * functional group/suite, the clarification questions (when the turn asked for scope) and any
     * orchestration findings. Keeping it as ONE message is what makes the final answer unambiguous.
     */
    private void completeAggregateWithArtifacts(AiChatSession session,
                                                AiSubAgentOrchestrator.OrchestrationResult orchestration,
                                                AiTestGeneratorService.GenerationResult result) {
        if (session == null || result == null) {
            return;
        }
        AiChatMessage aggregate = latestAggregate(session);
        if (aggregate == null) {
            aggregate = new AiChatMessage("assistant", "");
            aggregate.setAggregatedResult(true);
            session.addMessage(aggregate);
        }
        aggregate.setContent(result.getSummary());
        applyGenerationResult(aggregate, result);

        String warning = completenessWarningOf(result);
        if (orchestration != null && !orchestration.openFindings().isEmpty()) {
            String findings = "⚠️ Ghi chú điều phối:\n- " + String.join("\n- ", orchestration.openFindings());
            warning = warning == null || warning.isBlank() ? findings : warning + "\n" + findings;
        }
        aggregate.setCompletenessWarning(warning);
        aggregate.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(aggregate));
    }

    private static AiChatMessage latestAggregate(AiChatSession session) {
        List<AiChatMessage> msgs = session.getMessages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            AiChatMessage m = msgs.get(i);
            if (m != null && m.isAggregatedResult()) {
                return m;
            }
        }
        return null;
    }

    /** Copies the artefact-bearing fields of a generation result onto its chat message. */
    private void applyGenerationResult(AiChatMessage msg, AiTestGeneratorService.GenerationResult result) {
        if (result.isClarification()) {
            msg.setClarificationQuestions(result.getQuestions());
        }
        msg.setGeneratedCasesSnapshot(result.getCases());
        msg.setSuggestedGroup(result.getSuggestedGroup());
        msg.setSuggestedSuite(result.getSuggestedSuite());
        msg.setIntent(result.getIntent());
    }

    /**
     * One model call that keeps fetching the tail when the provider stops on the output limit.
     *
     * <p>The planner and the sub-agents used to issue a single request and use whatever came back, so an
     * answer that overran {@code max_tokens} silently lost its second half. The continuation loop is the
     * same one test-case generation uses, with the document shape deciding how completeness is judged.</p>
     *
     * @param continuationLog receives each appended piece, so the caller can report the real call count
     */
    private OpenAiClient.Completion completeUntilDone(OpenAiClient client, AiConfig cfg,
                                                      List<OpenAiClient.ChatMessage> messages,
                                                      int outputBudget,
                                                      AiContinuation.DocumentShape shape,
                                                      String label,
                                                      List<String> continuationLog) throws Exception {
        int ceiling = AiModelCapabilities.resolveOutputCeiling(cfg);
        int budget = outputBudget > 0 ? Math.min(outputBudget, ceiling) : ceiling;
        OpenAiClient.Completion first = client.completeChat(cfg, messages, budget);
        if (!first.truncated()) {
            return first;
        }
        AiLoggingService.warn("AI-CONTINUE", label
                + " chạm trần output tokens; nối tiếp để lấy trọn phản hồi (shape=" + shape + ")");
        return AiContinuation.continueUntilComplete(cfg, messages, first, budget, shape,
                (turnMessages, turnBudget) -> client.completeChat(cfg, turnMessages, turnBudget),
                continuationLog);
    }

    private void renderChatHistory() {
        // The transcript is rebuilt from the session, so any bubble left from the previous render is
        // detached; a run still active in this session gets a fresh indicator at the end.
        removeThinkingBubble();
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

        syncRunBubbleForCurrentSession();
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    /** Recreates the "đang chạy" indicator for the open conversation after a re-render or a switch. */
    private void syncRunBubbleForCurrentSession() {
        if (currentSession == null) {
            return;
        }
        AiChatRunRegistry.Run run = runRegistry.getRun(currentSession.getId());
        if (run == null) {
            return;
        }
        showThinkingBubble(resolveConfiguredModelName());
        if (thinkingStatusLabel != null) {
            thinkingStatusLabel.setText(run.getStatus());
        }
    }

    private String resolveConfiguredModelName() {
        try {
            return settingsStore.load().getAiConfig().getEffectiveModel();
        } catch (Exception e) {
            AiLoggingService.warn("AI-CHAT", "Không đọc được cấu hình mô hình: " + e.getMessage());
            return AiConfig.DEFAULT_MODEL;
        }
    }

    private void restoreLatestPreviewCases() {
        List<AiChatMessage> msgs = currentSession.getMessages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            AiChatMessage m = msgs.get(i);
            if ("assistant".equalsIgnoreCase(m.getRole()) && !m.getGeneratedCasesSnapshot().isEmpty()) {
                currentGeneratedCases.clear();
                currentGeneratedCases.addAll(m.getGeneratedCasesSnapshot());
                currentSuggestedGroup = m.getSuggestedGroup();
                currentSuggestedSuite = m.getSuggestedSuite();
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
        currentSuggestedGroup = null;
        currentSuggestedSuite = null;
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
        tokenUsageBadge.setText("Tokens: ~" + total + limitStr + " (Prompt: ~" + promptTokens + ")"
                + orchestrationUsageSuffix(currentSession));
    }

    /**
     * Real cost of the session's orchestration: how many agents ran, how many model calls they made and
     * the provider-reported input/output tokens. Empty for a session with no sub-agents, so a plain
     * chat keeps its existing badge.
     */
    static String orchestrationUsageSuffix(AiChatSession session) {
        if (session == null || !session.hasSubAgents()) {
            return "";
        }
        int agents = 0;
        int calls = 0;
        int in = 0;
        int out = 0;
        for (AiSubAgentSession child : session.getSubAgents()) {
            if (child == null) continue;
            agents++;
            calls += parseIntOrZero(child.getMetrics().get("llmCalls"));
            in += Math.max(0, parseIntOrZero(child.getMetrics().get("promptTokens")));
            out += Math.max(0, parseIntOrZero(child.getMetrics().get("completionTokens")));
        }
        if (agents == 0) {
            return "";
        }
        return "  •  " + agents + " agent · " + calls + " LLM call · ↑" + in + " / ↓" + out + " tokens";
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
        if (currentSession == null) {
            return;
        }
        if (isSessionRunning(currentSession.getId())) {
            ToastService.warn("⚠️ Tác nhân AI đang chạy trong cuộc trò chuyện này. Vui lòng đợi hoàn tất trước khi xóa.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa lịch sử");
        alert.setHeaderText("Xóa tin nhắn trong cuộc trò chuyện");
        alert.setContentText("Bạn có chắc chắn muốn xóa tin nhắn trong cuộc trò chuyện \"" + currentSession.getTitle() + "\" không?");
        DialogTheme.apply(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            removeThinkingBubble();
            currentSession.getMessages().clear();
            try {
                chatHistoryStore.saveSession(currentSession);
            } catch (IOException e) {
                AiLoggingService.warn("AI-CHAT", "Không lưu được trạng thái xóa chat: " + e.getMessage());
                ToastService.error("Không lưu được trạng thái xóa: " + e.getMessage());
            }
            refreshSessionsList(currentSession.getId());
            renderChatHistory();
            updateTokenUsageDisplay();
            updateTargetControlsLock();
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
        if (currentSession == null) {
            currentSession = chatHistoryStore.createNewSession(null, "Cuộc trò chuyện mới");
        }
        if (isSessionRunning(currentSession.getId())) {
            ToastService.warn("⚠️ Tác nhân AI đang xử lý tin nhắn trước đó trong cuộc trò chuyện này."
                    + " Hãy tạo cuộc trò chuyện mới để chạy song song.");
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

        // Capture the save target exactly once, with the first prompt. From then on it belongs to the
        // conversation and the pickers are locked, so the worker can never be re-pointed mid-run.
        final AiChatSession targetSession = currentSession;
        final boolean firstPrompt = !targetSession.hasUserMessages();
        if (firstPrompt) {
            targetSession.setTargetGroup(normalizeTarget(targetGroupCombo != null ? targetGroupCombo.getValue() : null));
            targetSession.setTargetSuite(normalizeTarget(targetSuiteCombo != null ? targetSuiteCombo.getValue() : null));
        }
        final String targetGroupName = normalizeTarget(targetSession.getTargetGroup());
        final String targetSuiteName = normalizeTarget(targetSession.getTargetSuite());
        final Path targetSuiteDir = resolveSuiteDir(targetGroupName, targetSuiteName);

        // Create User Message
        StringBuilder userChatText = new StringBuilder();
        if (activeSkill != com.studio.model.AiSkill.AUTO) {
            userChatText.append("[").append(activeSkill.getDisplayName()).append("]\n");
        }
        userChatText.append(prompt);
        List<String> fileNames = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        List<AiChatMessage.AttachedDocSnapshot> docsSnapshotList = new ArrayList<>();
        if (!attachedDocuments.isEmpty()) {
            userChatText.append("\n\nĐã đính kèm: ");
            for (var d : attachedDocuments) {
                userChatText.append(d.getFileName()).append(" ");
                fileNames.add(d.getFileName());
                if (d.getPath() != null) {
                    filePaths.add(d.getPath().toString());
                }
                docsSnapshotList.add(new AiChatMessage.AttachedDocSnapshot(
                        d.getFileName(),
                        d.getPath() != null ? d.getPath().toString() : null,
                        d.isImage(),
                        d.getContent(),
                        d.getMimeType(),
                        d.getSizeBytes(),
                        d.getMarkdownPath() != null ? d.getMarkdownPath().toString() : null
                ));
            }
        }

        AiChatMessage userMsg = new AiChatMessage("user", userChatText.toString());
        userMsg.setRawPrompt(prompt);
        userMsg.setSkill(activeSkill != null ? activeSkill.name() : null);
        userMsg.setAttachedFiles(fileNames);
        userMsg.setAttachedFilePaths(filePaths);
        userMsg.setAttachedDocsSnapshot(docsSnapshotList);
        userMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(userMsg));
        targetSession.addMessage(userMsg);

        if ("Cuộc trò chuyện mới".equalsIgnoreCase(targetSession.getTitle())) {
            String cleanPrompt = prompt.replace("\n", " ").trim();
            if (cleanPrompt.length() > 28) {
                cleanPrompt = cleanPrompt.substring(0, 28) + "...";
            }
            if (!cleanPrompt.isBlank()) {
                targetSession.setTitle(cleanPrompt);
            }
        }

        // Persist the new turn BEFORE the worker starts: a crash or a page switch must not lose the
        // history entry that the running agent is writing into.
        try {
            chatHistoryStore.saveSession(targetSession);
            refreshSessionsList(targetSession.getId());
        } catch (IOException e) {
            AiLoggingService.warn("AI-CHAT", "Không lưu được tin nhắn mới: " + e.getMessage());
            ToastService.error("Không lưu được lịch sử chat: " + e.getMessage());
        }

        renderMessageBubble(userMsg, true);
        updateTargetControlsLock();

        // Snapshot attached docs and clear input
        List<DocumentParserService.ParsedDocument> docsSnapshot = new ArrayList<>(attachedDocuments);
        promptInputArea.clear();
        clearAttachments();
        updateTokenUsageDisplay();

        executePromptInternal(targetSession, targetSuiteDir, targetGroupName, targetSuiteName,
                prompt, docsSnapshot, activeSkill);
    }

    private static String normalizeTarget(String value) {
        return value == null ? "" : value.trim();
    }

    private void executePromptInternal(AiChatSession targetSession, Path suiteDir, String targetGroupName,
            String targetSuiteName, String prompt,
            List<DocumentParserService.ParsedDocument> docsSnapshot,
            com.studio.model.AiSkill activeSkill) {
        SettingsStore.Settings settings;
        try {
            settings = settingsStore.load();
        } catch (IOException e) {
            settings = new SettingsStore.Settings();
        }
        AiConfig aiConfig = settings.getAiConfig();

        final String targetSessionId = targetSession.getId();
        final String initialStatus = aiConfig.getEffectiveModel() + " — " + activeSkill.getDisplayName();
        runRegistry.startRun(targetSession, initialStatus);
        refreshActivityIndicators();
        updateComposerRunState();
        if (isCurrentSession(targetSession)) {
            showThinkingBubble(initialStatus);
        }

        String groupVal = targetGroupName.isBlank() ? "(Tự động tạo khi lưu)" : targetGroupName;
        String suiteVal = targetSuiteName.isBlank() ? "(Tự động tạo khi lưu)" : targetSuiteName;
        String context = "Nhóm đích: " + groupVal + ", Suite đích: " + suiteVal;

        // Tự động phát hiện URL từ prompt hoặc từ tài liệu đính kèm nếu chưa có Live DOM
        String promptUrl = com.studio.service.LiveDomScanner.extractFirstUrl(prompt);
        if (promptUrl == null && docsSnapshot != null && !docsSnapshot.isEmpty()) {
            for (DocumentParserService.ParsedDocument doc : docsSnapshot) {
                if (doc.getContent() != null) {
                    promptUrl = com.studio.service.LiveDomScanner.extractFirstUrl(doc.getContent());
                    if (promptUrl != null)
                        break;
                }
            }
        }
        final String detectedTargetUrl = promptUrl;

        AiAmbiguityAnalyzer.Intent detectedIntent = aiGenerator.getAmbiguityAnalyzer().detectIntent(prompt);
        boolean isBothIntent = (detectedIntent == AiAmbiguityAnalyzer.Intent.GENERATE_BOTH);
        boolean isScenarioIntent = (detectedIntent == AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS);

        if (isBothIntent) {
            // Khi người dùng yêu cầu CẢ HAI (Kịch bản + Script): Bật tự động sinh script
            if (autoGenerateScriptCheck != null && !autoGenerateScriptCheck.isSelected()) {
                autoGenerateScriptCheck.setSelected(true);
            }
        } else if (isScenarioIntent) {
            // Khi người dùng yêu cầu tạo test scenario/kịch bản: Tắt tự động sinh script
            if (autoGenerateScriptCheck != null && autoGenerateScriptCheck.isSelected()) {
                autoGenerateScriptCheck.setSelected(false);
            }
        } else if (AiAmbiguityAnalyzer.containsScriptOrAutomationRequest(prompt)) {
            // Khi người dùng yêu cầu tạo test case / test script / code automation: Bật tự động sinh script
            if (autoGenerateScriptCheck != null && !autoGenerateScriptCheck.isSelected()) {
                autoGenerateScriptCheck.setSelected(true);
            }
        } else if (detectedTargetUrl != null && autoGenerateScriptCheck != null && !autoGenerateScriptCheck.isSelected()) {
            autoGenerateScriptCheck.setSelected(true);
        }

        boolean generateScript;
        if (isBothIntent) {
            generateScript = true;
        } else if (isScenarioIntent) {
            generateScript = false;
        } else if (AiAmbiguityAnalyzer.containsScriptOrAutomationRequest(prompt)) {
            generateScript = true;
        } else if (autoGenerateScriptCheck != null) {
            generateScript = autoGenerateScriptCheck.isSelected();
        } else {
            generateScript = (detectedTargetUrl != null || (liveDomJson != null && !liveDomJson.isBlank() && !"[]".equals(liveDomJson)));
        }
        final boolean effectiveGenerateScript = generateScript;
        String domContext = liveDomJson;
        final AiConfig effectiveAiConfig = aiConfig;

        Thread thread = new Thread(() -> {
            try {
                String activeDomContext = domContext;
                // Nếu chưa có DOM context mà phát hiện URL hợp lệ -> Tự động quét Live DOM
                // ngầm (chỉ quét khi không phải là kịch bản thuần túy)!
                if (!isScenarioIntent
                        && (activeDomContext == null || activeDomContext.isBlank() || "[]".equals(activeDomContext))
                        && detectedTargetUrl != null) {
                    publishRunStatus(targetSession,
                            "Đang tự động quét cấu trúc Live DOM từ " + detectedTargetUrl + "...");
                    try {
                        String autoScannedDom = domScanner.scanUrl(detectedTargetUrl, 10000);
                        if (autoScannedDom != null && !autoScannedDom.isBlank() && !"[]".equals(autoScannedDom)) {
                            activeDomContext = autoScannedDom;
                            liveDomJson = autoScannedDom; // Cache cho phiên làm việc
                            Platform.runLater(
                                     () -> ToastService.info("🌐 Đã tự động nạp Live DOM từ " + detectedTargetUrl));
                        }
                    } catch (Exception ex) {
                        AiLoggingService.warn("AI-CHAT",
                                "Tự động quét DOM ngầm thất bại cho " + detectedTargetUrl + ": " + ex.getMessage());
                    }
                }

                // Điều phối đa tác nhân (khi yêu cầu cần tài liệu và không phải lượt cần làm rõ): mỗi
                // tác nhân hiện ngay khi xong, sau đó bộ sinh test case có cấu trúc vẫn chạy để trả về
                // artefact thật thay vì chỉ văn xuôi.
                boolean needsClarification = generationNeedsClarification(
                        prompt, docsSnapshot, activeDomContext, activeSkill, context);
                AiTestGeneratorService.GenerationResult result = null;
                boolean delegatedRun = false;
                if (!needsClarification) {
                    result = delegateToAgents(targetSession, prompt, effectiveAiConfig, effectiveGenerateScript,
                            docsSnapshot, activeDomContext, context, detectedIntent, activeSkill, suiteDir);
                    delegatedRun = result != null;
                } else {
                    AiLoggingService.info("AI-COORDINATOR",
                            "Bỏ qua điều phối: lượt này cần làm rõ phạm vi trước khi sinh artefact");
                }

                if (result == null) {
                    result = aiGenerator.generateTestCases(
                            effectiveAiConfig, prompt, docsSnapshot, context, activeDomContext,
                            historyWithoutAgentTurns(targetSession),
                            status -> publishRunStatus(targetSession, status),
                            activeSkill,
                            suiteDir,
                            effectiveGenerateScript);
                }

                final AiTestGeneratorService.GenerationResult generationResult = result;
                final AiChatMessage delegatedAnswer = delegatedRun ? latestAggregate(targetSession) : null;

                Platform.runLater(() -> {
                    finishRun(targetSession);

                    // The delegated pass already recorded exactly one aggregated answer and filled it with
                    // the generated cases; adding a second one duplicated the final deliverable.
                    AiChatMessage assistantMsg;
                    if (delegatedAnswer != null) {
                        assistantMsg = delegatedAnswer;
                    } else {
                        assistantMsg = new AiChatMessage("assistant", generationResult.getSummary());
                        assistantMsg.setCompletenessWarning(completenessWarningOf(generationResult));
                        applyGenerationResult(assistantMsg, generationResult);
                        assistantMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(assistantMsg));
                        targetSession.addMessage(assistantMsg);
                    }

                    // Persist updated history
                    try {
                        chatHistoryStore.saveSession(targetSession);
                    } catch (IOException e) {
                        AiLoggingService.warn("AI-CHAT",
                                "Không lưu được kết quả phiên " + targetSessionId + ": " + e.getMessage());
                    }

                    boolean isCurrentActive = isCurrentSession(targetSession);
                    if (isCurrentActive) {
                        // Re-render instead of appending: the live agent bubbles are already on screen and
                        // the aggregated answer was completed after they appeared.
                        renderChatHistory();
                        updateTokenUsageDisplay();

                        resultSummaryLabel.setText(generationResult.getSummary());

                        currentGeneratedCases.clear();
                        currentGeneratedCases.addAll(generationResult.getCases());
                        currentSuggestedGroup = generationResult.getSuggestedGroup();
                        currentSuggestedSuite = generationResult.getSuggestedSuite();

                        generatedCasesListView.getItems().setAll(currentGeneratedCases);
                        rawResponseViewer.setText(generationResult.getRawResponse());

                        if (!currentGeneratedCases.isEmpty()) {
                            applyToSuiteBtn.setDisable(false);
                            if (exportCasesMenuBtn != null) {
                                exportCasesMenuBtn.setDisable(false);
                            }
                            generatedCasesListView.getSelectionModel().select(0);
                            String unitLabel = isBothIntent
                                    ? "kịch bản & test scripts tự động hóa (CẢ HAI)"
                                    : (isScenarioIntent ? "kịch bản kiểm thử (Test Scenarios)" : "test cases");
                            ToastService.success("✨ Đã tạo " + currentGeneratedCases.size() + " " + unitLabel + " thành công!");
                        } else {
                            applyToSuiteBtn.setDisable(true);
                            if (exportCasesMenuBtn != null) {
                                exportCasesMenuBtn.setDisable(true);
                            }
                            showCaseDetail(null);
                            if (!generationResult.isClarification()) {
                                ToastService.info("💬 Trợ lý AI: " + ("SEARCH_TESTS".equals(generationResult.getIntent())
                                        ? "Đã tìm thấy test cases phù hợp."
                                        : "EVALUATE_TEST".equals(generationResult.getIntent()) ? "Đã phân tích đánh giá test case."
                                                : "CHAT_ADVICE".equals(generationResult.getIntent()) ? "Đã phản hồi tư vấn kỹ thuật."
                                                        : "Đã phản hồi yêu cầu."));
                            }
                        }
                    } else {
                        ToastService.success("✨ Phiên \"" + targetSession.getTitle() + "\" đã hoàn thành sinh kịch bản!");
                    }
                    refreshSessionsList(currentSession != null ? currentSession.getId() : null);
                });
            } catch (Exception ex) {
                AiLoggingService.error("AI-CHAT",
                        "Lượt sinh kịch bản của phiên " + targetSessionId + " thất bại: " + ex.getMessage());
                Platform.runLater(() -> {
                    finishRun(targetSession);

                    AiChatMessage errorMsg = new AiChatMessage("assistant", "❌ Lỗi: " + ex.getMessage());
                    errorMsg.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(errorMsg));
                    targetSession.addMessage(errorMsg);
                    try {
                        chatHistoryStore.saveSession(targetSession);
                    } catch (IOException saveFailure) {
                        AiLoggingService.warn("AI-CHAT",
                                "Không lưu được lỗi của phiên " + targetSessionId + ": " + saveFailure.getMessage());
                    }

                    if (isCurrentSession(targetSession)) {
                        renderMessageBubble(errorMsg, true);
                        updateTokenUsageDisplay();
                        ToastService.error("Lỗi AI: " + ex.getMessage());
                    } else {
                        ToastService.error("Lỗi AI ở phiên \"" + targetSession.getTitle() + "\": " + ex.getMessage());
                    }
                    refreshSessionsList(currentSession != null ? currentSession.getId() : null);
                });
            }
        }, "AiAssistant-Worker-" + shortSessionId(targetSessionId));
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Ends one conversation's run. A turn that completed while the user was reading another
     * conversation leaves its result flagged as unseen until that conversation is opened.
     */
    private void finishRun(AiChatSession targetSession) {
        if (targetSession == null) {
            return;
        }
        String viewedSessionId = currentSession != null ? currentSession.getId() : null;
        runRegistry.finishRun(targetSession.getId(), viewedSessionId);
        refreshActivityIndicators();
        if (isCurrentSession(targetSession)) {
            removeThinkingBubble();
            updateComposerRunState();
        }
    }

    private static String shortSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "unknown";
        }
        return sessionId.length() > 8 ? sessionId.substring(0, 8) : sessionId;
    }

    /** Enables/disables the composer according to the run state of the conversation on screen. */
    private void updateComposerRunState() {
        boolean running = currentSession != null && isSessionRunning(currentSession.getId());
        if (sendBtn != null) {
            sendBtn.setDisable(running);
            sendBtn.setText(running ? "Đang sinh kịch bản..." : "Gửi (Enter)");
        }
        if (generationProgress != null) {
            generationProgress.setVisible(running);
            generationProgress.setManaged(running);
        }
    }

    /** True while a worker of this conversation is generating (also drives the history badge). */
    public boolean isSessionRunning(String sessionId) {
        return runRegistry.isRunning(sessionId);
    }

    /**
     * Mirrors the generator's own clarification gate. A turn that must ask the user about scope is never
     * delegated: running four agents first only to end with a question wastes the user's tokens.
     */
    private boolean generationNeedsClarification(String prompt,
                                                 List<DocumentParserService.ParsedDocument> docs,
                                                 String domJson,
                                                 AiSkill skill,
                                                 String contextInfo) {
        boolean hasDocs = docs != null && !docs.isEmpty();
        boolean hasDom = domJson != null && !domJson.isBlank() && !"[]".equals(domJson);
        boolean hasExplicitSkillOrUrl = (skill != null && skill != AiSkill.AUTO)
                || AiTestGeneratorService.containsTargetUrl(prompt, docs, domJson, contextInfo);
        if (hasExplicitSkillOrUrl) {
            return false;
        }
        try {
            return aiGenerator.getAmbiguityAnalyzer()
                    .analyze(prompt, hasDocs, hasDom, docs)
                    .needsClarification();
        } catch (Exception e) {
            AiLoggingService.warn("AI-COORDINATOR", "Không phân tích được yêu cầu: " + e.getMessage());
            return false;
        }
    }

    private void showThinkingBubble(String statusText) {
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

        thinkingStatusLabel = new Label(statusText != null && !statusText.isBlank()
                ? statusText
                : "Đang kết nối tới mô hình AI...");
        thinkingStatusLabel.getStyleClass().add("ai-thinking-status-text");
        HBox.setHgrow(thinkingStatusLabel, Priority.ALWAYS);

        body.getChildren().addAll(spinner, thinkingStatusLabel);
        bubble.getChildren().addAll(header, body);

        thinkingBubbleWrapper = new HBox(bubble);
        thinkingBubbleWrapper.setAlignment(Pos.CENTER_LEFT);
        chatMessagesBox.getChildren().add(thinkingBubbleWrapper);
        Platform.runLater(() -> chatScroll.setVvalue(1.0));

        // Only the dots animate. Progress text is driven by the real run (publishRunStatus) — the old
        // canned 4-agent rotation overwrote those live statuses after a couple of seconds.
        final int[] dotStep = { 0 };
        thinkingTimeline = new Timeline(new KeyFrame(Duration.millis(500), ev -> {
            dotStep[0]++;
            int activeDot = dotStep[0] % 3;
            dot1.setOpacity(activeDot == 0 ? 1.0 : 0.3);
            dot2.setOpacity(activeDot == 1 ? 1.0 : 0.3);
            dot3.setOpacity(activeDot == 2 ? 1.0 : 0.3);
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
    public void applyCasesToSelectedSuite() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case nào để nhập vào Suite.");
            return;
        }

        String group = targetGroupCombo.getValue();
        String suite = targetSuiteCombo.getValue();

        // Tự động đặt tên Nhóm theo đúng chức năng & test case nếu chưa chọn hoặc đang để tên mặc định chung chung
        if (group == null || group.isBlank() || com.studio.service.AiMultiAgentCoordinator.isGenericOrBlank(group)) {
            group = resolveAutoGroupName();
        }

        // Tự động đặt tên Suite theo đúng chức năng & test case nếu chưa chọn hoặc đang để tên mặc định chung chung
        if (suite == null || suite.isBlank() || com.studio.service.AiMultiAgentCoordinator.isGenericOrBlank(suite)) {
            suite = resolveAutoSuiteName();
        }

        group = ProjectStore.sanitizeDirectoryName(group != null ? group.trim() : "");
        suite = ProjectStore.sanitizeDirectoryName(suite != null ? suite.trim() : "");

        Path projectDir = StudioPaths.getProjectDir();
        Path groupDir = projectDir.resolve(group);
        boolean isGroupNew = false;
        try {
            Files.createDirectories(groupDir);
            Path groupJson = groupDir.resolve("group.json");
            if (!Files.exists(groupJson)) {
                projectStore.createGroup(projectDir, group, "Nhóm chức năng: " + group + " (Tự động tạo theo đặc tả kiểm thử)");
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
                projectStore.createSuite(groupDir, suite, "Bộ kịch bản kiểm thử chức năng: " + suite, "READY", "", null);
                isSuiteNew = true;
            }
        } catch (IOException e) {
            ToastService.error("Không thể tạo thư mục Suite: " + e.getMessage());
            return;
        }

        // Cập nhật lại UI ComboBoxes nếu vừa tự động tạo (guarded: setting a value fires onAction)
        isApplyingSessionTarget = true;
        try {
            if (!targetGroupCombo.getItems().contains(group)) {
                targetGroupCombo.getItems().add(group);
            }
            targetGroupCombo.setValue(group);

            if (!targetSuiteCombo.getItems().contains(suite)) {
                targetSuiteCombo.getItems().add(suite);
            }
            targetSuiteCombo.setValue(suite);
        } finally {
            isApplyingSessionTarget = false;
        }
        // The applied target becomes the conversation's target, so reopening it shows the same choice.
        currentSession.setTargetGroup(group);
        currentSession.setTargetSuite(suite);
        updateTargetControlsLock();

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

            chatHistoryStore.saveSession(currentSession);
            refreshSessionsList(currentSession.getId());
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

    public String resolveAutoGroupName() {
        String latestPrompt = findLatestUserPrompt();
        return com.studio.service.AiMultiAgentCoordinator.resolveFunctionalGroupName(
                latestPrompt,
                attachedDocuments,
                currentGeneratedCases,
                currentSuggestedGroup
        );
    }

    public String resolveAutoSuiteName() {
        String latestPrompt = findLatestUserPrompt();
        String sessionTitle = (currentSession != null) ? currentSession.getTitle() : null;
        return com.studio.service.AiMultiAgentCoordinator.resolveFunctionalSuiteName(
                latestPrompt,
                sessionTitle,
                attachedDocuments,
                currentGeneratedCases,
                currentSuggestedSuite
        );
    }

    private String findLatestUserPrompt() {
        if (currentSession != null && currentSession.getMessages() != null) {
            List<AiChatMessage> msgs = currentSession.getMessages();
            for (int i = msgs.size() - 1; i >= 0; i--) {
                AiChatMessage m = msgs.get(i);
                if ("user".equalsIgnoreCase(m.getRole()) && m.getContent() != null && !m.getContent().isBlank()) {
                    return m.getContent();
                }
            }
        }
        if (promptInputArea != null && promptInputArea.getText() != null && !promptInputArea.getText().isBlank()) {
            return promptInputArea.getText();
        }
        return "";
    }

    private static void configureExportChooserDirectory(FileChooser chooser) {
        if (chooser == null) return;
        File exportsDir = StudioPaths.getExportsDir().toFile();
        if (exportsDir.exists() || exportsDir.mkdirs()) {
            chooser.setInitialDirectory(exportsDir);
        }
    }

    @FXML
    public void exportCasesToExcel() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case nào để tải về.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Bộ Test Cases ra Excel (.xlsx)");
        configureExportChooserDirectory(chooser);
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
        configureExportChooserDirectory(chooser);
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
        configureExportChooserDirectory(chooser);
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

    @FXML
    public void exportScriptsToZip() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Test Case/Script nào để tải về.");
            return;
        }
        long scriptCount = currentGeneratedCases.stream()
                .filter(c -> c.getScriptCode() != null && !c.getScriptCode().isBlank())
                .count();
        if (scriptCount == 0) {
            ToastService.warn("⚠️ Các ca kiểm thử hiện tại chưa có mã script tự động hóa. Vui lòng xuất kịch bản Excel/Markdown.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Mã Nguồn Playwright Java Scripts ra file ZIP");
        configureExportChooserDirectory(chooser);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Archive (*.zip)", "*.zip"));
        chooser.setInitialFileName("PlaywrightScripts_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".zip");

        File file = chooser.showSaveDialog(promptInputArea != null && promptInputArea.getScene() != null ? promptInputArea.getScene().getWindow() : null);
        if (file == null) return;

        try {
            int exported = TestCaseExcelExportService.exportScriptsToZip(currentGeneratedCases, file.toPath());
            ToastService.success("☕ Đã xuất " + exported + " mã Playwright Java test scripts ra file: " + file.getName());
        } catch (Exception ex) {
            AiLoggingService.error("EXPORT", "Xuất file Scripts ZIP thất bại: " + ex.getMessage());
            ToastService.error("❌ Xuất file Scripts ZIP thất bại: " + ex.getMessage());
        }
    }

    @FXML
    public void exportFullBundle() {
        if (currentGeneratedCases.isEmpty()) {
            ToastService.warn("⚠️ Chưa có Kịch bản hoặc Script nào để tải về.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu Trọn gói Cả 2 (Kịch bản QA & Mã Playwright Scripts) ra file ZIP");
        configureExportChooserDirectory(chooser);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Bundle (*.zip)", "*.zip"));
        chooser.setInitialFileName("FullQA_AutomationBundle_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".zip");

        File file = chooser.showSaveDialog(promptInputArea != null && promptInputArea.getScene() != null ? promptInputArea.getScene().getWindow() : null);
        if (file == null) return;

        try {
            TestCaseExcelExportService.exportFullBundle(
                    currentGeneratedCases,
                    resultSummaryLabel != null ? resultSummaryLabel.getText() : "Sinh bởi Trợ lý AI",
                    file.toPath(),
                    ExportLanguage.VIETNAMESE
            );
            ToastService.success("📦 Đã xuất trọn gói CẢ HAI (Kịch bản Excel/MD + Mã Java) ra file: " + file.getName());
        } catch (Exception ex) {
            AiLoggingService.error("EXPORT", "Xuất trọn gói thất bại: " + ex.getMessage());
            ToastService.error("❌ Xuất trọn gói thất bại: " + ex.getMessage());
        }
    }

    /**
     * Bubble header. A sub-agent message shows its agent name and role so the transcript
     * makes clear which agent is speaking; the aggregated answer is labelled as the final
     * result of the whole team.
     */
    private String bubbleHeader(AiChatMessage msg, String formattedTime) {
        String speaker;
        if (msg.isAgentMessage()) {
            String role = msg.getAgentRole();
            speaker = "🤖 " + msg.getAgentName() + (role != null && !role.isBlank() ? " · " + role : "");
        } else if (msg.isAggregatedResult()) {
            speaker = "✅ Điều phối · Kết quả tổng hợp";
        } else if ("user".equalsIgnoreCase(msg.getRole())) {
            speaker = "Bạn";
        } else {
            speaker = "Trợ lý AI";
        }
        // Sub-agent turns also carry the real cost of the run that produced them.
        if (msg.isAgentMessage()) {
            speaker += usageSuffix(msg);
        }
        return speaker + "  •  " + formattedTime + "  •  ~" + msg.getEstimatedTokens() + " tokens";
    }

    /**
     * Provider-reported usage for one agent turn, or a note that the provider reported none. The "~"
     * on the bubble's own token count marks a heuristic; these figures have no "~" because they are
     * what the API actually billed.
     */
    private static String usageSuffix(AiChatMessage msg) {
        if (!msg.hasReportedTokens()) {
            return "";
        }
        return "  •  ↑" + msg.getPromptTokens() + " / ↓" + msg.getCompletionTokens() + " tokens";
    }

    /**
     * One sentence for the assistant bubble when the suite is not provably complete: a reply that stayed cut
     * after every continuation turn, or a screen batch that never returned cases. Empty when nothing is wrong.
     */
    static String completenessWarningOf(AiTestGeneratorService.GenerationResult result) {
        if (result == null) {
            return "";
        }
        StringBuilder warning = new StringBuilder();
        String truncation = result.describeOutputTruncation();
        if (truncation != null && !truncation.isBlank()) {
            warning.append(truncation);
        }
        String lostBatches = result.describeLostBatches();
        if (lostBatches != null && !lostBatches.isBlank()) {
            if (warning.length() > 0) {
                warning.append("\n");
            }
            warning.append(lostBatches);
        }
        return warning.toString();
    }

    private void renderMessageBubble(AiChatMessage msg, boolean scrollToBottom) {
        String role = msg.getRole();
        VBox bubble = new VBox(6);
        bubble.setMaxWidth(520);
        bubble.getStyleClass().add("ai-chat-bubble");
        bubble.getStyleClass().add("user".equalsIgnoreCase(role) ? "ai-chat-bubble-user" : "ai-chat-bubble-assistant");
        if (msg.isAgentMessage()) {
            bubble.getStyleClass().add("ai-chat-bubble-agent");
        }
        if (msg.isAggregatedResult()) {
            bubble.getStyleClass().add("ai-chat-bubble-aggregate");
        }

        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(msg.getTimestamp()), ZoneId.systemDefault());
        String formattedTime = time.format(DateTimeFormatter.ofPattern("HH:mm"));

        // Header Row: Label + Spacer + Copy Button + Undo Button (user bubbles only)
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        String title = bubbleHeader(msg, formattedTime);
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

        // Undo Button. Only a user message carries it: undoing is "take back the prompt", and the
        // assistant turns it produced are removed with it (handleUndoMessage). An assistant bubble on
        // its own has nothing a user can take back.
        header.getChildren().addAll(headerLabel, copyBtn);
        if ("user".equalsIgnoreCase(role)) {
            Button undoBtn = new Button("Hoàn tác");
            undoBtn.getStyleClass().addAll("chat-action-btn", "chat-action-btn-danger");
            UiIcons.setGraphic(undoBtn, UiIcons.UNDO, 10);
            undoBtn.setOnAction(e -> handleUndoMessage(msg));
            header.getChildren().add(undoBtn);
        }

        // Content: user prompts stay verbatim, assistant/agent turns follow the density setting and
        // keep any hidden text behind a "Xem thêm" toggle.
        bubble.getChildren().addAll(header, buildContentBox(msg));

        if (msg.hasClarificationQuestions()) {
            VBox clarificationCard = buildClarificationCard(msg);
            bubble.getChildren().add(clarificationCard);
        }

        // A suite that is missing its tail (output cut) or an entire screen batch (batch lost) must not look
        // complete: the warning sits inside the same bubble, above the deliverable box it qualifies.
        String completenessWarning = msg.getCompletenessWarning();
        if (completenessWarning != null && !completenessWarning.isBlank()) {
            Label warningLabel = new Label(completenessWarning);
            warningLabel.setWrapText(true);
            warningLabel.setMaxWidth(480);
            warningLabel.getStyleClass().add("ai-chat-incomplete-warning");
            bubble.getChildren().add(warningLabel);
        }

        // Action Toolbar for Generated Deliverables (Scenarios / Cases & Scripts)
        if ("assistant".equalsIgnoreCase(role) && msg.getGeneratedCasesSnapshot() != null && !msg.getGeneratedCasesSnapshot().isEmpty()) {
            List<AiGeneratedCase> casesSnapshot = msg.getGeneratedCasesSnapshot();
            boolean hasScripts = casesSnapshot.stream().anyMatch(c -> c.getScriptCode() != null && !c.getScriptCode().isBlank());
            String deliverableTitle = hasScripts
                    ? "📦 Bộ " + casesSnapshot.size() + " Test Cases & Script Tự động hóa"
                    : "📋 Bộ " + casesSnapshot.size() + " Kịch bản kiểm thử (Test Scenarios chuẩn QA)";

            VBox actionBox = new VBox(6);
            actionBox.getStyleClass().add("ai-chat-deliverable-box");

            HBox titleRow = new HBox(6);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            Label boxTitle = new Label(deliverableTitle);
            boxTitle.getStyleClass().add("ai-chat-deliverable-title");
            titleRow.getChildren().add(boxTitle);

            HBox buttonsRow = new HBox(6);
            buttonsRow.setAlignment(Pos.CENTER_LEFT);

            if (hasScripts) {
                Button excelBtn = new Button("Excel (.xlsx)");
                excelBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
                UiIcons.setGraphic(excelBtn, UiIcons.EXCEL, 11);
                UiTooltips.install(excelBtn, "Tải tài liệu kịch bản ra file Excel theo chuẩn QA/ISTQB");
                excelBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    exportCasesToExcel();
                });

                Button scriptsZipBtn = new Button("Mã Java (.zip)");
                scriptsZipBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
                UiIcons.setGraphic(scriptsZipBtn, UiIcons.FILE, 11);
                UiTooltips.install(scriptsZipBtn, "Tải toàn bộ mã nguồn Playwright Java test scripts (.java) đóng gói trong file ZIP");
                scriptsZipBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    exportScriptsToZip();
                });

                Button bundleZipBtn = new Button("Trọn gói Cả 2 (.zip)");
                bundleZipBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
                UiIcons.setGraphic(bundleZipBtn, UiIcons.DOWNLOAD, 11);
                UiTooltips.install(bundleZipBtn, "Tải trọn gói CẢ HAI: Kịch bản kiểm thử QA (Excel + MD) và Toàn bộ Mã Java scripts (.zip)");
                bundleZipBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    exportFullBundle();
                });

                Button viewBtn = new Button("Xem danh sách");
                viewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
                UiIcons.setGraphic(viewBtn, UiIcons.SEARCH, 11);
                UiTooltips.install(viewBtn, "Xem chi tiết danh sách kịch bản ở khung bên phải");
                viewBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    generatedCasesListView.getItems().setAll(currentGeneratedCases);
                    resultSummaryLabel.setText(msg.getContent());
                    applyToSuiteBtn.setDisable(false);
                    if (exportCasesMenuBtn != null) {
                        exportCasesMenuBtn.setDisable(false);
                    }
                    generatedCasesListView.getSelectionModel().select(0);
                });

                buttonsRow.getChildren().addAll(excelBtn, scriptsZipBtn, bundleZipBtn, viewBtn);
            } else {
                Button excelBtn = new Button("Excel (.xlsx)");
                excelBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
                UiIcons.setGraphic(excelBtn, UiIcons.EXCEL, 11);
                UiTooltips.install(excelBtn, "Tải tài liệu kịch bản ra file Excel theo chuẩn QA/ISTQB");
                excelBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    exportCasesToExcel();
                });

                Button mdBtn = new Button("Markdown (.md)");
                mdBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
                UiIcons.setGraphic(mdBtn, UiIcons.FILE, 11);
                UiTooltips.install(mdBtn, "Tải tài liệu kịch bản ra file Markdown");
                mdBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    exportCasesToMarkdown();
                });

                Button jsonBtn = new Button("JSON");
                jsonBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
                UiIcons.setGraphic(jsonBtn, UiIcons.TERMINAL, 11);
                UiTooltips.install(jsonBtn, "Tải dữ liệu kịch bản ra định dạng JSON");
                jsonBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    exportCasesToJson();
                });

                Button viewBtn = new Button("Xem danh sách");
                viewBtn.getStyleClass().addAll("btn-primary", "btn-sm");
                UiIcons.setGraphic(viewBtn, UiIcons.SEARCH, 11);
                UiTooltips.install(viewBtn, "Xem chi tiết danh sách kịch bản ở khung bên phải");
                viewBtn.setOnAction(e -> {
                    currentGeneratedCases.clear();
                    currentGeneratedCases.addAll(casesSnapshot);
                    currentSuggestedGroup = msg.getSuggestedGroup();
                    currentSuggestedSuite = msg.getSuggestedSuite();
                    generatedCasesListView.getItems().setAll(currentGeneratedCases);
                    resultSummaryLabel.setText(msg.getContent());
                    applyToSuiteBtn.setDisable(false);
                    if (exportCasesMenuBtn != null) {
                        exportCasesMenuBtn.setDisable(false);
                    }
                    generatedCasesListView.getSelectionModel().select(0);
                });

                buttonsRow.getChildren().addAll(excelBtn, mdBtn, jsonBtn, viewBtn);
            }
            actionBox.getChildren().addAll(titleRow, buttonsRow);
            bubble.getChildren().add(actionBox);
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

    /**
     * Bubble body. The stored message is never rewritten: a long assistant/agent turn shows an excerpt
     * in Gọn/Tổng hợp mode and keeps the full text one click away. User prompts are shown verbatim
     * because hiding what the user typed is surprising.
     */
    private VBox buildContentBox(AiChatMessage msg) {
        String content = msg.getContent() != null ? msg.getContent() : "";
        ChatDisplayMode mode = "user".equalsIgnoreCase(msg.getRole()) ? ChatDisplayMode.DETAIL : chatDisplayMode;
        ChatDisplayMode.Excerpt excerpt = mode.excerpt(content);

        Label contentLabel = new Label(excerpt.text());
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("ai-chat-bubble-content");

        VBox box = new VBox(4);
        box.getChildren().add(contentLabel);
        if (!excerpt.truncated()) {
            return box;
        }

        Button toggle = new Button("Xem thêm");
        toggle.getStyleClass().add("chat-action-btn");
        boolean[] expanded = {false};
        toggle.setOnAction(e -> {
            expanded[0] = !expanded[0];
            contentLabel.setText(expanded[0] ? content : excerpt.text());
            toggle.setText(expanded[0] ? "Thu gọn" : "Xem thêm");
            Platform.runLater(() -> chatScroll.setVvalue(1.0));
        });
        box.getChildren().add(toggle);
        return box;
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
                // One generation at a time per conversation: a second click while a run is alive used
                // to start a parallel generation that raced on the same session file.
                if (currentSession != null && isSessionRunning(currentSession.getId())) {
                    ToastService.warn("⚠️ Đang sinh kịch bản — vui lòng chờ lượt hiện tại hoàn tất.");
                    return;
                }
                confirmBtn.setDisable(true);

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

                try {
                    chatHistoryStore.saveSession(currentSession);
                    refreshSessionsList(currentSession.getId());
                } catch (IOException saveFailure) {
                    ToastService.error("Không lưu được câu trả lời làm rõ: " + saveFailure.getMessage());
                }

                String augmented = aiGenerator.getAmbiguityAnalyzer().buildAugmentedPrompt(originalPrompt, collected);

                // Refresh UI to show chips disabled and badge (the run starts below and re-renders anyway)
                renderChatHistory();

                // Add user confirmation bubble and execute against the conversation's own target
                AiChatMessage userConfirm = new AiChatMessage("user", "Đã xác nhận lựa chọn chi tiết:\n" + augmented);
                userConfirm.setEstimatedTokens(tokenOptimizer.estimateMessageTokens(userConfirm));
                currentSession.addMessage(userConfirm);
                renderMessageBubble(userConfirm, true);
                // Persisted before the worker starts, exactly like a normal prompt: the confirmed turn
                // must survive a crash in the middle of generation.
                try {
                    chatHistoryStore.saveSession(currentSession);
                } catch (IOException saveFailure) {
                    AiLoggingService.warn("AI-CHAT",
                            "Không lưu được lượt xác nhận làm rõ: " + saveFailure.getMessage());
                }

                String group = normalizeTarget(currentSession.getTargetGroup());
                String suite = normalizeTarget(currentSession.getTargetSuite());
                executePromptInternal(currentSession, resolveSuiteDir(group, suite), group, suite,
                        augmented, List.of(), com.studio.model.AiSkill.AUTO);
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

    /**
     * The assistant reply that carries the suite snapshot an undo of {@code targetMsg} must roll back.
     * Undo works forward from the prompt: every assistant turn after the undone user message belongs to
     * that undo scope, so the LAST applied reply in the scope wins — its snapshot is the state before the
     * first apply of the scope, which also undoes the applies stacked on top of it.
     */
    public static AiChatMessage resolveAppliedSnapshotMessage(List<AiChatMessage> msgs, AiChatMessage targetMsg) {
        if (targetMsg == null) return null;
        if (targetMsg.isAppliedToSuite() && targetMsg.getSuiteSnapshotJson() != null) {
            return targetMsg;
        }
        if (msgs == null || !"user".equalsIgnoreCase(targetMsg.getRole())) {
            return null;
        }
        int idx = msgs.indexOf(targetMsg);
        if (idx < 0) {
            return null;
        }
        AiChatMessage applied = null;
        for (int i = idx + 1; i < msgs.size(); i++) {
            AiChatMessage m = msgs.get(i);
            if ("user".equalsIgnoreCase(m.getRole())) {
                break;
            }
            if ("assistant".equalsIgnoreCase(m.getRole())
                    && m.isAppliedToSuite()
                    && m.getSuiteSnapshotJson() != null) {
                applied = m;
            }
        }
        return applied;
    }

    /**
     * Undo scope of a user message: it plus every message after it, up to (not including) the next user
     * message. Messages before the undone prompt are never touched. Returns an empty list for anything
     * that is not a user message — assistant turns cannot be undone on their own.
     */
    public static List<AiChatMessage> undoScopeOf(List<AiChatMessage> msgs, AiChatMessage targetMsg) {
        List<AiChatMessage> scope = new ArrayList<>();
        if (msgs == null || targetMsg == null || !"user".equalsIgnoreCase(targetMsg.getRole())) {
            return scope;
        }
        int idx = msgs.indexOf(targetMsg);
        if (idx < 0) {
            return scope;
        }
        for (int i = idx; i < msgs.size(); i++) {
            AiChatMessage m = msgs.get(i);
            if (i > idx && "user".equalsIgnoreCase(m.getRole())) {
                break;
            }
            scope.add(m);
        }
        return scope;
    }

    public static Path resolveRollbackSuiteDir(AiChatMessage appliedMsg, Path fallbackSelectedSuiteDir) {
        if (appliedMsg != null && appliedMsg.getSuiteSnapshotDir() != null && !appliedMsg.getSuiteSnapshotDir().isBlank()) {
            return Path.of(appliedMsg.getSuiteSnapshotDir());
        }
        return fallbackSelectedSuiteDir;
    }

    public void handleUndoMessage(AiChatMessage targetMsg) {
        if (targetMsg == null)
            return;

        // Undo is the user taking a prompt back: only a user message can be undone, and everything the
        // assistant produced after it goes with it (undoScopeOf). An assistant turn has no undo of its own.
        if (!"user".equalsIgnoreCase(targetMsg.getRole())) {
            ToastService.warn("Chỉ có thể hoàn tác tin nhắn bạn đã gửi. Tin nhắn của Trợ lý AI được hoàn tác cùng với tin nhắn đã sinh ra nó.");
            return;
        }

        if (currentSession != null && isSessionRunning(currentSession.getId())) {
            ToastService.warn("⚠️ AI đang sinh kịch bản. Vui lòng đợi hoàn tất trước khi hoàn tác.");
            return;
        }

        List<AiChatMessage> msgs = currentSession.getMessages();
        if (!msgs.contains(targetMsg)) {
            return;
        }
        AiChatMessage appliedMsg = resolveAppliedSnapshotMessage(msgs, targetMsg);

        // 1. If applied to suite, ask for confirmation to rollback files
        if (appliedMsg != null) {
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

            Path rollbackDir = resolveRollbackSuiteDir(appliedMsg, getSelectedSuiteDir());
            try {
                chatHistoryStore.rollbackSuiteSnapshot(rollbackDir, appliedMsg.getSuiteSnapshotJson());
                ExplorerPageController explorer = ViewNavigator.getController(ViewNavigator.Page.EXPLORER, ExplorerPageController.class);
                if (explorer != null) {
                    explorer.reloadFromDisk();
                }
                ToastService.success("⚡ Đã khôi phục Suite về trạng thái trước khi áp dụng!");
            } catch (Exception ex) {
                ToastService.error("Lỗi khi khôi phục Suite: " + ex.getMessage());
            }
        }

        // 2. Remove the prompt and every assistant turn after it; earlier history stays untouched.
        msgs.removeAll(undoScopeOf(msgs, targetMsg));

        // Restore prompt content and attachments back to the chat composer
        restoreToInputArea(targetMsg);

        // 3. Persist and re-render
        try {
            chatHistoryStore.saveSession(currentSession);
            refreshSessionsList(currentSession.getId());
        } catch (IOException e) {
            AiLoggingService.warn("AI-CHAT", "Không lưu được trạng thái hoàn tác: " + e.getMessage());
        }

        renderChatHistory();
        updateTokenUsageDisplay();
        updateTargetControlsLock();
        ToastService.info("Đã hoàn tác tin nhắn và các phản hồi của Trợ lý AI sau đó.");
    }

    public AiChatSession getCurrentSession() {
        return currentSession;
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

    public TextField getSessionSearchField() {
        return sessionSearchField;
    }

    public Button getHistoryToggleBtn() {
        return toggleSidebarBtn;
    }

    public VBox getChatMessagesBox() {
        return chatMessagesBox;
    }

    public List<AiGeneratedCase> getCurrentGeneratedCases() {
        return currentGeneratedCases;
    }

    public String getCurrentSuggestedGroup() {
        return currentSuggestedGroup;
    }

    public void setCurrentSuggestedGroup(String currentSuggestedGroup) {
        this.currentSuggestedGroup = currentSuggestedGroup;
    }

    public String getCurrentSuggestedSuite() {
        return currentSuggestedSuite;
    }

    public void setCurrentSuggestedSuite(String currentSuggestedSuite) {
        this.currentSuggestedSuite = currentSuggestedSuite;
    }

    public TextArea getPromptInputArea() {
        return promptInputArea;
    }

    public Button getSendBtn() {
        return sendBtn;
    }

    public List<DocumentParserService.ParsedDocument> getAttachedDocuments() {
        return attachedDocuments;
    }

    public void restoreToInputArea(AiChatMessage msg) {
        if (msg == null) return;

        // 1. Restore prompt text
        String promptToRestore = msg.getRawPrompt();
        if (promptToRestore == null || promptToRestore.isBlank()) {
            promptToRestore = extractPromptFromUserContent(msg.getContent());
        }
        if (promptInputArea != null && promptToRestore != null) {
            promptInputArea.setText(promptToRestore);
            promptInputArea.positionCaret(promptInputArea.getText().length());
            promptInputArea.requestFocus();
        }

        // 2. Restore skill if applicable
        if (msg.getSkill() != null && skillComboBox != null) {
            try {
                com.studio.model.AiSkill s = com.studio.model.AiSkill.valueOf(msg.getSkill());
                skillComboBox.setValue(s);
            } catch (Exception ignored) {}
        }

        // 3. Clear existing attachments and restore from message
        clearAttachments();

        if (msg.getAttachedDocsSnapshot() != null && !msg.getAttachedDocsSnapshot().isEmpty()) {
            for (AiChatMessage.AttachedDocSnapshot snap : msg.getAttachedDocsSnapshot()) {
                Path filePath = snap.getFilePath() != null ? Path.of(snap.getFilePath()) : null;
                Path mdPath = snap.getMarkdownPath() != null ? Path.of(snap.getMarkdownPath()) : null;
                DocumentParserService.ParsedDocument doc = new DocumentParserService.ParsedDocument(
                        snap.getFileName(),
                        filePath,
                        snap.isImage(),
                        snap.getContent(),
                        snap.getMimeType(),
                        snap.getSizeBytes(),
                        mdPath,
                        null
                );
                attachedDocuments.add(doc);
                addFileChip(doc);
            }
        } else if (msg.getAttachedFilePaths() != null && !msg.getAttachedFilePaths().isEmpty()) {
            for (String pathStr : msg.getAttachedFilePaths()) {
                try {
                    Path p = Path.of(pathStr);
                    if (Files.exists(p)) {
                        DocumentParserService.ParsedDocument doc = documentParser.parse(p);
                        attachedDocuments.add(doc);
                        addFileChip(doc);
                    }
                } catch (Exception ignored) {}
            }
        }

        updateAttachmentsVisibility();
        updateTokenUsageDisplay();

        if (AiTestGeneratorService.containsTargetUrl(
                promptInputArea != null ? promptInputArea.getText() : "", attachedDocuments, liveDomJson, null)) {
            if (autoGenerateScriptCheck != null) {
                autoGenerateScriptCheck.setSelected(true);
            }
        }
    }

    public static String extractPromptFromUserContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = content;
        // Strip leading [Skill Name]\n
        if (text.startsWith("[")) {
            int closeBracket = text.indexOf("]\n");
            if (closeBracket > 0) {
                text = text.substring(closeBracket + 2);
            }
        }
        // Strip trailing \n\nĐã đính kèm: ...
        int attachIdx = text.lastIndexOf("\n\nĐã đính kèm: ");
        if (attachIdx >= 0) {
            text = text.substring(0, attachIdx);
        }
        return text.trim();
    }
}
