package com.studio.controller;

import com.studio.util.BrowserDetector;
import com.studio.util.DialogTheme;
import com.studio.util.RunTargetResolve;
import com.studio.util.StatusDisplay;
import com.studio.service.ToastService;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.Optional;

/** Shared create forms for Group / Suite / Case. */
public final class EntityFormDialog {

    private static final double DIALOG_WIDTH = 560;

    public enum Kind { GROUP, SUITE, CASE }

    public record Result(
            String name,
            String description,
            String status,
            String startUrl,
            String browser,
            Boolean headless,
            boolean startRecord
    ) {
        /** Seed / non-case results — never auto-start recording. */
        public Result(String name, String description, String status,
                      String startUrl, String browser, Boolean headless) {
            this(name, description, status, startUrl, browser, headless, false);
        }
    }

    private EntityFormDialog() {}

    /**
     * @param parentLabel parent group name; null/blank/"Project" → no “trong: …” suffix
     */
    public static Optional<Result> showCreateGroup(String parentLabel) {
        String header = isRootProjectLabel(parentLabel)
                ? "Tạo nhóm mới"
                : "Tạo nhóm mới trong: " + parentLabel;
        return show("Nhóm mới", header, Kind.GROUP, null, null, false);
    }

    public static Optional<Result> showCreateSuite(String parentLabel) {
        String header = isRootProjectLabel(parentLabel)
                ? "Tạo suite mới"
                : "Tạo suite mới trong nhóm: " + parentLabel;
        return show("Suite mới", header, Kind.SUITE, "DRAFT", null, false);
    }

    public static Optional<Result> showCreateCase(String suiteLabel) {
        return show("Case mới",
                "Tạo case mới trong suite: " + suiteLabel,
                Kind.CASE,
                "Draft",
                null,
                false);
    }

    public static Optional<Result> showEditGroup(Result seed) {
        return show("Sửa nhóm", "Cập nhật thông tin nhóm", Kind.GROUP, null, seed, true);
    }

    public static Optional<Result> showEditSuite(Result seed) {
        return show("Sửa suite", "Cập nhật thông tin suite", Kind.SUITE, "DRAFT", seed, true);
    }

    public static Optional<Result> showEditCase(Result seed) {
        return show("Sửa case", "Cập nhật thông tin case", Kind.CASE, "Draft", seed, true);
    }

    private static boolean isRootProjectLabel(String label) {
        if (label == null || label.isBlank()) return true;
        String t = label.trim();
        return "Project".equalsIgnoreCase(t)
                || "Gốc dự án".equalsIgnoreCase(t)
                || "Dự án".equalsIgnoreCase(t)
                || "Tất cả".equalsIgnoreCase(t);
    }

    private static Optional<Result> show(String title, String header, Kind kind, String defaultStatus,
                                         Result seed, boolean editing) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setGraphic(null);
        DialogTheme.apply(dialog);
        dialog.setGraphic(null);
        dialog.getDialogPane().setGraphic(null);

        ButtonType okType = new ButtonType(editing ? "Lưu" : "Tạo", ButtonBar.ButtonData.OK_DONE);
        ButtonType saveRecordType = kind == Kind.CASE
                ? new ButtonType("Lưu và ghi", ButtonBar.ButtonData.APPLY)
                : null;
        ButtonType cancelType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (saveRecordType != null) {
            dialog.getDialogPane().getButtonTypes().setAll(okType, saveRecordType, cancelType);
        } else {
            dialog.getDialogPane().getButtonTypes().setAll(okType, cancelType);
        }

        TextField nameField = new TextField();
        nameField.setPromptText(switch (kind) {
            case GROUP -> "Nhập tên nhóm";
            case SUITE -> "Nhập tên suite";
            case CASE -> "Nhập tên case";
        });
        nameField.setMaxWidth(Double.MAX_VALUE);

