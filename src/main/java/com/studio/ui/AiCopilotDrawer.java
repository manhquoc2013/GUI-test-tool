package com.studio.ui;

import com.studio.model.AiConfig;
import com.studio.model.TestStep;
import com.studio.model.TestCase;
import com.studio.service.AiCopilotService;
import com.studio.service.AiLoggingService;
import com.studio.service.LiveDomScanner;
import com.studio.service.OpenAiClient;
import com.studio.service.ToastService;
import com.studio.util.UiIcons;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Contextual AI Copilot drawer docked inside the workspace.
 *
 * <p>Offers the two repairs testers need most while authoring a step: fixing a broken selector
 * from the live DOM and generating a Playwright assertion for the selected step. Both actions call
 * the configured model on a daemon thread and never block the JavaFX application thread.</p>
 */
public final class AiCopilotDrawer extends VBox {

    private static final int ICON_SIZE = 13;
    private static final String CSS_BUSY = "ai-copilot-busy";
    private static final String CSS_SUCCESS = "ai-copilot-success";
    private static final String CSS_ERROR = "ai-copilot-error";

    /** Everything the drawer needs from the workspace, supplied by the controller. */
    public interface CopilotContext {
        TestCase testCase();

        TestStep step();

        String startUrl();

        String currentScript();

        AiConfig aiConfig();

        void applySelector(String selector);

        void applyAssertion(TestStep assertionStep);

        void requestConfiguration();

        /** Lets the workspace dock the drawer (reserve layout space) instead of stacking it. */
        void onVisibilityChanged(boolean visible);
    }

    private enum PendingApply { NONE, SELECTOR, ASSERTION }

    private final CopilotContext context;
    private final Label contextLabel = new Label("Chọn một bước để AI hỗ trợ.");
    private final TextArea instructionArea = new TextArea();
    private final TextArea outputArea = new TextArea();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private final Button fixSelectorBtn = new Button("Sửa selector");
    private final Button generateAssertBtn = new Button("Sinh assertion");
    private final Button applyBtn = new Button("Áp dụng");
    private final Button copyBtn = new Button("Sao chép");

    /** Monotonic token: a reply is only accepted while it still matches the latest request. */
    private final java.util.concurrent.atomic.AtomicLong requestGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    private PendingApply pendingApply = PendingApply.NONE;
    private String pendingSelector;
    private TestStep pendingAssertion;
    /** Step the pending suggestion was computed for; applying it elsewhere would corrupt a step. */
    private TestStep pendingStep;

