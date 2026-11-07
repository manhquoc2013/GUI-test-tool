package com.studio.controller;

import com.studio.model.ExportLanguage;
import com.studio.util.DialogTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Modal dialog prompting the user to choose their preferred export language
 * (Vietnamese default or English) before selecting the output file path.
 */
public final class ExportLanguageDialog {

    private ExportLanguageDialog() {}

    public static Optional<ExportLanguage> show(Window owner, String exportTitle) {
        Dialog<ExportLanguage> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        dialog.setTitle("Chọn ngôn ngữ xuất / Export Language");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("export-language-dialog");
        pane.setPrefWidth(480);

        ButtonType okButtonType = new ButtonType("Tiếp tục (Chọn file)", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Hủy bỏ", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(okButtonType, cancelButtonType);

        // Header section
        Label titleLabel = new Label(exportTitle != null && !exportTitle.isBlank()
                ? exportTitle
                : "Xuất dữ liệu kiểm thử");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;");

        Label descLabel = new Label("Vui lòng chọn ngôn ngữ hiển thị cho các tiêu đề cột, thẻ tổng quan và báo cáo xuất ra:");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -fx-text-muted-color;");

        VBox headerBox = new VBox(4, titleLabel, descLabel);

        // Language options
        ToggleGroup group = new ToggleGroup();

        RadioButton viRadio = new RadioButton("Tiếng Việt (Mặc định)");
        viRadio.setToggleGroup(group);
        viRadio.setSelected(true);
        viRadio.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label viDesc = new Label("Báo cáo và kịch bản hiển thị tiêu đề, trạng thái và tổng hợp bằng Tiếng Việt.");
        viDesc.setWrapText(true);
        viDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-muted-color; -fx-padding: 0 0 0 24px;");

        VBox viCard = new VBox(3, viRadio, viDesc);
        viCard.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-accent-color; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
        viCard.setOnMouseClicked(e -> viRadio.setSelected(true));

        RadioButton enRadio = new RadioButton("English (Tiếng Anh)");
        enRadio.setToggleGroup(group);
        enRadio.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label enDesc = new Label("Report and test cases formatted with standard English headers and ISTQB/IEEE terms.");
        enDesc.setWrapText(true);
        enDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-muted-color; -fx-padding: 0 0 0 24px;");

        VBox enCard = new VBox(3, enRadio, enDesc);
        enCard.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-box-border; -fx-border-radius: 8px; -fx-border-width: 1px; -fx-cursor: hand;");
        enCard.setOnMouseClicked(e -> enRadio.setSelected(true));

        // Dynamic border highlight on selection
        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == viRadio) {
                viCard.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-accent-color; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
                enCard.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-box-border; -fx-border-radius: 8px; -fx-border-width: 1px; -fx-cursor: hand;");
            } else {
                viCard.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-box-border; -fx-border-radius: 8px; -fx-border-width: 1px; -fx-cursor: hand;");
                enCard.setStyle("-fx-background-color: -fx-control-inner-background; -fx-padding: 12px 14px; -fx-background-radius: 8px; -fx-border-color: -fx-accent-color; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
            }
        });

        VBox contentBox = new VBox(14, headerBox, viCard, enCard);
        contentBox.setPadding(new Insets(16, 20, 16, 20));
        pane.setContent(contentBox);

        DialogTheme.apply(dialog);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return viRadio.isSelected() ? ExportLanguage.VIETNAMESE : ExportLanguage.ENGLISH;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