        TextArea descArea = new TextArea();
        descArea.setPromptText("Mô tả (tuỳ chọn)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);
        descArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(descArea, Priority.NEVER);

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.setMaxWidth(Double.MAX_VALUE);
        TextField urlField = new TextField();
        urlField.setPromptText("https://...");
        urlField.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> browserCombo = new ComboBox<>();
        browserCombo.setMaxWidth(Double.MAX_VALUE);
        fillBrowserCombo(browserCombo);
        browserCombo.setValue(RunTargetResolve.BROWSER_DEFAULT_LABEL);
        CheckBox headlessCheck = new CheckBox("Ẩn trình duyệt");

        if (kind == Kind.CASE) {
            statusCombo.getItems().addAll("Draft", "Ready", "Inactive");
            statusCombo.setValue(defaultStatus);
            statusCombo.setButtonCell(statusCell());
            statusCombo.setCellFactory(lv -> statusCell());
        } else if (kind == Kind.SUITE) {
            statusCombo.getItems().addAll("DRAFT", "ACTIVE", "INACTIVE");
            statusCombo.setValue(defaultStatus);
            statusCombo.setButtonCell(statusCell());
            statusCombo.setCellFactory(lv -> statusCell());
        }

        if (seed != null) {
            nameField.setText(seed.name() != null ? seed.name() : "");
            descArea.setText(seed.description() != null ? seed.description() : "");
            if (kind == Kind.SUITE || kind == Kind.CASE) {
                if (seed.status() != null && !seed.status().isBlank()) {
                    statusCombo.setValue(seed.status());
                }
            }
            if (kind == Kind.CASE) {
                urlField.setText(seed.startUrl() != null ? seed.startUrl() : "");
            }
            if (kind == Kind.SUITE || kind == Kind.CASE) {
                selectBrowser(browserCombo, seed.browser());
                headlessCheck.setSelected(Boolean.TRUE.equals(seed.headless()));
            }
        }

        VBox form = new VBox(12);
        form.getStyleClass().add("entity-form");
        form.setPadding(new Insets(8, 4, 4, 4));
        form.setPrefWidth(DIALOG_WIDTH - 48);
        form.setMinWidth(DIALOG_WIDTH - 48);

        form.getChildren().add(fieldBlock(switch (kind) {
            case GROUP -> "Tên nhóm";
            case SUITE -> "Tên suite";
            case CASE -> "Tên case";
        }, nameField));
        form.getChildren().add(fieldBlock("Mô tả", descArea));

        if (kind == Kind.SUITE || kind == Kind.CASE) {
            form.getChildren().add(fieldBlock("Trạng thái", statusCombo));
        }
        if (kind == Kind.CASE) {
            form.getChildren().add(fieldBlock("URL bắt đầu", urlField));
        }
        if (kind == Kind.SUITE || kind == Kind.CASE) {
            VBox browserBlock = fieldBlock("Trình duyệt", browserCombo);
            form.getChildren().add(headlessCheck);
            form.getChildren().add(browserBlock);
            Runnable syncBrowserVisibility = () -> {
                boolean showBrowser = !headlessCheck.isSelected();
                browserBlock.setVisible(showBrowser);
                browserBlock.setManaged(showBrowser);
            };
            headlessCheck.selectedProperty().addListener((obs, o, n) -> syncBrowserVisibility.run());
            syncBrowserVisibility.run();
        }

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);
        dialog.getDialogPane().setMinWidth(DIALOG_WIDTH);
        nameField.requestFocus();

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        form.getChildren().add(errorLabel);