    public AiCopilotDrawer(CopilotContext context) {
        this.context = context;

        getStyleClass().add("ai-copilot-drawer");
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Double.MAX_VALUE);
        setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(buildHeader(), buildContextBox(), buildInstructionBox(),
                buildActionRow(), buildStatusRow(), buildOutputBox(), buildApplyRow());
    }

    private HBox buildHeader() {
        Label title = new Label("AI Copilot");
        title.getStyleClass().add("ai-copilot-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button();
        closeBtn.getStyleClass().addAll("icon-btn", "flat");
        closeBtn.setAccessibleText("Đóng AI Copilot");
        UiIcons.setGraphic(closeBtn, UiIcons.CLOSE, 12);
        closeBtn.setTooltip(new Tooltip("Đóng AI Copilot"));
        closeBtn.setOnAction(e -> hideDrawer());

        HBox header = new HBox(8, title, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("ai-copilot-header");
        return header;
    }

    private VBox buildContextBox() {
        contextLabel.getStyleClass().add("ai-copilot-context");
        contextLabel.setWrapText(true);

        Label hint = new Label("Sửa selector hỏng hoặc sinh assertion cho bước đang chọn.");
        hint.getStyleClass().add("ai-copilot-hint");
        hint.setWrapText(true);

        VBox box = new VBox(4, contextLabel, hint);
        box.getStyleClass().add("ai-copilot-section");
        return box;
    }

    private VBox buildInstructionBox() {
        Label label = new Label("Ghi chú thêm (tùy chọn)");
        label.getStyleClass().add("field-label-sm");

        instructionArea.setPromptText("Ví dụ: nút đăng nhập nằm trong dialog, ưu tiên role/name...");
        instructionArea.setWrapText(true);
        instructionArea.setPrefRowCount(2);
        instructionArea.getStyleClass().add("ai-copilot-input");

        VBox box = new VBox(4, label, instructionArea);
        box.getStyleClass().add("ai-copilot-section");
        return box;
    }

    private HBox buildActionRow() {
        fixSelectorBtn.getStyleClass().addAll("accent", "small");
        UiIcons.setGraphic(fixSelectorBtn, UiIcons.GLOBE, ICON_SIZE);
        fixSelectorBtn.setTooltip(new Tooltip("Quét DOM trang và đề xuất selector đúng cho bước"));
        fixSelectorBtn.setOnAction(e -> runTask(true));

        generateAssertBtn.getStyleClass().addAll("small");
        UiIcons.setGraphic(generateAssertBtn, UiIcons.CHECK, ICON_SIZE);
        generateAssertBtn.setTooltip(new Tooltip("Sinh một assertion Playwright cho bước đang chọn"));
        generateAssertBtn.setOnAction(e -> runTask(false));

        HBox row = new HBox(8, fixSelectorBtn, generateAssertBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ai-copilot-section");
        return row;
    }

    private HBox buildStatusRow() {
        progress.setPrefSize(16, 16);
        progress.setVisible(false);
        progress.setManaged(false);

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        HBox row = new HBox(8, progress, statusLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ai-copilot-status-row");
        return row;
    }

    private VBox buildOutputBox() {
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setPrefRowCount(5);
        outputArea.setPromptText("Kết quả từ AI sẽ hiển thị ở đây...");
        outputArea.getStyleClass().addAll("ai-copilot-output", "code-editor-inner");
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        VBox box = new VBox(4, outputArea);
        box.getStyleClass().add("ai-copilot-section");
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private HBox buildApplyRow() {
        applyBtn.getStyleClass().addAll("success", "small");
        UiIcons.setGraphic(applyBtn, UiIcons.CHECK, ICON_SIZE);
        applyBtn.setDisable(true);
        applyBtn.setOnAction(e -> applyPending());

        copyBtn.getStyleClass().addAll("small", "flat");
        UiIcons.setGraphic(copyBtn, UiIcons.COPY, ICON_SIZE);
        copyBtn.setOnAction(e -> copyOutput());

        HBox row = new HBox(8, applyBtn, copyBtn);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.getStyleClass().add("ai-copilot-section");
        return row;
    }

    /** Shows the drawer and refreshes the context label from the workspace selection. */
    public void showDrawer() {
        refreshContext();
        setVisible(true);
        setManaged(true);
        context.onVisibilityChanged(true);
    }

    public void hideDrawer() {
        setVisible(false);
        setManaged(false);
        // Drop any in-flight reply: applying it later would land on a different step.
        requestGeneration.incrementAndGet();
        clearState();
        context.onVisibilityChanged(false);
    }

    public void toggleDrawer() {
        if (isVisible()) {
            hideDrawer();
        } else {
            showDrawer();
        }
    }

    /** Re-reads the selected case/step so the drawer never acts on a stale selection. */
    public void refreshContext() {
        TestCase testCase = context.testCase();
        TestStep step = context.step();
        StringBuilder text = new StringBuilder();
        text.append("Case: ").append(testCase != null ? testCase.getName() : "(chưa chọn)");
        if (step != null) {
            text.append(" • Bước ").append(step.getStepNumber())
                    .append(" [").append(step.getAction()).append("]");
            if (step.getTarget() != null && !step.getTarget().isBlank()) {
                text.append(" → ").append(step.getTarget());
            }
        } else {
            text.append(" • Chưa chọn bước");
        }
        contextLabel.setText(text.toString());
        updateActionAvailability();
    }

    private void updateActionAvailability() {
        boolean hasStep = context.step() != null;
        fixSelectorBtn.setDisable(!hasStep);
        generateAssertBtn.setDisable(!hasStep);
    }

    private void runTask(boolean selectorTask) {
        TestCase testCase = context.testCase();
        TestStep step = context.step();
        if (testCase == null || step == null) {
            ToastService.warn("Hãy chọn một test case và một bước trước khi dùng AI Copilot.");
            return;
        }
        AiConfig config = context.aiConfig();
        if (config == null || !config.hasApiKey()) {
            context.requestConfiguration();
            return;
        }

        String instruction = instructionArea.getText();
        String startUrl = context.startUrl();
        String script = context.currentScript();
        long generation = requestGeneration.incrementAndGet();
        startBusy(selectorTask ? "Đang quét DOM và sửa selector..." : "Đang sinh assertion...");

        Thread worker = new Thread(() -> {
            try {
                String dom = scanDom(startUrl);
                String prompt = selectorTask
                        ? AiCopilotService.buildSelectorFixPrompt(testCase, step, dom, instruction)
                        : AiCopilotService.buildAssertionPrompt(testCase, step, script, dom, instruction);
                OpenAiClient.Completion completion = new OpenAiClient().completeChat(
                        config, AiCopilotService.asMessages(prompt), AiCopilotService.MAX_OUTPUT_TOKENS);
                String output = AiCopilotService.cleanModelOutput(
                        completion != null ? completion.content() : "");
                Platform.runLater(() -> {
                    if (generation != requestGeneration.get()) {
                        // The drawer was closed or another request superseded this one.
                        return;
                    }
                    pendingStep = step;
                    handleSuccess(selectorTask, output);
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> finishWithError("Đã hủy yêu cầu AI."));
            } catch (Exception failure) {
                AiLoggingService.error("AI-COPILOT", "AI Copilot thất bại", failure);
                String message = failure.getMessage() != null ? failure.getMessage() : "Lỗi không xác định";
                Platform.runLater(() -> {
                    if (generation == requestGeneration.get()) {
                        finishWithError(message);
                    }
                });
            }
        }, "ai-copilot-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** DOM is best-effort: a blank or unreachable page must not abort the AI request. */
    private String scanDom(String startUrl) {
        if (startUrl == null || startUrl.isBlank()) {
            return "";
        }
        String dom = new LiveDomScanner().scanUrl(startUrl);
        return AiCopilotService.trimDom(dom);
    }

    private void startBusy(String message) {
        clearState();
        progress.setVisible(true);
        progress.setManaged(true);
        statusLabel.getStyleClass().removeAll(CSS_SUCCESS, CSS_ERROR, CSS_BUSY);
        statusLabel.getStyleClass().add(CSS_BUSY);
        statusLabel.setText(message);
        setBusy(true);
    }

    private void handleSuccess(boolean selectorTask, String output) {
        setBusy(false);
        if (output == null || output.isBlank()) {
            finishWithError("AI không trả về nội dung.");
            return;
        }
        if (selectorTask) {
            String selector = AiCopilotService.extractSelector(output);
            if (selector == null) {
                finishWithError("AI không trả về selector hợp lệ.");
                outputArea.setText(output);
                return;
            }
            outputArea.setText(selector);
            pendingSelector = selector;
            pendingApply = PendingApply.SELECTOR;
            applyBtn.setText("Áp dụng selector");
            finishWithSuccess("Đã có selector đề xuất.");
        } else {
            TestStep assertionStep = parseAssertionStep(output);
            if (assertionStep == null) {
                finishWithError("AI không trả về assertion Playwright hợp lệ.");
                outputArea.setText(output);
                return;
            }
            outputArea.setText(output);
            pendingAssertion = assertionStep;
            pendingApply = PendingApply.ASSERTION;
            applyBtn.setText("Thêm assertion");
            finishWithSuccess("Đã có assertion đề xuất.");
        }
        applyBtn.setDisable(false);
    }

    /** Accepts only ASSERT_VISIBLE / ASSERT_TEXT lines; anything else is not an assertion. */
    private TestStep parseAssertionStep(String output) {
        List<TestStep> parsed = StepScriptBidirectionalSync.scriptToSteps(output);
        for (TestStep step : parsed) {
            String action = step.getAction() != null ? step.getAction() : "";
            if (action.startsWith("ASSERT_")) {
                return step;
            }
        }
        return null;
    }

    private void finishWithError(String message) {
        setBusy(false);
        progress.setVisible(false);
        progress.setManaged(false);
        statusLabel.getStyleClass().removeAll(CSS_SUCCESS, CSS_BUSY);
        if (!statusLabel.getStyleClass().contains(CSS_ERROR)) {
            statusLabel.getStyleClass().add(CSS_ERROR);
        }
        statusLabel.setText("Lỗi: " + message);
        applyBtn.setDisable(true);
        pendingApply = PendingApply.NONE;
    }

    private void finishWithSuccess(String message) {
        progress.setVisible(false);
        progress.setManaged(false);
        statusLabel.getStyleClass().removeAll(CSS_ERROR, CSS_BUSY);
        if (!statusLabel.getStyleClass().contains(CSS_SUCCESS)) {
            statusLabel.getStyleClass().add(CSS_SUCCESS);
        }
        statusLabel.setText(message);
    }

    private void setBusy(boolean busy) {
        fixSelectorBtn.setDisable(busy);
        generateAssertBtn.setDisable(busy);
        applyBtn.setDisable(busy || pendingApply == PendingApply.NONE);
        copyBtn.setDisable(busy);
        if (!busy) {
            updateActionAvailability();
        }
    }

    private void clearState() {
        pendingApply = PendingApply.NONE;
        pendingSelector = null;
        pendingAssertion = null;
        pendingStep = null;
        applyBtn.setDisable(true);
        applyBtn.setText("Áp dụng");
        outputArea.clear();
    }

    private void applyPending() {
        if (pendingApply == PendingApply.NONE) {
            return;
        }
        if (pendingStep == null || context.step() != pendingStep) {
            // The selection moved while the model was answering: never write the result onto
            // a step it was not computed for.
            clearState();
            finishWithError("Bước đang chọn đã thay đổi — hãy chạy lại AI Copilot.");
            return;
        }
        switch (pendingApply) {
            case SELECTOR -> {
                if (pendingSelector != null) {
                    context.applySelector(pendingSelector);
                    finishWithSuccess("Đã áp dụng selector vào bước.");
                }
            }
            case ASSERTION -> {
                if (pendingAssertion != null) {
                    context.applyAssertion(pendingAssertion);
                    finishWithSuccess("Đã thêm assertion vào kịch bản.");
                }
            }
            case NONE -> {
                // Handled by the early return above.
            }
        }
        pendingApply = PendingApply.NONE;
        pendingStep = null;
        applyBtn.setDisable(true);
    }

    private void copyOutput() {
        String text = outputArea.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        ToastService.success("Đã sao chép kết quả AI.");
    }
}
