package com.studio.controller;

import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.service.ToastService;
import com.studio.store.ProjectStore;
import com.studio.util.AssertTypeDisplay;
import com.studio.util.DialogTheme;
import com.studio.util.StatusDisplay;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.stage.Modality;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller for the assertion-builder popup dialog.
 * Manages adding, removing, saving, and skipping assertions for a test case.
 */
public class AssertBuilderPopupController extends Dialog<List<Assertion>> {

    @FXML private Label titleLabel;
    @FXML private TextField assertSearchField;
    @FXML private ListView<String> assertListView;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField valueField;
    @FXML private TextField locatorField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Button addBtn;
    @FXML private CheckBox exactCheck;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private Button closeBtn;
    @FXML private Button saveBtn;

    private final ObservableList<Assertion> assertions = FXCollections.observableArrayList();
    private TestCase testCase;
    private Path suitePath;
    private Runnable onSaved;
    private Integer dragSourceIndex;
    /** When non-null, Add button updates this index instead of appending. */
    private Integer editingIndex;
    private boolean suppressListSelect;
    /** ListView row → assertions index (search may hide rows). */
    private final List<Integer> visibleAssertIndices = new ArrayList<>();
    /** Snapshot of assertions when dialog opened / last saved. */
    private List<String> baselineFingerprint = List.of();
    /** After user confirmed discard (or no dirty), skip a second confirm on WINDOW_CLOSE_REQUEST. */
    private boolean closeAllowed;

    private final ProjectStore projectStore = new ProjectStore();

    public AssertBuilderPopupController() {
        // Instantiated by FXMLLoader via fx:controller
    }

