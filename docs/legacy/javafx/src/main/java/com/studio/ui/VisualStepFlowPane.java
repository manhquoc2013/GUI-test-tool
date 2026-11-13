package com.studio.ui;

import com.studio.model.StepResult;
import com.studio.model.TestStep;
import com.studio.service.ToastService;
import com.studio.util.UiIcons;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Visual canvas holding ordered {@link StepCardView}s.
 * Enables No-code / Low-code authoring, drag re-ordering, and quick step creation.
 *
 * <p>Disabled steps stay in the list (and in the model) so toggling one off is reversible. They are
 * still handed to the script renderer, which emits a placeholder line for them, so the card numbers
 * the runner reports stay aligned with the cards on screen.</p>
 *
 * <p>Reordering is the most destructive thing a builder does without a confirmation, so every
 * delete and every move lands on an undo stack ({@link #undo()} / {@link #redo()}, also bound to
 * the platform shortcut and to the footer buttons).</p>
 */
public final class VisualStepFlowPane extends VBox {

    private static final int QUICK_ADD_ICON_SIZE = 12;
    private static final int EMPTY_ICON_SIZE = 28;
    private static final int FOOTER_ICON_SIZE = 13;
    private static final double EMPTY_HINT_MAX_WIDTH = 340;
    private static final String CSS_DRAG_SOURCE = "step-card-dragging";
    private static final String CSS_DROP_BEFORE = "step-card-drop-before";
    private static final String CSS_DROP_AFTER = "step-card-drop-after";
    private static final String CSS_EMPTY = "step-empty-state";
    /** {@code .step-add-bar} padding, left + right (see {@code studio.css}). */
    private static final double BAR_HORIZONTAL_INSETS = 24;
    /** Gap between the caption and the quick-add row. */
    private static final double BAR_GAP = 8;
    /**
     * Share of the quick-add row the caption must leave intact before it is dropped. Two buttons per
     * line (≈40%) is still a usable toolbar, so the caption only steps aside when the row would be
     * reduced to a single narrow column.
     */
    private static final double QUICK_ADD_ROW_SHARE = 0.4;
    /** Distance from the viewport edge where a drag starts scrolling the step list. */
    private static final double AUTO_SCROLL_EDGE = 40;
    /** Pixels per frame scrolled at the very edge of the viewport. */
    private static final double AUTO_SCROLL_MAX_STEP = 14;
    /** Number of delete/move edits kept for undo; older ones fall off the end of the stack. */
    private static final int MAX_HISTORY = 50;
    /** Drop position meaning "no change" — either nothing was dragged or the slot is the current one. */
    static final int NO_DROP = -1;

    private final VBox cardsBox;
    private final VBox emptyState;
    private final ScrollPane scrollPane;
    private final List<StepCardView> cardViews = new ArrayList<>();
    private final Deque<FlowEdit> undoStack = new ArrayDeque<>();
    private final Deque<FlowEdit> redoStack = new ArrayDeque<>();
    private Button undoBtn;
    private Button redoBtn;
    private Runnable onStepsChanged;
    private Consumer<TestStep> onStepSelected;
    private StepCardView selectedCard;
    private Label runSummary;

    private StepCardView draggingCard;
    private int pendingDropIndex = NO_DROP;
    private AnimationTimer autoScrollTicker;
    private double autoScrollVelocity;

    public VisualStepFlowPane() {
        setSpacing(0);
        getStyleClass().add("visual-step-flow-pane");

        // ── Top Action Toolbar ──
        // The caption keeps its full text and the quick-add buttons wrap into a FlowPane instead of
        // being squeezed: an HBox of buttons shrinks each one to its ellipsis width, so a narrow
        // editor used to show "+ Mở …" / "+ …" instead of the action names.
        HBox addBar = new HBox(8);
        addBar.setAlignment(Pos.CENTER_LEFT);
        addBar.getStyleClass().add("step-add-bar");

        Label label = new Label("Kịch bản từng bước (No-Code):");
        label.getStyleClass().add("step-add-bar-title");
        label.setMinWidth(Region.USE_PREF_SIZE);

        FlowPane quickAdds = new FlowPane(6, 6);
        quickAdds.setAlignment(Pos.CENTER_RIGHT);
        quickAdds.getStyleClass().add("step-quick-adds");
        // A FlowPane reflows at any width, so the bar may take all the room the caption leaves.
        quickAdds.setMinWidth(0);
        HBox.setHgrow(quickAdds, Priority.ALWAYS);

        Button addNavBtn = createQuickAddBtn("Mở trang", "NAVIGATE", "https://example.com", UiIcons.GLOBE);
        Button addClickBtn = createQuickAddBtn("Nhấp chuột", "CLICK", "button.submit", UiIcons.CURSOR);
        Button addFillBtn = createQuickAddBtn("Nhập liệu", "FILL", "input#user", UiIcons.EDIT);
        Button addAssertBtn = createQuickAddBtn("Kiểm tra hiển thị", "ASSERT_VISIBLE", ".success-msg",
                UiIcons.EYE);
        quickAdds.getChildren().addAll(addNavBtn, addClickBtn, addFillBtn, addAssertBtn);

        addBar.getChildren().addAll(label, quickAdds);
        // When the pane is too narrow to give the buttons a usable row, the caption (which only
        // restates the "No-Code" switch above) steps aside instead of stacking the buttons one
        // per line. Both sides of the comparison are pref widths plus the bar width, so the
        // binding cannot feed back into the layout it measures.
        DoubleBinding captionRoom = Bindings.createDoubleBinding(
                () -> BAR_HORIZONTAL_INSETS + label.prefWidth(-1) + BAR_GAP
                        + quickAddRowWidth(quickAdds) * QUICK_ADD_ROW_SHARE,
                addBar.widthProperty());
        label.visibleProperty().bind(addBar.widthProperty().greaterThanOrEqualTo(captionRoom));
        label.managedProperty().bind(label.visibleProperty());

        // ── Scrollable Cards Container ──
        cardsBox = new VBox(8);
        cardsBox.getStyleClass().add("step-cards-container");

        emptyState = buildEmptyState();
        cardsBox.getChildren().add(emptyState);

        scrollPane = new ScrollPane(cardsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().addAll("edge-to-edge", "subtle-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // ── Footer: history + primary add action ──
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER);
        footer.getStyleClass().add("step-footer-bar");

        undoBtn = historyButton(UiIcons.UNDO, "Hoàn tác (Ctrl/Cmd+Z)", "step-undo-btn", this::undo);
        redoBtn = historyButton(UiIcons.REDO, "Làm lại (Ctrl/Cmd+Shift+Z)", "step-redo-btn", this::redo);

        Button addStepBtn = new Button("Thêm bước mới");
        addStepBtn.getStyleClass().addAll("accent", "small");
        UiIcons.setGraphic(addStepBtn, UiIcons.PLUS, QUICK_ADD_ICON_SIZE);
        addStepBtn.setOnAction(e -> addNewStep("CLICK", "", ""));

        runSummary = new Label();
        runSummary.getStyleClass().add("step-run-summary");
        runSummary.setMinWidth(0);
        runSummary.setMaxWidth(Double.MAX_VALUE);
        runSummary.setTextOverrun(OverrunStyle.ELLIPSIS);
        runSummary.setVisible(false);
        runSummary.setManaged(false);
        HBox.setHgrow(runSummary, Priority.ALWAYS);
        runSummary.setAlignment(Pos.CENTER_RIGHT);

        Button clearRunBtn = new Button();
        clearRunBtn.getStyleClass().addAll("flat", "step-tool-btn", "step-history-btn");
        clearRunBtn.setTooltip(new Tooltip("Xoá kết quả lần chạy gần nhất"));
        clearRunBtn.setAccessibleText("Xoá kết quả lần chạy gần nhất");
        UiIcons.setGraphic(clearRunBtn, UiIcons.CLOSE, FOOTER_ICON_SIZE);
        clearRunBtn.setOnAction(e -> clearStepResults());
        clearRunBtn.visibleProperty().bind(runSummary.visibleProperty());
        clearRunBtn.managedProperty().bind(runSummary.visibleProperty());

        footer.getChildren().addAll(undoBtn, redoBtn, addStepBtn, runSummary, clearRunBtn);

        getChildren().addAll(addBar, scrollPane, footer);

        installDropHandlers();
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleHistoryShortcut);
        refreshHistoryButtons();
    }

    /**
     * An empty case is the first thing a new user sees, so it explains what a step is and offers the
     * primary action instead of a single muted sentence.
     */
    private VBox buildEmptyState() {
        Label icon = new Label();
        icon.getStyleClass().add("step-empty-icon");
        UiIcons.setGraphic(icon, UiIcons.CURSOR, EMPTY_ICON_SIZE);

        Label title = new Label("Chưa có bước nào");
        title.getStyleClass().add("step-empty-title");

        Label hint = new Label("Mỗi bước là một thao tác trên trang: đi tới URL, nhấp, nhập liệu hoặc kiểm tra. "
                + "Bắt đầu bằng nút bên dưới, hoặc dùng các nút gợi ý ở thanh trên.");
        hint.getStyleClass().add("step-empty-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(EMPTY_HINT_MAX_WIDTH);

        Button start = new Button("Thêm bước đầu tiên");
        start.getStyleClass().addAll("accent", "small");
        UiIcons.setGraphic(start, UiIcons.PLUS, QUICK_ADD_ICON_SIZE);
        start.setOnAction(e -> addNewStep("NAVIGATE", "", ""));

        VBox box = new VBox(8, icon, title, hint, start);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add(CSS_EMPTY);
        return box;
    }

    private Button createQuickAddBtn(String text, String action, String targetPlaceholder, String iconPath) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("small", "flat", "step-quick-add");
        // Never let the label truncate: the buttons wrap in their FlowPane instead of shrinking.
        btn.setMinWidth(Region.USE_PREF_SIZE);
        UiIcons.setGraphic(btn, iconPath, QUICK_ADD_ICON_SIZE);
        btn.setOnAction(e -> addNewStep(action, targetPlaceholder, ""));
        return btn;
    }

    private Button historyButton(String iconPath, String tooltip, String styleClass, Runnable action) {
        Button button = new Button();
        button.getStyleClass().addAll("flat", "step-tool-btn", "step-history-btn", styleClass);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        UiIcons.setGraphic(button, iconPath, FOOTER_ICON_SIZE);
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Width the quick-add buttons need on a single line.
     *
     * <p>{@code FlowPane.prefWidth(-1)} cannot be used here: it is capped by the pane's
     * {@code prefWrapLength} (400px by default), which would make a 400px bar look narrower than
     * the caption it can actually hold.</p>
     */
    private static double quickAddRowWidth(FlowPane row) {
        double width = 0;
        int visible = 0;
        for (Node child : row.getChildren()) {
            if (!child.isManaged()) {
                continue;
            }
            width += ((Region) child).prefWidth(-1);
            if (visible++ > 0) {
                width += row.getHgap();
            }
        }
        return width;
    }

    public void setOnStepsChanged(Runnable callback) {
        this.onStepsChanged = callback;
    }

    /** Replaces the whole flow with {@code steps}; each model object keeps its own enabled flag. */
    public void loadSteps(List<TestStep> steps) {
        cardsBox.getChildren().clear();
        cardViews.clear();
        selectedCard = null;
        // A different case is a different run: keeping the previous verdicts would paint results
        // onto steps that were never executed.
        clearStepResults();
        // A loaded case is a new editing session: undoing across documents would restore a step
        // into the case the user just closed.
        clearHistory();

        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                TestStep step = steps.get(i);
                step.setStepNumber(i + 1);
                StepCardView card = buildCard(step);
                cardViews.add(card);
                cardsBox.getChildren().add(card);
            }
        }
        updateEmptyState();
    }

    public void addNewStep(String action, String target, String testData) {
        TestStep step = new TestStep(cardViews.size() + 1, action, target, testData, "", "");
        StepCardView card = buildCard(step);
        cardViews.add(card);
        cardsBox.getChildren().add(card);
        reindexSteps();
        updateEmptyState();
        selectCard(card);
        // A freshly created step is almost always the next thing the user wants to fill in.
        card.focusPrimaryField();
        notifyChange();
    }

    /**
     * Inserts a new step directly after {@code anchor} and opens its editor.
     *
     * <p>Repairing a recorded flow used to mean appending a step at the end and dragging it up
     * through the list; authoring tools need an insert-at-position primitive.</p>
     */
    private void insertStepAfter(TestStep anchor) {
        int index = indexOfStep(anchor);
        if (index < 0) {
            return;
        }
        TestStep step = new TestStep(index + 2, "CLICK", "", "", "", "");
        StepCardView card = buildCard(step);
        cardViews.add(index + 1, card);
        renderOrder();
        reindexSteps();
        updateEmptyState();
        selectCard(card);
        card.focusPrimaryField();
        notifyChange();
    }

    /** Appends an externally produced step (e.g. an assertion generated by AI Copilot). */
    public void appendStep(TestStep step) {
        if (step == null) {
            return;
        }
        step.setStepNumber(cardViews.size() + 1);
        StepCardView card = buildCard(step);
        cardViews.add(card);
        cardsBox.getChildren().add(card);
        reindexSteps();
        updateEmptyState();
        selectCard(card);
        notifyChange();
    }

    /** Builds the view for {@code step}; registering it in {@link #cardViews} is the caller's job. */
    private StepCardView buildCard(TestStep step) {
        StepCardView card = new StepCardView(step, this::requestRemove, this::notifyChange,
                this::copyStepCommand, () -> insertStepAfter(step));
        installDragSource(card);
        installKeyboardReorder(card);
        card.setOnMousePressed(event -> selectCard(card));
        return card;
    }

    /**
     * Drag-and-drop is mouse-only; Alt+Up/Down reorders the focused card so the step order is
     * reachable from the keyboard as well.
     */
    private void installKeyboardReorder(StepCardView card) {
        card.setFocusTraversable(true);
        card.setOnKeyPressed(event -> {
            if (!event.isAltDown()) {
                return;
            }
            int index = cardViews.indexOf(card);
            if (index < 0) {
                return;
            }
            if (event.getCode() == KeyCode.UP && index > 0) {
                moveCard(index, index - 1);
                selectCard(card);
                card.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN && index < cardViews.size() - 1) {
                moveCard(index, index + 1);
                selectCard(card);
                card.requestFocus();
                event.consume();
            }
        });
    }

    /** Highlights {@code card} and notifies listeners so the AI Copilot follows the selection. */
    private void selectCard(StepCardView card) {
        if (selectedCard != null && selectedCard != card) {
            selectedCard.setSelected(false);
        }
        selectedCard = card;
        card.setSelected(true);
        if (onStepSelected != null) {
            onStepSelected.accept(card.getStep());
        }
    }

    public void setOnStepSelected(Consumer<TestStep> listener) {
        this.onStepSelected = listener;
    }

    /** @return the step whose card is currently highlighted, or {@code null}. */
    public TestStep getSelectedStep() {
        return selectedCard != null ? selectedCard.getStep() : null;
    }

    /** Applies an AI-suggested selector to the highlighted step and refreshes its editor fields. */
    public void updateSelectedStepTarget(String selector) {
        if (selectedCard == null || selector == null) {
            return;
        }
        selectedCard.getStep().setTarget(selector);
        selectedCard.refreshFromModel();
        notifyChange();
    }

    // ── Drag & drop ─────────────────────────────────────────────────────────

    /** Starts a reorder gesture from a card's drag handle; the drag itself is handled by the pane. */
    private void installDragSource(StepCardView card) {
        // The handle is a child of whichever row is showing (a note has its own), so the gesture is
        // detected on the card and filtered down to the handle instead of binding one node.
        card.addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
            if (!isDragHandle(event.getTarget())) {
                return;
            }
            if (cardViews.size() < 2) {
                // A single step has nowhere to go, so do not start a gesture that cannot complete.
                event.consume();
                return;
            }
            draggingCard = card;
            Dragboard dragboard = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(Integer.toString(cardViews.indexOf(card)));
            dragboard.setContent(content);
            card.getStyleClass().add(CSS_DRAG_SOURCE);
            selectCard(card);
            event.consume();
        });

        card.setOnDragDone(event -> {
            finishDrag();
            event.consume();
        });
    }

    private static boolean isDragHandle(Object eventTarget) {
        Node current = eventTarget instanceof Node node ? node : null;
        while (current != null) {
            if (current.getStyleClass().contains("step-drag-handle")) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Drop handling lives on the pane, not on each card: a {@link javafx.scene.control.TextField}
     * inside an open card accepts text drops and would swallow the reorder event before it could
     * reach the card.
     */
    private void installDropHandlers() {
        addEventFilter(DragEvent.DRAG_OVER, event -> {
            if (draggingCard == null) {
                return;
            }
            StepCardView target = cardUnder(event.getTarget());
            if (target == null) {
                clearDropMarks();
                pendingDropIndex = NO_DROP;
                stopAutoScroll();
            } else {
                event.acceptTransferModes(TransferMode.MOVE);
                highlightDropPosition(target, event.getSceneY());
                trackAutoScroll(event.getSceneY());
            }
            event.consume();
        });

        addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            if (draggingCard == null) {
                return;
            }
            int from = cardViews.indexOf(draggingCard);
            int to = pendingDropIndex;
            boolean completed = from >= 0 && to != NO_DROP && to != from;
            finishDrag();
            event.setDropCompleted(completed);
            event.consume();
            if (completed) {
                moveCard(from, to);
            }
        });
    }

    /**
     * Computes the slot a drop would use, from the pointer's half of the hovered card.
     *
     * @param from        index of the dragged card
     * @param targetIndex index of the hovered card
     * @param after       true when the pointer is in the lower half of the hovered card
     * @return the destination index in the list *after* the dragged card is removed, or
     *         {@link #NO_DROP} when that would leave the order unchanged
     */
    static int dropIndex(int from, int targetIndex, boolean after) {
        if (from < 0 || targetIndex < 0) {
            return NO_DROP;
        }
        int destination = after ? targetIndex + 1 : targetIndex;
        if (destination > from) {
            // Removing the dragged card first shifts every later slot one place to the left.
            destination--;
        }
        return destination == from ? NO_DROP : destination;
    }

    private void highlightDropPosition(StepCardView target, double sceneY) {
        clearDropMarks();
        int from = cardViews.indexOf(draggingCard);
        int targetIndex = cardViews.indexOf(target);
        Bounds bounds = target.localToScene(target.getBoundsInLocal());
        boolean after = bounds != null && sceneY >= bounds.getMinY() + bounds.getHeight() / 2;
        int destination = dropIndex(from, targetIndex, after);
        pendingDropIndex = destination;
        if (destination != NO_DROP && target != draggingCard) {
            target.getStyleClass().add(after ? CSS_DROP_AFTER : CSS_DROP_BEFORE);
        }
    }

    private StepCardView cardUnder(Object eventTarget) {
        Node current = eventTarget instanceof Node node ? node : null;
        while (current != null) {
            if (current instanceof StepCardView card) {
                return card;
            }
            current = current.getParent();
        }
        return null;
    }

    private void finishDrag() {
        if (draggingCard != null) {
            draggingCard.getStyleClass().remove(CSS_DRAG_SOURCE);
            draggingCard = null;
        }
        clearDropMarks();
        pendingDropIndex = NO_DROP;
        stopAutoScroll();
    }

    private void clearDropMarks() {
        for (StepCardView view : cardViews) {
            view.getStyleClass().removeAll(CSS_DROP_BEFORE, CSS_DROP_AFTER);
        }
    }

    // ── Auto-scroll while dragging ─────────────────────────────────────────

    /** Scrolls when the pointer approaches a viewport edge, so a long case can be reordered. */
    private void trackAutoScroll(double sceneY) {
        Bounds viewport = scrollPane.localToScene(scrollPane.getBoundsInLocal());
        if (viewport == null || viewport.getHeight() <= 0) {
            stopAutoScroll();
            return;
        }
        double edge = Math.min(AUTO_SCROLL_EDGE, viewport.getHeight() / 3);
        double velocity = 0;
        if (sceneY < viewport.getMinY() + edge) {
            velocity = -(1 - Math.max(0, sceneY - viewport.getMinY()) / edge) * AUTO_SCROLL_MAX_STEP;
        } else if (sceneY > viewport.getMaxY() - edge) {
            velocity = (1 - Math.max(0, viewport.getMaxY() - sceneY) / edge) * AUTO_SCROLL_MAX_STEP;
        }
        autoScrollVelocity = velocity;
        if (velocity == 0) {
            stopAutoScroll();
        } else {
            startAutoScroll();
        }
    }

    private void startAutoScroll() {
        if (autoScrollTicker != null) {
            return;
        }
        autoScrollTicker = new AnimationTimer() {
            @Override
            public void handle(long now) {
                applyAutoScrollStep();
            }
        };
        autoScrollTicker.start();
    }

    private void applyAutoScrollStep() {
        if (autoScrollVelocity == 0) {
            stopAutoScroll();
            return;
        }
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double scrollable = cardsBox.getHeight() - viewportHeight;
        if (scrollable <= 0) {
            stopAutoScroll();
            return;
        }
        double next = scrollPane.getVvalue() + autoScrollVelocity / scrollable;
        double clamped = Math.max(scrollPane.getVmin(), Math.min(scrollPane.getVmax(), next));
        if (clamped == scrollPane.getVvalue()) {
            // Reached the first/last step: stop the ticker instead of spinning every frame.
            stopAutoScroll();
            return;
        }
        scrollPane.setVvalue(clamped);
    }

    private void stopAutoScroll() {
        autoScrollVelocity = 0;
        if (autoScrollTicker != null) {
            autoScrollTicker.stop();
            autoScrollTicker = null;
        }
    }

    // ── Reordering & history ────────────────────────────────────────────────

    /** Moves the card at {@code from} to {@code to} (both indexes over the full list) and records it. */
    public void moveCard(int from, int to) {
        if (from < 0 || to < 0 || from >= cardViews.size() || to >= cardViews.size() || from == to) {
            return;
        }
        TestStep moved = cardViews.get(from).getStep();
        applyMoveTo(moved, to);
        push(new MoveEdit(moved, from, to));
        notifyChange();
    }

    /** Moves the card holding {@code step} to {@code targetIndex} without touching the history. */
    private void applyMoveTo(TestStep step, int targetIndex) {
        int current = indexOfStep(step);
        if (current < 0) {
            return;
        }
        int destination = Math.max(0, Math.min(targetIndex, cardViews.size() - 1));
        if (current == destination) {
            return;
        }
        StepCardView card = cardViews.remove(current);
        cardViews.add(destination, card);
        renderOrder();
        reindexSteps();
        updateEmptyState();
        selectCard(card);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Reverses the last delete or reorder. @return true when something was undone. */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        FlowEdit edit = undoStack.pop();
        edit.undo();
        redoStack.push(edit);
        refreshHistoryButtons();
        notifyChange();
        ToastService.info("Đã hoàn tác.");
        return true;
    }

    /** Re-applies the last undone edit. @return true when something was redone. */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        FlowEdit edit = redoStack.pop();
        edit.redo();
        undoStack.push(edit);
        refreshHistoryButtons();
        notifyChange();
        ToastService.info("Đã làm lại.");
        return true;
    }

    private void push(FlowEdit edit) {
        undoStack.push(edit);
        // A new edit invalidates the redo branch, exactly like a text editor.
        redoStack.clear();
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        refreshHistoryButtons();
    }

    private void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        refreshHistoryButtons();
    }

    private void refreshHistoryButtons() {
        if (undoBtn != null) {
            undoBtn.setDisable(undoStack.isEmpty());
        }
        if (redoBtn != null) {
            redoBtn.setDisable(redoStack.isEmpty());
        }
    }

    /** The platform undo/redo shortcut, ignored while a text field owns the focus. */
    private void handleHistoryShortcut(KeyEvent event) {
        if (!event.isShortcutDown()) {
            return;
        }
        Node focusOwner = getScene() != null ? getScene().getFocusOwner() : null;
        if (focusOwner instanceof TextInputControl) {
            // Inside a field the shortcut belongs to the field's own text history.
            return;
        }
        if (event.getCode() == KeyCode.Z) {
            if (event.isShiftDown()) {
                redo();
            } else {
                undo();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.Y) {
            redo();
            event.consume();
        }
    }

    // ── Edits ───────────────────────────────────────────────────────────────

    /** One reversible delete or reorder. */
    private interface FlowEdit {
        void undo();

        void redo();
    }

    private final class DeleteEdit implements FlowEdit {
        private final int index;
        private final TestStep step;

        private DeleteEdit(int index, TestStep step) {
            this.index = index;
            this.step = step;
        }

        @Override
        public void undo() {
            if (indexOfStep(step) >= 0) {
                return;
            }
            insertCardAt(Math.min(index, cardViews.size()), step);
        }

        @Override
        public void redo() {
            int current = indexOfStep(step);
            if (current >= 0) {
                detachCard(current);
            }
        }
    }

    private final class MoveEdit implements FlowEdit {
        private final TestStep step;
        private final int from;
        private final int to;

        private MoveEdit(TestStep step, int from, int to) {
            this.step = step;
            this.from = from;
            this.to = to;
        }

        @Override
        public void undo() {
            applyMoveTo(step, from);
        }

        @Override
        public void redo() {
            applyMoveTo(step, to);
        }
    }

    private void insertCardAt(int index, TestStep step) {
        StepCardView card = buildCard(step);
        cardViews.add(Math.max(0, Math.min(index, cardViews.size())), card);
        renderOrder();
        reindexSteps();
        updateEmptyState();
        selectCard(card);
        scrollIntoView(card);
    }

    /** Brings a restored card back into the viewport after an undo. */
    private void scrollIntoView(StepCardView card) {
        cardsBox.applyCss();
        cardsBox.layout();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double scrollable = cardsBox.getHeight() - viewportHeight;
        if (scrollable <= 0) {
            return;
        }
        double offset = card.getBoundsInParent().getMinY() - cardsBox.getPadding().getTop();
        double value = offset / scrollable;
        scrollPane.setVvalue(Math.max(scrollPane.getVmin(),
                Math.min(scrollPane.getVmax(), value)));
    }

    /** Removes the card at {@code index} from the view and the list, without recording history. */
    private void detachCard(int index) {
        StepCardView card = cardViews.remove(index);
        cardsBox.getChildren().remove(card);
        if (selectedCard == card) {
            selectedCard = null;
            if (onStepSelected != null) {
                onStepSelected.accept(null);
            }
        }
        reindexSteps();
        updateEmptyState();
    }

    /**
     * Deletes a card after its own confirmation; the removal stays undoable so a mis-click on a
     * configured step is recoverable without re-typing the selector.
     */
    private void requestRemove(StepCardView card) {
        int index = cardViews.indexOf(card);
        if (index < 0) {
            return;
        }
        TestStep step = card.getStep();
        detachCard(index);
        push(new DeleteEdit(index, step));
        notifyChange();
        ToastService.info("Đã xoá bước " + String.format("%02d", index + 1)
                + " — Ctrl/Cmd+Z để hoàn tác.");
    }

    private int indexOfStep(TestStep step) {
        for (int i = 0; i < cardViews.size(); i++) {
            if (cardViews.get(i).getStep() == step) {
                return i;
            }
        }
        return -1;
    }

    /** Puts the cards back in {@link #cardViews} order, dropping the empty state when there is one. */
    private void renderOrder() {
        if (cardViews.isEmpty()) {
            updateEmptyState();
            return;
        }
        cardsBox.getChildren().setAll(cardViews);
    }

    private void reindexSteps() {
        for (int i = 0; i < cardViews.size(); i++) {
            cardViews.get(i).setStepNumber(i + 1);
        }
    }

    private void updateEmptyState() {
        boolean empty = cardViews.isEmpty();
        boolean showing = cardsBox.getChildren().contains(emptyState);
        if (empty && !showing) {
            cardsBox.getChildren().add(emptyState);
        } else if (!empty && showing) {
            cardsBox.getChildren().remove(emptyState);
        }
    }

    private void copyStepCommand(TestStep step) {
        if (step == null) {
            return;
        }
        // The single statement, not a rendered script: a disabled step renders a placeholder line,
        // and copying that would look like a successful copy of a command that does not exist.
        String command = StepScriptBidirectionalSync.statement(step).trim();
        if (command.isBlank()) {
            ToastService.warn("Bước đang tắt hoặc chưa có lệnh — bật bước để sao chép.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(command);
        Clipboard.getSystemClipboard().setContent(content);
        ToastService.success("Đã sao chép lệnh Playwright của bước.");
    }

    private void notifyChange() {
        if (onStepsChanged != null) {
            onStepsChanged.run();
        }
    }

    /** Every authored step, including disabled ones — used when persisting the case. */
    public List<TestStep> getAllSteps() {
        List<TestStep> steps = new ArrayList<>(cardViews.size());
        for (StepCardView view : cardViews) {
            steps.add(view.getStep());
        }
        return steps;
    }

    /**
     * Paints a finished run onto the cards: each step gets the verdict recorded for its number, and
     * the footer states the tally in one line.
     *
     * <p>Steps with no recorded result are cleared rather than left with an older verdict, because a
     * run that stopped early must not keep showing check marks from a previous one.</p>
     *
     * <p>A disabled card is skipped entirely: the run emitted only a placeholder for it, so it has
     * no verdict, and counting it as "not run" would blame the tester for a step they switched off.</p>
     */
    public void applyStepResults(List<StepResult> results) {
        Map<Integer, StepResult> byStep = new HashMap<>();
        if (results != null) {
            for (StepResult result : results) {
                if (result != null) {
                    byStep.put(result.getStepNumber(), result);
                }
            }
        }
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int runningStep = 0;
        for (StepCardView card : cardViews) {
            StepResult result = byStep.get(card.getStep().getStepNumber());
            card.applyExecution(result);
            if (result == null || !card.getStep().isEnabled()) {
                continue;
            }
            switch (result.getStatus()) {
                case StepResult.PASSED -> passed++;
                case StepResult.FAILED -> failed++;
                case StepResult.RUNNING -> runningStep = result.getStepNumber();
                default -> skipped++;
            }
        }
        if (byStep.isEmpty()) {
            clearStepResults();
            return;
        }
        // A live run is still going: the tally is a progress line, not a verdict, and saying
        // "lần chạy gần nhất" mid-run would claim a result that does not exist yet.
        StringBuilder summary = new StringBuilder(runningStep > 0
                ? "Đang chạy bước " + runningStep + "… · " + passed + " đạt"
                : "Lần chạy gần nhất: " + passed + " đạt");
        if (failed > 0) {
            summary.append(" · ").append(failed).append(" lỗi");
        }
        if (skipped > 0 && runningStep == 0) {
            summary.append(" · ").append(skipped).append(" chưa chạy");
        }
        StepResult firstFailure = byStep.values().stream()
                .filter(StepResult::isFailed)
                .min(java.util.Comparator.comparingInt(StepResult::getStepNumber))
                .orElse(null);
        if (firstFailure != null) {
            summary.append(" · dừng ở bước ").append(firstFailure.getStepNumber());
        }
        runSummary.setText(summary.toString());
        runSummary.setVisible(true);
        runSummary.setManaged(true);
        runSummary.setTooltip(new Tooltip(summary.toString()));
        setStyleOf(runSummary, StepCardView.CSS_STATUS_FAILED, failed > 0);
        setStyleOf(runSummary, StepCardView.CSS_STATUS_PASSED,
                failed == 0 && passed > 0 && runningStep == 0);
        setStyleOf(runSummary, StepCardView.CSS_STATUS_RUNNING, runningStep > 0);
    }

    /** Removes every run badge, e.g. before a reload or when the user dismisses the last result. */
    public void clearStepResults() {
        for (StepCardView card : cardViews) {
            card.clearExecution();
        }
        if (runSummary != null) {
            runSummary.setText("");
            runSummary.setTooltip(null);
            runSummary.setVisible(false);
            runSummary.setManaged(false);
            setStyleOf(runSummary, StepCardView.CSS_STATUS_FAILED, false);
            setStyleOf(runSummary, StepCardView.CSS_STATUS_PASSED, false);
            setStyleOf(runSummary, StepCardView.CSS_STATUS_RUNNING, false);
        }
    }

    public boolean hasStepResults() {
        return runSummary != null && runSummary.isVisible();
    }

    private static void setStyleOf(Node node, String styleClass, boolean present) {
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    /** Exposed for layout tests: number of step cards currently rendered. */
    public int getCardCount() {
        return cardViews.size();
    }

    /** Exposed for layout tests: the current card order. */
    public List<StepCardView> getCardViews() {
        return List.copyOf(cardViews);
    }
}