        if (saveRecordType != null) {
            javafx.scene.control.Button saveRecordBtn = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(saveRecordType);
            if (saveRecordBtn != null) {
                saveRecordBtn.getStyleClass().add("btn-success");
                saveRecordBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                    String name = nameField.getText() != null ? nameField.getText().trim() : "";
                    String url = urlField.getText() != null ? urlField.getText().trim() : "";
                    if (name.isEmpty()) {
                        e.consume();
                        showFormError(errorLabel, "Tên không được để trống");
                        nameField.requestFocus();
                        return;
                    }
                    if (url.isEmpty()) {
                        e.consume();
                        showFormError(errorLabel, "Điền URL bắt đầu trước khi ghi hình");
                        urlField.requestFocus();
                        return;
                    }
                    errorLabel.setVisible(false);
                    errorLabel.setManaged(false);
                });
            }
        }

        javafx.scene.control.Button okBtn = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(okType);
        if (okBtn != null) {
            okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                String name = nameField.getText() != null ? nameField.getText().trim() : "";
                if (name.isEmpty()) {
                    e.consume();
                    showFormError(errorLabel, "Tên không được để trống");
                    nameField.requestFocus();
                } else {
                    errorLabel.setVisible(false);
                    errorLabel.setManaged(false);
                }
            });
        }

        dialog.setResultConverter(btn -> {
            if (btn == null || btn == cancelType) return null;
            boolean startRecord = saveRecordType != null && btn == saveRecordType;
            if (btn != okType && !startRecord) return null;
            String name = nameField.getText() != null ? nameField.getText().trim() : "";
            if (name.isEmpty()) return null;
            if (startRecord) {
                String url = urlField.getText() != null ? urlField.getText().trim() : "";
                if (url.isEmpty()) return null;
            }
            boolean headless = kind != Kind.GROUP && headlessCheck.isSelected();
            String browser = kind == Kind.GROUP
                    ? ""
                    : (headless ? "" : RunTargetResolve.persistBrowser(browserCombo.getValue()));
            return new Result(
                    name,
                    descArea.getText() != null ? descArea.getText().trim() : "",
                    kind == Kind.GROUP ? null : statusCombo.getValue(),
                    kind == Kind.CASE && urlField.getText() != null ? urlField.getText().trim() : "",
                    browser,
                    kind == Kind.GROUP ? null : headless,
                    startRecord
            );
        });

        return dialog.showAndWait()
                .filter(r -> r != null && r.name() != null && !r.name().isBlank());
    }

    private static void showFormError(Label errorLabel, String message) {
        errorLabel.setText("⚠️ " + message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        ToastService.warn("⚠️ " + message);
    }

    private static VBox fieldBlock(String labelText, javafx.scene.Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setPrefWidth(Region.USE_COMPUTED_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(false);
        label.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        label.setTextAlignment(TextAlignment.LEFT);
        VBox block = new VBox(4, label, control);
        block.setFillWidth(true);
        return block;
    }

    public static void fillBrowserCombo(ComboBox<String> combo) {
        combo.getItems().clear();
        combo.getItems().add(RunTargetResolve.BROWSER_DEFAULT_LABEL);
        for (BrowserDetector.BrowserInfo info : BrowserDetector.detectAvailable()) {
            if (!combo.getItems().contains(info.name)) {
                combo.getItems().add(info.name);
            }
        }
    }

    public static void selectBrowser(ComboBox<String> combo, String stored) {
        if (RunTargetResolve.isDefaultBrowser(stored)) {
            combo.setValue(RunTargetResolve.BROWSER_DEFAULT_LABEL);
            return;
        }
        String id = RunTargetResolve.normalizeBrowserId(stored);
        for (String item : combo.getItems()) {
            if (item != null && RunTargetResolve.normalizeBrowserId(item).equals(id)
                    && !RunTargetResolve.isDefaultBrowser(item)) {
                combo.setValue(item);
                return;
            }
        }
        combo.setValue(RunTargetResolve.BROWSER_DEFAULT_LABEL);
    }

    /** Two-column labeled row for Explorer preview forms. */
    public static void configureLabelColumn(GridPane grid, double labelWidth) {
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(labelWidth);
        c0.setPrefWidth(labelWidth);
        c0.setHgrow(Priority.NEVER);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setMinWidth(200);
        grid.getColumnConstraints().setAll(c0, c1);
    }

    private static javafx.scene.control.ListCell<String> statusCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : StatusDisplay.label(item));
            }
        };
    }
}
