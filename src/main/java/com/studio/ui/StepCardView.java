package com.studio.ui;

import com.studio.model.TestStep;
import com.studio.util.DialogTheme;
import com.studio.util.UiIcons;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Interactive visual card representing a single {@link TestStep}.
 *
 * <p>Authoring happens in two steps so a long case stays scannable: the card shows a compact
 * summary — an identity row (order, action) and a description row ("nhập «admin» vào input#email")
 * — and only the opened step reveals its editor. The editor renders just the fields the chosen
 * {@link StepAction} consumes: a click has no value field, a raw statement has no data field,
 * instead of the permanent rows that forced every card to the same height and wasted half the
 * width on a disabled input.</p>
 *
 * <p>A {@link StepAction#NOTE} is the exception: a comment has nothing to expand, so it renders as
 * one editable line instead of a card with two rows and a code editor.</p>
 *
 * <p>All visual styling lives in {@code studio.css} ({@code .step-card*} tokens) so the card
 * follows the active theme instead of pinning dark-theme colours inline.</p>
 */
public final class StepCardView extends VBox {

    private static final int TOOL_ICON_SIZE = 12;
    private static final int DRAG_ICON_SIZE = 14;
    private static final double CHEVRON_EXPANDED_ROTATION = 180;

    private static final String CSS_DISABLED = "step-card-disabled";
    private static final String CSS_RAW = "step-card-raw";
    private static final String CSS_NOTE = "step-card-note";
    private static final String CSS_EXPANDED = "step-card-expanded";
    private static final String CSS_SELECTED = "step-card-selected";
    private static final String CSS_CODE_FIELD = "step-code-field";
    private static final String CSS_SUMMARY_CODE = "step-summary-code";
    private static final String CSS_SUMMARY_INCOMPLETE = "step-summary-incomplete";

    private final TestStep step;
    private final Consumer<StepCardView> onDelete;
    private final Runnable onChange;
    private final Consumer<TestStep> onCopyCommand;
    private final Runnable onInsertAfter;

    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);
    private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper(this, "selected");

    private HBox noteRow;
    private TextField noteField;
    private MenuButton noteChip;
    private Button noteEye;

    private HBox metaRow;
    private HBox summaryRow;
    private Label stepNumLabel;
    private MenuButton actionChip;
    private Label summaryLabel;
    private final Tooltip summaryTooltip = new Tooltip();
    private Button eyeBtn;
    private MenuButton overflowBtn;
    private Button expandBtn;

    private VBox body;
    private Label targetCaption;
    private TextField targetField;
    private VBox targetBlock;
    private Label valueCaption;
    private TextField valueField;
    private VBox valueBlock;
    private TextField descField;

    public StepCardView(TestStep step,
                        Consumer<StepCardView> onDelete,
                        Runnable onChange,
                        Consumer<TestStep> onCopyCommand,
                        Runnable onInsertAfter) {
        this.step = step;
        this.onDelete = onDelete;
        this.onChange = onChange;
        this.onCopyCommand = onCopyCommand;
        this.onInsertAfter = onInsertAfter;

        migrateRawComment();
        initUI();
        applyActionState();
        applyEnabledVisualState();
    }

    /**
     * Older suites stored {@code // comments} as raw statements. They are notes, and migrating here
     * keeps one shape for the card, the note field and the generator: {@code // text} in the script,
     * {@code text} in the model.
     */
    private void migrateRawComment() {
        if (StepAction.fromCode(step.getAction()) == StepAction.RAW
                && PlaywrightDescribe.isComment(step.getTarget())) {
            step.setAction(StepAction.NOTE.code());
            step.setTarget(PlaywrightDescribe.commentText(step.getTarget()));
        }
    }

    private void initUI() {
        getStyleClass().addAll("card", "step-card");
        setSpacing(2);

        noteRow = buildNoteRow();
        metaRow = buildMetaRow();
        summaryRow = buildSummaryRow();
        body = buildBody();

        // Both text rows are the expand affordance; child controls keep their own clicks. Only the
        // identity row is a tab stop, so a long case does not double the keyboard stops per step.
        installExpandGesture(metaRow, true);
        installExpandGesture(summaryRow, false);

        expanded.addListener((obs, was, is) -> applyExpandedState());

        getChildren().addAll(noteRow, metaRow, summaryRow, body);
        applyExpandedState();
    }

    private void installExpandGesture(HBox row, boolean tabStop) {
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !isInteractive(event.getTarget())) {
                toggleExpanded();
            }
        });
        row.setFocusTraversable(tabStop);
        row.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                toggleExpanded();
                event.consume();
            }
        });
    }

    /**
     * A note is one line: handle, kind, the sentence itself, enable, and the overflow menu. Nothing
     * to expand, so it must not cost the height of a normal step in a long case.
     */
    private HBox buildNoteRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("step-note-row");

        noteChip = new MenuButton();
        noteChip.getStyleClass().addAll("step-action-chip", "flat", "step-note-chip");
        noteChip.setMinWidth(Region.USE_PREF_SIZE);
        noteChip.setTooltip(new Tooltip("Đổi loại hành động của bước"));
        noteChip.setAccessibleText("Loại hành động của bước");
        buildActionMenu(noteChip);

        noteField = new TextField(step.getTarget());
        noteField.getStyleClass().addAll("step-field", "step-note-field");
        noteField.setMinWidth(0);
        noteField.setPromptText("Ghi chú cho người đọc kịch bản");
        HBox.setHgrow(noteField, Priority.ALWAYS);
        noteField.textProperty().addListener((obs, oldV, newV) -> {
            step.setTarget(newV);
            notifyChange();
        });

        noteEye = iconButton(UiIcons.EYE, "Bật / tắt bước này");
        noteEye.setOnAction(e -> toggleEnable());

        MenuButton noteOverflow = buildOverflowButton();
        noteOverflow.visibleProperty().bind(hoverProperty().or(selected).or(noteOverflow.showingProperty()));

        row.getChildren().addAll(dragHandle(), noteChip, noteField, noteEye, noteOverflow);
        return row;
    }

    /**
     * Identity row: order, action, and the expand chevron. Kept separate from the summary row so a
     * narrow editor (a ~300px code panel) never has to squeeze the action chip, the description and
     * three icon buttons into one line — which is what forced every label to an ellipsis before.
     */
    private HBox buildMetaRow() {
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("step-card-header");

        stepNumLabel = new Label(formatStepNumber(step.getStepNumber()));
        stepNumLabel.getStyleClass().addAll("badge", "badge-pill", "step-num-badge");
        stepNumLabel.setMinWidth(Region.USE_PREF_SIZE);

        actionChip = new MenuButton();
        actionChip.getStyleClass().addAll("step-action-chip", "flat");
        actionChip.setMinWidth(Region.USE_PREF_SIZE);
        actionChip.setTooltip(new Tooltip("Đổi loại hành động của bước"));
        actionChip.setAccessibleText("Loại hành động của bước");
        buildActionMenu(actionChip);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        expandBtn = iconButton(UiIcons.CHEVRON_DOWN, "Mở rộng / thu gọn chi tiết bước");
        expandBtn.getStyleClass().add("step-expand-btn");
        expandBtn.setOnAction(e -> toggleExpanded());

        header.getChildren().addAll(dragHandle(), stepNumLabel, actionChip, spacer, expandBtn);
        return header;
    }

    /**
     * Grouped so a long picker stays scannable: an assertion or a note must be findable without
     * reading every action, and the separator doubles as the group heading.
     */
    private void buildActionMenu(MenuButton menu) {
        for (StepAction.Group group : StepAction.Group.values()) {
            if (!menu.getItems().isEmpty()) {
                menu.getItems().add(new SeparatorMenuItem());
            }
            for (StepAction action : StepAction.values()) {
                if (action.group() != group) {
                    continue;
                }
                MenuItem item = new MenuItem(action.label());
                item.setGraphic(UiIcons.icon(action.iconPath(), TOOL_ICON_SIZE));
                item.setOnAction(e -> changeAction(action));
                menu.getItems().add(item);
            }
        }
    }

    /** Description row: the step in words, plus the quick enable toggle and the overflow menu. */
    private HBox buildSummaryRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("step-card-summary-row");

        summaryLabel = new Label();
        summaryLabel.getStyleClass().add("step-summary");
        summaryLabel.setMinWidth(0);
        summaryLabel.setMaxWidth(Double.MAX_VALUE);
        summaryLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        summaryLabel.setTooltip(summaryTooltip);
        HBox.setHgrow(summaryLabel, Priority.ALWAYS);

        eyeBtn = iconButton(UiIcons.EYE, "Bật / tắt bước này");
        eyeBtn.setOnAction(e -> toggleEnable());

        overflowBtn = buildOverflowButton();
        // Secondary actions stay out of the scan path until the card is hovered or selected, but the
        // space is still reserved so the row never shifts when they appear.
        overflowBtn.visibleProperty().bind(
                hoverProperty().or(selected).or(overflowBtn.showingProperty()));

        row.getChildren().addAll(summaryLabel, eyeBtn, overflowBtn);
        return row;
    }

    private MenuButton buildOverflowButton() {
        MenuButton menu = new MenuButton();
        menu.getStyleClass().addAll("step-overflow-btn", "flat");
        menu.setGraphic(UiIcons.icon(UiIcons.MENU, TOOL_ICON_SIZE));
        menu.setTooltip(new Tooltip("Thao tác khác"));
        menu.setAccessibleText("Thao tác khác cho bước này");
        menu.setFocusTraversable(true);

        MenuItem insertItem = new MenuItem("Chèn bước bên dưới");
        insertItem.setGraphic(UiIcons.icon(UiIcons.PLUS, TOOL_ICON_SIZE));
        insertItem.setOnAction(e -> insertAfter());
        MenuItem copyItem = new MenuItem("Sao chép lệnh Playwright");
        copyItem.setGraphic(UiIcons.icon(UiIcons.COPY, TOOL_ICON_SIZE));
        copyItem.setOnAction(e -> copyCommand());
        MenuItem deleteItem = new MenuItem("Xoá bước");
        deleteItem.setGraphic(UiIcons.icon(UiIcons.TRASH, TOOL_ICON_SIZE));
        deleteItem.setOnAction(e -> requestDelete());
        // Inserting a missed step is a menu action: dragging a step from the end back into the
        // middle of a long case is the slowest way to repair a recorded flow.
        menu.getItems().addAll(insertItem, copyItem, deleteItem);
        return menu;
    }

    private Label dragHandle() {
        Label handle = new Label();
        handle.getStyleClass().add("step-drag-handle");
        handle.setTooltip(new Tooltip("Kéo để đổi thứ tự bước"));
        UiIcons.setGraphic(handle, UiIcons.DRAG, DRAG_ICON_SIZE);
        return handle;
    }

    private VBox buildBody() {
        VBox editor = new VBox(10);
        editor.getStyleClass().add("step-body");

        targetCaption = new Label();
        targetCaption.getStyleClass().add("step-field-caption");
        targetCaption.setWrapText(true);
        targetField = new TextField(step.getTarget());
        targetField.getStyleClass().add("step-field");
        targetField.setMinWidth(0);
        targetField.textProperty().addListener((obs, oldV, newV) -> {
            step.setTarget(newV);
            updateSummary();
            notifyChange();
        });
        targetBlock = new VBox(4, targetCaption, targetField);
        targetBlock.getStyleClass().add("step-target-block");

        valueCaption = new Label();
        valueCaption.getStyleClass().add("step-field-caption");
        valueCaption.setWrapText(true);
        valueField = new TextField(step.getTestData());
        valueField.getStyleClass().add("step-field");
        valueField.setMinWidth(0);
        valueField.textProperty().addListener((obs, oldV, newV) -> {
            step.setTestData(newV);
            updateSummary();
            notifyChange();
        });
        valueBlock = new VBox(4, valueCaption, valueField);
        valueBlock.getStyleClass().add("step-value-block");

        Label descCaption = new Label("Ghi chú");
        descCaption.getStyleClass().add("step-field-caption");
        descField = new TextField(step.getDescription());
        descField.getStyleClass().add("step-field");
        descField.setMinWidth(0);
        descField.setPromptText("Mô tả ngắn cho bước (tùy chọn)");
        descField.textProperty().addListener((obs, oldV, newV) -> {
            step.setDescription(newV);
            notifyChange();
        });

        VBox descBlock = new VBox(4, descCaption, descField);
        descBlock.getStyleClass().add("step-desc-block");
        editor.getChildren().addAll(targetBlock, valueBlock, descBlock);
        return editor;
    }

    /** Switches the step to {@code action} and re-renders the fields it consumes. */
    private void changeAction(StepAction action) {
        if (action == null || action == currentAction()) {
            return;
        }
        step.setAction(action.code());
        applyActionState();
        // Choosing an action is an authoring gesture: open the editor so the fields it just asked
        // for are reachable without a second click. A note has no editor, so it stays closed.
        if (action != StepAction.NOTE) {
            expand();
        }
        notifyChange();
    }

    /** Rebuilds chip, summary, and field visibility from the model. */
    private void applyActionState() {
        StepAction action = currentAction();
        boolean note = action == StepAction.NOTE;

        applyChip(actionChip, action);
        applyChip(noteChip, action);

        targetBlock.setVisible(action.needsTarget());
        targetBlock.setManaged(action.needsTarget());
        if (action.needsTarget()) {
            targetCaption.setText(action.targetLabel());
            targetField.setPromptText("Ví dụ: " + action.targetExample());
        }

        valueBlock.setVisible(action.needsValue());
        valueBlock.setManaged(action.needsValue());
        if (action.needsValue()) {
            valueCaption.setText(action.valueLabel());
            valueField.setPromptText("Ví dụ: " + action.valueExample());
        }

        targetField.getStyleClass().remove(CSS_CODE_FIELD);
        if (action.isRaw()) {
            targetField.getStyleClass().add(CSS_CODE_FIELD);
        }

        toggleClass(CSS_RAW, action.isRaw());
        toggleClass(CSS_NOTE, note);

        if (note && !noteField.getText().equals(step.getTarget())) {
            noteField.setText(step.getTarget());
        }
        applyLayoutMode();
        updateSummary();
    }

    private static void applyChip(MenuButton chip, StepAction action) {
        chip.setText(action.label());
        chip.setAccessibleText("Hành động: " + action.label());
        UiIcons.setGraphic(chip, action.iconPath(), TOOL_ICON_SIZE);
        chip.getStyleClass().removeIf(style -> style.startsWith("action-"));
        chip.getStyleClass().add(action.styleClass());
    }

    private void toggleClass(String styleClass, boolean present) {
        if (present) {
            if (!getStyleClass().contains(styleClass)) {
                getStyleClass().add(styleClass);
            }
        } else {
            getStyleClass().remove(styleClass);
        }
    }

    /**
     * A note has no identity row, no summary row and no editor: showing any of them would cost a
     * comment the same height as a real step, which is exactly the noise this replaces.
     */
    private void applyLayoutMode() {
        boolean note = currentAction() == StepAction.NOTE;
        noteRow.setVisible(note);
        noteRow.setManaged(note);
        metaRow.setVisible(!note);
        metaRow.setManaged(!note);
        summaryRow.setVisible(!note);
        summaryRow.setManaged(!note);
        boolean showBody = !note && expanded.get();
        body.setVisible(showBody);
        body.setManaged(showBody);
    }

    private StepAction currentAction() {
        return StepAction.fromCode(step.getAction());
    }

    private void updateSummary() {
        StepAction action = currentAction();
        summaryLabel.setText(action.describe(step.getTarget(), step.getTestData()));
        summaryTooltip.setText(action.label() + ": " + summaryLabel.getText());
        setStyle(summaryLabel, CSS_SUMMARY_CODE,
                action.describeReturnsCode(step.getTarget(), step.getTestData()));
        setStyle(summaryLabel, CSS_SUMMARY_INCOMPLETE,
                !action.isConfigured(step.getTarget(), step.getTestData()));
    }

    private static void setStyle(Node node, String styleClass, boolean present) {
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private void applyExpandedState() {
        applyLayoutMode();
        if (expandBtn != null && expandBtn.getGraphic() != null) {
            expandBtn.getGraphic().setRotate(expanded.get() ? CHEVRON_EXPANDED_ROTATION : 0);
        }
        toggleClass(CSS_EXPANDED, expanded.get() && currentAction() != StepAction.NOTE);
    }

    /** @return true when the card shows its editor fields. */
    public boolean isExpanded() {
        return expanded.get();
    }

    public ReadOnlyBooleanProperty expandedProperty() {
        return expanded;
    }

    /** Opens the editor if it is closed (used when a step is created, so it is editable at once). */
    public void expand() {
        expanded.set(true);
    }

    public void collapse() {
        expanded.set(false);
    }

    public void toggleExpanded() {
        expanded.set(!expanded.get());
    }

    /** @return true when this card renders as a one-line comment instead of a step. */
    public boolean isNote() {
        return currentAction() == StepAction.NOTE;
    }

    /** Places the caret in the first editable field, so a new step can be filled in immediately. */
    public void focusPrimaryField() {
        if (isNote()) {
            noteField.requestFocus();
            return;
        }
        expanded.set(true);
        StepAction action = currentAction();
        TextField field = action.needsTarget() ? targetField : action.needsValue() ? valueField : descField;
        field.requestFocus();
    }

    private Button iconButton(String iconPath, String tooltip) {
        Button button = new Button();
        button.getStyleClass().addAll("flat", "step-tool-btn");
        button.setTooltip(new Tooltip(tooltip));
        // Icon-only controls have no text, so screen readers would announce just "button".
        button.setAccessibleText(tooltip);
        UiIcons.setGraphic(button, iconPath, TOOL_ICON_SIZE);
        return button;
    }

    private static boolean isInteractive(Object eventTarget) {
        Node node = eventTarget instanceof Node n ? n : null;
        while (node != null) {
            if (node instanceof ButtonBase || node instanceof ComboBoxBase) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private void insertAfter() {
        if (onInsertAfter != null) {
            onInsertAfter.run();
        }
    }

    private void copyCommand() {
        if (onCopyCommand != null) {
            onCopyCommand.accept(step);
        }
    }

    /**
     * Deleting a configured step destroys a selector or value the user may have spent minutes on, so
     * it asks first. An empty step carries nothing worth a modal and is removed immediately.
     */
    private void requestDelete() {
        if (onDelete == null) {
            return;
        }
        if (hasContent() && !confirmDelete()) {
            return;
        }
        onDelete.accept(this);
    }

    private boolean hasContent() {
        return !isBlank(step.getTarget()) || !isBlank(step.getTestData());
    }

    private boolean confirmDelete() {
        ButtonType deleteType = new ButtonType("Xoá bước", ButtonType.OK.getButtonData());
        ButtonType cancelType = new ButtonType("Huỷ", ButtonType.CANCEL.getButtonData());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Bước " + stepNumLabel.getText() + " sẽ bị xoá khỏi kịch bản.", deleteType, cancelType);
        alert.setTitle("Xoá bước");
        alert.setHeaderText("Xoá bước " + stepNumLabel.getText() + "?");
        alert.initOwner(getScene() != null ? getScene().getWindow() : null);
        DialogTheme.apply(alert);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == deleteType;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String formatStepNumber(int number) {
        return String.format("%02d", number);
    }

    /** Applies the model's enabled flag to the card (opacity + eye glyph) without notifying. */
    private void applyEnabledVisualState() {
        boolean enabled = step.isEnabled();
        toggleClass(CSS_DISABLED, !enabled);
        for (Button eye : new Button[]{eyeBtn, noteEye}) {
            if (eye == null) {
                continue;
            }
            UiIcons.setGraphic(eye, enabled ? UiIcons.EYE : UiIcons.EYE_OFF, TOOL_ICON_SIZE);
            eye.setTooltip(new Tooltip(enabled ? "Tắt bước này" : "Bật bước này"));
            eye.setAccessibleText(enabled ? "Tắt bước này" : "Bật bước này");
        }
    }

    /** Re-reads every editor field from the model (used after AI Copilot mutates the step). */
    public void refreshFromModel() {
        if (!targetField.getText().equals(step.getTarget())) {
            targetField.setText(step.getTarget());
        }
        if (!noteField.getText().equals(step.getTarget())) {
            noteField.setText(step.getTarget());
        }
        if (!valueField.getText().equals(step.getTestData())) {
            valueField.setText(step.getTestData());
        }
        if (!descField.getText().equals(step.getDescription())) {
            descField.setText(step.getDescription());
        }
        applyActionState();
        applyEnabledVisualState();
    }

    public void setStepNumber(int number) {
        step.setStepNumber(number);
        stepNumLabel.setText(formatStepNumber(number));
    }

    public void toggleEnable() {
        step.setEnabled(!step.isEnabled());
        applyEnabledVisualState();
        notifyChange();
    }

    public boolean isStepEnabled() {
        return step.isEnabled();
    }

    public TestStep getStep() {
        return step;
    }

    /** Highlight state owned by the flow pane; also reveals the secondary actions. */
    public void setSelected(boolean value) {
        selected.set(value);
        toggleClass(CSS_SELECTED, value);
    }

    public ReadOnlyBooleanProperty selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