    public static AssertBuilderPopupController create() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AssertBuilderPopupController.class.getResource("/fxml/assert_builder_popup.fxml"));
            Parent content = loader.load();
            AssertBuilderPopupController controller = loader.getController();
            DialogPane pane = controller.getDialogPane();
            pane.setContent(content);
            pane.getButtonTypes().clear();
            DialogTheme.apply(controller);
            controller.wireWindowCloseConfirm();
            return controller;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load assert builder FXML", e);
        }
    }

    @FXML
    public void initialize() {
        typeCombo.setItems(FXCollections.observableArrayList(AssertTypeDisplay.ALL_TYPES));
        AssertTypeDisplay.wireTypeCombo(typeCombo);
        typeCombo.setValue("TEXT_VISIBLE");
        statusCombo.setItems(FXCollections.observableArrayList("ACTIVE", "DRAFT", "INACTIVE"));
        statusCombo.setButtonCell(statusListCell());
        statusCombo.setCellFactory(lv -> statusListCell());
        statusCombo.setValue("ACTIVE");
        statusCombo.getStyleClass().add("status-combo");
        if (timeoutSpinner.getValueFactory() == null) {
            timeoutSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1000, 120000, 30000, 1000));
        } else {
            timeoutSpinner.getValueFactory().setValue(30000);
        }
        timeoutSpinner.getStyleClass().add("number-spinner");
        addBtn.setOnAction(e -> addAssert());
        saveBtn.setOnAction(e -> save());
        if (closeBtn != null) closeBtn.setOnAction(e -> closeDialog());
        com.studio.util.UiIcons.setGraphic(addBtn, com.studio.util.UiIcons.PLUS, 12);
        com.studio.util.UiIcons.setGraphic(saveBtn, com.studio.util.UiIcons.SAVE, 12);
        if (closeBtn != null) com.studio.util.UiIcons.setGraphic(closeBtn, com.studio.util.UiIcons.CLOSE, 12);
        setupListDragDrop();
        if (assertSearchField != null) {
            assertSearchField.textProperty().addListener((obs, o, n) -> refreshList());
        }
        assertListView.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (suppressListSelect || n == null || n.intValue() < 0) return;
            int real = toAssertIndex(n.intValue());
            if (real >= 0) {
                startEditing(real);
            }
        });
    }

    private void wireWindowCloseConfirm() {
        try {
            initModality(Modality.APPLICATION_MODAL);
        } catch (IllegalStateException ignored) {
            // already set via DialogTheme
        }
        setOnShowing(ev -> {
            closeAllowed = false;
            if (getDialogPane().getScene() == null) return;
            var window = getDialogPane().getScene().getWindow();
            window.setOnCloseRequest(e -> {
                if (closeAllowed) {
                    return;
                }
                e.consume();
                if (!confirmDiscardIfDirty()) {
                    return;
                }
                forceClose();
            });
            window.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    e.consume();
                    closeDialog();
                }
            });
        });
    }

    private static ListCell<String> statusListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(StatusDisplay.label(item));
                }
            }
        };
    }

    private void setupListDragDrop() {
        assertListView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                private final HBox row = new HBox(8);
                private final Label infoLabel = new Label();
                private final Button deleteBtn = new Button("Xóa");

                {
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("assert-row");
                    infoLabel.getStyleClass().add("assert-row-label");
                    infoLabel.setMaxWidth(Double.MAX_VALUE);
                    infoLabel.setWrapText(true);
                    HBox.setHgrow(infoLabel, Priority.ALWAYS);
                    deleteBtn.getStyleClass().add("assert-row-delete-btn");
                    deleteBtn.setFocusTraversable(false);
                    deleteBtn.setTooltip(new Tooltip("Xóa assertion này"));
                    deleteBtn.addEventFilter(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
                    deleteBtn.setOnAction(e -> {
                        int viewIdx = getIndex();
                        int idx = toAssertIndex(viewIdx);
                        if (idx >= 0 && idx < assertions.size()) {
                            removeAt(idx);
                        }
                        e.consume();
                    });
                    row.getChildren().addAll(infoLabel, deleteBtn);
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    infoLabel.setText(item);
                    setText(null);
                    setGraphic(row);
                }
            };
            cell.setOnDragDetected(e -> {
                if (cell.isEmpty()) return;
                if (assertSearchActive()) return;
                if (e.getTarget() instanceof javafx.scene.Node node
                        && findAncestorButton(node) != null) {
                    return;
                }
                int real = toAssertIndex(cell.getIndex());
                if (real < 0) return;
                dragSourceIndex = real;
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(dragSourceIndex));
                db.setContent(content);
                e.consume();
            });
            cell.setOnDragOver(e -> {
                if (assertSearchActive()) {
                    e.consume();
                    return;
                }
                if (e.getGestureSource() != cell && e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            cell.setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                boolean success = false;
                if (!assertSearchActive() && db.hasString() && dragSourceIndex != null) {
                    int from = dragSourceIndex;
                    int to = cell.isEmpty()
                            ? assertions.size() - 1
                            : toAssertIndex(cell.getIndex());
                    if (from >= 0 && to >= 0 && from < assertions.size() && to < assertions.size() && from != to) {
                        Assertion moved = assertions.remove(from);
                        assertions.add(to, moved);
                        if (editingIndex != null) {
                            if (editingIndex == from) {
                                editingIndex = to;
                            } else if (from < editingIndex && to >= editingIndex) {
                                editingIndex--;
                            } else if (from > editingIndex && to <= editingIndex) {
                                editingIndex++;
                            }
                        }
                        refreshList();
                        selectAssertIndex(to);
                        success = true;
                    }
                }
                e.setDropCompleted(success);
                dragSourceIndex = null;
                e.consume();
            });
            return cell;
        });
    }

    private boolean assertSearchActive() {
        return assertSearchField != null
                && assertSearchField.getText() != null
                && !assertSearchField.getText().isBlank();
    }

    private int toAssertIndex(int viewIndex) {
        if (viewIndex < 0 || viewIndex >= visibleAssertIndices.size()) {
            return -1;
        }
        return visibleAssertIndices.get(viewIndex);
    }

    private void selectAssertIndex(int assertIndex) {
        int view = visibleAssertIndices.indexOf(assertIndex);
        if (view >= 0) {
            assertListView.getSelectionModel().select(view);
        }
    }

    private static Button findAncestorButton(javafx.scene.Node node) {
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (cur instanceof Button b) return b;
            cur = cur.getParent();
        }
        return null;
    }

    private void removeAt(int idx) {
        if (idx < 0 || idx >= assertions.size()) return;
        assertions.remove(idx);
        if (editingIndex != null) {
            if (editingIndex == idx) {
                clearForm();
            } else if (editingIndex > idx) {
                editingIndex--;
            }
        }
        refreshList();
        if (editingIndex != null && editingIndex >= 0 && editingIndex < assertions.size()) {
            startEditing(editingIndex);
        }
    }

    public void setup(TestSuite suite, TestCase testCase, Path suitePath, Runnable onSaved) {
        this.testCase = testCase;
        this.suitePath = suitePath;
        this.onSaved = onSaved;
        this.editingIndex = null;
        this.assertions.clear();
        if (testCase != null) {
            this.assertions.addAll(testCase.getAssertions());
        }
        if (titleLabel != null && testCase != null) {
            titleLabel.setText("Assertions cho " + testCase.getName());
        } else if (titleLabel != null && suite != null) {
            titleLabel.setText("Assertions cho " + suite.getName());
        }
        clearForm();
        refreshList();
        baselineFingerprint = fingerprint(assertions);
    }

    /** Prefill form to edit assertion at index (call after setup). */
    public void startEditing(int index) {
        if (index < 0 || index >= assertions.size()) return;
        Assertion a = assertions.get(index);
        editingIndex = index;
        if (typeCombo != null) typeCombo.setValue(a.getType());
        if (valueField != null) valueField.setText(a.getValue() != null ? a.getValue() : "");
        if (locatorField != null) locatorField.setText(a.getLocator() != null ? a.getLocator() : "");
        if (statusCombo != null) {
            String st = a.getStatus() != null ? StatusDisplay.normalize(a.getStatus()) : "ACTIVE";
            if (!"ACTIVE".equals(st) && !"DRAFT".equals(st) && !"INACTIVE".equals(st)) {
                st = "ACTIVE";
            }
            statusCombo.setValue(st);
        }
        if (exactCheck != null) exactCheck.setSelected(a.isExact());
        if (timeoutSpinner != null && timeoutSpinner.getValueFactory() != null) {
            timeoutSpinner.getValueFactory().setValue(a.getTimeoutMs() > 0 ? a.getTimeoutMs() : 30000);
        }
        if (addBtn != null) {
            addBtn.setText("Cập nhật");
            com.studio.util.UiIcons.setGraphic(addBtn, com.studio.util.UiIcons.SAVE, 12);
        }
        suppressListSelect = true;
        assertListView.getSelectionModel().select(index);
        suppressListSelect = false;
    }

    @FXML
    public void addAssert() {
        String type = typeCombo.getValue();
        if (type == null || type.isBlank()) {
            ToastService.warn("⚠️ Vui lòng chọn loại assertion");
            return;
        }
        String value = valueField != null && valueField.getText() != null
                ? valueField.getText().trim() : "";
        String locator = locatorField != null && locatorField.getText() != null
                ? locatorField.getText().trim() : "";
        if (needsValue(type) && value.isEmpty()) {
            ToastService.warn("⚠️ Loại " + type + " cần điền Giá trị (text cần kiểm tra)");
            if (valueField != null) valueField.requestFocus();
            return;
        }
        if (needsLocator(type) && locator.isEmpty()) {
            ToastService.warn("⚠️ Loại " + type + " cần Locator (selector Playwright)");
            if (locatorField != null) locatorField.requestFocus();
            return;
        }
        Assertion a = (editingIndex != null && editingIndex >= 0 && editingIndex < assertions.size())
                ? assertions.get(editingIndex)
                : new Assertion();
        a.setType(type);
        a.setValue(value);
        a.setLocator(locator);
        String status = statusCombo.getValue();
        a.setStatus(status != null && !status.isBlank() ? status : "ACTIVE");
        if (exactCheck != null) {
            a.setExact(exactCheck.isSelected());
        }
        Integer timeout = timeoutSpinner.getValue();
        a.setTimeoutMs(timeout != null ? timeout : 30000);
        boolean wasEdit = editingIndex != null;
        if (!wasEdit) {
            assertions.add(a);
        }
        refreshList();
        clearForm();
        ToastService.success(wasEdit
                ? "✅ Đã cập nhật assertion trong form — nhớ Lưu"
                : "✅ Đã thêm — nhớ Lưu");
    }

    private static boolean needsValue(String type) {
        return type.startsWith("TEXT_") || type.startsWith("TITLE_") || type.startsWith("URL_")
                || type.equals("PAGE_HAS_TEXT") || type.equals("HEADING_VISIBLE")
                || type.contains("HAS_TEXT") || type.contains("EQUALS") || type.contains("CONTAINS")
                || type.equals("CUSTOM") || type.equals("CUSTOM_JS");
    }

    private static boolean needsLocator(String type) {
        return type.startsWith("ELEMENT_") || type.startsWith("INPUT_")
                || type.equals("ATTRIBUTE_EQUALS") || type.equals("CSS_CONTAINS")
                || type.equals("LINK_HREF_CONTAINS") || type.equals("PLACEHOLDER_EQUALS")
                || type.equals("SELECT_HAS_OPTION") || type.equals("FORM_VALID")
                || type.equals("FORM_INVALID") || type.equals("IMAGE_VISIBLE")
                || type.startsWith("TABLE_");
    }

    @FXML
    public void save() {
        if (suitePath == null || testCase == null) {
            ToastService.error("⚠️ Chưa chọn case để sửa assertions");
            return;
        }
        try {
            projectStore.replaceAssertions(suitePath, testCase.getId(), new ArrayList<>(assertions));
            testCase.setAssertions(new ArrayList<>(assertions));
            ToastService.success("✅ Đã lưu assertions (" + assertions.size() + ")");
        } catch (Exception e) {
            ToastService.error("❌ Không thể lưu assertions: " + e.getMessage());
            return;
        }
        if (onSaved != null) {
            onSaved.run();
        }
        baselineFingerprint = fingerprint(assertions);
        clearForm();
        setResult(new ArrayList<>(assertions));
        forceClose();
    }

    @FXML
    private void closeDialog() {
        if (!closeAllowed && !confirmDiscardIfDirty()) {
            return;
        }
        forceClose();
    }

    private void forceClose() {
        closeAllowed = true;
        setResult(null);
        DialogTheme.armExit(this);
        DialogTheme.animateExit(this, () -> {
            if (isShowing()) {
                hide();
            }
        });
    }

    /** @return true if OK to close (no changes or user confirmed discard) */
    private boolean confirmDiscardIfDirty() {
        if (!hasUnsavedChanges()) {
            return true;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogTheme.apply(confirm);
        confirm.setTitle("Đóng mà không lưu?");
        confirm.setHeaderText("Còn thay đổi assertions chưa lưu");
        confirm.setContentText("Đóng sẽ mất các thay đổi chưa bấm «Lưu Assertions». Tiếp tục đóng?");
        ButtonType discard = new ButtonType("Đóng không lưu", ButtonBar.ButtonData.YES);
        ButtonType stay = new ButtonType("Ở lại", ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(discard, stay);
        return confirm.showAndWait().filter(b -> b == discard).isPresent();
    }

    private boolean hasUnsavedChanges() {
        if (!fingerprint(assertions).equals(baselineFingerprint)) {
            return true;
        }
        return isFormDirty();
    }

    private boolean isFormDirty() {
        String value = valueField != null && valueField.getText() != null
                ? valueField.getText().trim() : "";
        String locator = locatorField != null && locatorField.getText() != null
                ? locatorField.getText().trim() : "";
        if (editingIndex != null && editingIndex >= 0 && editingIndex < assertions.size()) {
            Assertion a = assertions.get(editingIndex);
            String type = typeCombo != null ? typeCombo.getValue() : null;
            String status = statusCombo != null ? statusCombo.getValue() : null;
            boolean exact = exactCheck != null && exactCheck.isSelected();
            int timeout = timeoutSpinner != null && timeoutSpinner.getValue() != null
                    ? timeoutSpinner.getValue() : 30000;
            return !Objects.equals(type, a.getType())
                    || !Objects.equals(value, a.getValue() != null ? a.getValue() : "")
                    || !Objects.equals(locator, a.getLocator() != null ? a.getLocator() : "")
                    || !StatusDisplay.normalize(status).equals(StatusDisplay.normalize(a.getStatus()))
                    || exact != a.isExact()
                    || timeout != a.getTimeoutMs();
        }
        return !value.isEmpty() || !locator.isEmpty();
    }

    private static List<String> fingerprint(List<Assertion> list) {
        List<String> fp = new ArrayList<>(list.size());
        for (Assertion a : list) {
            fp.add(String.join("|",
                    nullToEmpty(a.getType()),
                    nullToEmpty(a.getValue()),
                    nullToEmpty(a.getLocator()),
                    StatusDisplay.normalize(a.getStatus()),
                    String.valueOf(a.isExact()),
                    String.valueOf(a.getTimeoutMs())));
        }
        return fp;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private void refreshList() {
        int keep = editingIndex != null ? editingIndex : toAssertIndex(assertListView.getSelectionModel().getSelectedIndex());
        String query = assertSearchField != null ? assertSearchField.getText() : "";
        suppressListSelect = true;
        visibleAssertIndices.clear();
        assertListView.getItems().clear();
        for (int i = 0; i < assertions.size(); i++) {
            Assertion a = assertions.get(i);
            if (!assertMatchesSearch(a, query)) {
                continue;
            }
            visibleAssertIndices.add(i);
            String detail;
            if (a.getValue() != null && !a.getValue().isBlank()) {
                detail = a.getValue();
            } else if (a.getLocator() != null && !a.getLocator().isBlank()) {
                detail = "locator: " + a.getLocator();
            } else {
                detail = "(chưa có giá trị / locator)";
            }
            String display = String.format("%d. %s  %s — %s",
                    i + 1,
                    StatusDisplay.label(a.getStatus()),
                    AssertTypeDisplay.label(a.getType()),
                    detail);
            assertListView.getItems().add(display);
        }
        if (keep >= 0 && keep < assertions.size()) {
            selectAssertIndex(keep);
        }
        suppressListSelect = false;
    }

    private static boolean assertMatchesSearch(Assertion a, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        String type = a.getType() != null ? a.getType().toLowerCase(java.util.Locale.ROOT) : "";
        String typeLabel = AssertTypeDisplay.label(a.getType()).toLowerCase(java.util.Locale.ROOT);
        String value = a.getValue() != null ? a.getValue().toLowerCase(java.util.Locale.ROOT) : "";
        String locator = a.getLocator() != null ? a.getLocator().toLowerCase(java.util.Locale.ROOT) : "";
        String status = a.getStatus() != null ? a.getStatus().toLowerCase(java.util.Locale.ROOT) : "";
        return type.contains(q) || typeLabel.contains(q) || value.contains(q)
                || locator.contains(q) || status.contains(q);
    }

    private void clearForm() {
        editingIndex = null;
        if (valueField != null) valueField.clear();
        if (locatorField != null) locatorField.clear();
        if (exactCheck != null) exactCheck.setSelected(false);
        if (typeCombo != null) typeCombo.setValue("TEXT_VISIBLE");
        if (statusCombo != null) statusCombo.setValue("ACTIVE");
        if (timeoutSpinner != null) timeoutSpinner.getValueFactory().setValue(30000);
        if (addBtn != null) {
            addBtn.setText("Thêm");
            com.studio.util.UiIcons.setGraphic(addBtn, com.studio.util.UiIcons.PLUS, 12);
        }
    }
}
