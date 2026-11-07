package com.studio.controller;

import com.studio.model.ExportLanguage;
import com.studio.service.I18nService;
import com.studio.util.DialogTheme;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modal dialog prompting the user to choose their preferred export language.
 * Dynamically loads configured languages from /i18n/languages.json so new languages
 * can be added via resources without modifying Java code.
 */
public final class ExportLanguageDialog {

    private ExportLanguageDialog() {}

    public static Optional<ExportLanguage> show(Window owner, String exportTitle) {
        Dialog<ExportLanguage> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        dialog.setTitle(I18nService.get(I18nService.getDefaultLanguage(), "export.dialog.title"));
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("export-language-dialog");
        pane.setPrefWidth(480);

        ButtonType okButtonType = new ButtonType(
                I18nService.get(I18nService.getDefaultLanguage(), "export.dialog.btn_continue"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(
                I18nService.get(I18nService.getDefaultLanguage(), "export.dialog.btn_cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(okButtonType, cancelButtonType);

        // Header section
        Label titleLabel = new Label(exportTitle != null && !exportTitle.isBlank()
                ? exportTitle
                : I18nService.get(I18nService.getDefaultLanguage(), "export.dialog.header_default"));
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;");

        Label descLabel = new Label(I18nService.get(I18nService.getDefaultLanguage(), "export.dialog.desc"));
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -fx-text-muted-color;");

        VBox headerBox = new VBox(4, titleLabel, descLabel);

        // Dynamic Language options from resources
        List<ExportLanguage> languages = I18nService.getAvailableLanguages();
        ToggleGroup group = new ToggleGroup();
        Map<RadioButton, ExportLanguage> radioMap = new LinkedHashMap<>();
        List<VBox> cards = new ArrayList<>();

        for (ExportLanguage lang : languages) {
            RadioButton rb = new RadioButton(lang.getDisplayName());
            rb.setToggleGroup(group);
            rb.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            if (lang.isDefault() || (group.getSelectedToggle() == null && lang.isVietnamese())) {
                rb.setSelected(true);
            }

            Label desc = new Label(lang.getDescription());
            desc.setWrapText(true);
            desc.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-muted-color; -fx-padding: 0 0 0 24px;");

            VBox card = new VBox(3, rb, desc);
            card.setOnMouseClicked(e -> rb.setSelected(true));
            radioMap.put(rb, lang);
            cards.add(card);
        }

        // If none selected, default to first
        if (group.getSelectedToggle() == null && !radioMap.isEmpty()) {
            radioMap.keySet().iterator().next().setSelected(true);
        }

        // Dynamic border highlight on selection
        Runnable updateBorders = () -> {
            int i = 0;
            for (Map.Entry<RadioButton, ExportLanguage> entry : radioMap.entrySet()) {
                VBox card = cards.get(i++);
                if (entry.getKey().isSelected()) {
                    card.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-accent-color; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
                } else {
                    card.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-box-border; -fx-border-radius: 8px; -fx-border-width: 1px; -fx-cursor: hand;");
                }
            }
        };
        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> updateBorders.run());
        updateBorders.run();

        VBox contentBox = new VBox(14);
        contentBox.getChildren().add(headerBox);
        contentBox.getChildren().addAll(cards);
        contentBox.setPadding(new Insets(16, 20, 16, 20));
        pane.setContent(contentBox);

        DialogTheme.apply(dialog);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                RadioButton selected = (RadioButton) group.getSelectedToggle();
                if (selected != null && radioMap.containsKey(selected)) {
                    return radioMap.get(selected);
                }
                return I18nService.getDefaultLanguage();
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
