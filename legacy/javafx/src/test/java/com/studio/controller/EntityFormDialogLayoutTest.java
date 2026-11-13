package com.studio.controller;

import com.studio.service.ThemeService;
import com.studio.util.DialogTheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EntityFormDialogLayoutTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void testGroupDialogLightModeIsCompact() throws InterruptedException {
        assertDialogHeightCompact("light", EntityFormDialog.Kind.GROUP, 180.0, 360.0);
    }

    @Test
    void testGroupDialogDarkModeIsCompact() throws InterruptedException {
        assertDialogHeightCompact("dark", EntityFormDialog.Kind.GROUP, 180.0, 360.0);
    }

    @Test
    void testCaseDialogLightModeIsCompact() throws InterruptedException {
        assertDialogHeightCompact("light", EntityFormDialog.Kind.CASE, 350.0, 580.0);
    }

    @Test
    void testCaseDialogDarkModeIsCompact() throws InterruptedException {
        assertDialogHeightCompact("dark", EntityFormDialog.Kind.CASE, 350.0, 580.0);
    }

    @Test
    void testCaptureGroupDialogScreenshots() throws InterruptedException {
        captureDialogToFile("dark", EntityFormDialog.Kind.GROUP, "docs/guide/images/02-dialog-new-group.png");
        captureDialogToFile("light", EntityFormDialog.Kind.GROUP, "docs/guide/images/light-02-dialog-new-group.png");
    }

    private void captureDialogToFile(String theme, EntityFormDialog.Kind kind, String outputPath) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ThemeService.setTheme(theme);
                Stage owner = new Stage();
                owner.setScene(new Scene(new javafx.scene.layout.Pane(), 1440, 900));
                owner.setWidth(1440);
                owner.setHeight(900);
                owner.show();

                Dialog<EntityFormDialog.Result> dialog = buildExactDialog(kind, owner);
                dialog.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWN, ev -> {
                    Platform.runLater(() -> {
                        Platform.runLater(() -> {
                            try {
                                DialogPane p = dialog.getDialogPane();
                                javafx.scene.Node shell = (javafx.scene.Node) p.getProperties().get(DialogTheme.SHELL_NODE);
                                if (shell != null) {
                                    shell.setOpacity(1.0);
                                    shell.setScaleX(1.0);
                                    shell.setScaleY(1.0);
                                    shell.setTranslateY(0.0);
                                }
                                p.setOpacity(1.0);
                                javafx.scene.Node target = shell != null ? shell : p;

                                javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                                params.setFill(javafx.scene.paint.Color.TRANSPARENT);
                                javafx.scene.image.WritableImage img = target.snapshot(params, null);

                                int w = (int) img.getWidth();
                                int h = (int) img.getHeight();
                                java.awt.image.BufferedImage bImg = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                javafx.scene.image.PixelReader pr = img.getPixelReader();
                                for (int y = 0; y < h; y++) {
                                    for (int x = 0; x < w; x++) {
                                        bImg.setRGB(x, y, pr.getArgb(x, y));
                                    }
                                }
                                java.io.File file = new java.io.File(outputPath);
                                javax.imageio.ImageIO.write(bImg, "png", file);
                                System.out.println("Captured " + outputPath + " (" + w + "x" + h + ")");
                            } catch (Throwable t) {
                                error.set(t);
                            } finally {
                                dialog.close();
                                owner.close();
                                latch.countDown();
                            }
                        });
                    });
                });
                dialog.show();
            } catch (Throwable t) {
                error.set(t);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Screenshot capture timed out");
        assertNull(error.get(), () -> "Error capturing screenshot: " + error.get());
    }

    private void assertDialogHeightCompact(String theme, EntityFormDialog.Kind kind, double minExpected, double maxExpected) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Double> height = new AtomicReference<>();
        AtomicReference<Double> prefH = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ThemeService.setTheme(theme);
                Stage owner = new Stage();
                owner.setScene(new Scene(new javafx.scene.layout.Pane(), 1440, 900));
                owner.setWidth(1440);
                owner.setHeight(900);
                owner.show();

                Dialog<EntityFormDialog.Result> dialog = buildExactDialog(kind, owner);
                dialog.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWN, ev -> {
                    Platform.runLater(() -> {
                        Platform.runLater(() -> {
                            try {
                                DialogPane p = dialog.getDialogPane();
                                height.set(p.getHeight());
                                prefH.set(p.prefHeight(-1));
                            } finally {
                                dialog.close();
                                owner.close();
                                latch.countDown();
                            }
                        });
                    });
                });
                dialog.show();
            } catch (Throwable t) {
                error.set(t);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Dialog layout timed out");
        assertNull(error.get(), () -> "Error during test: " + error.get());
        assertNotNull(height.get(), "Dialog pane height should be measured");
        assertTrue(height.get() >= minExpected && height.get() <= maxExpected,
                "Dialog pane height (" + height.get() + ") should be compact between "
                        + minExpected + " and " + maxExpected + " (pref=" + prefH.get() + ")");
    }

    private Dialog<EntityFormDialog.Result> buildExactDialog(EntityFormDialog.Kind kind, Stage owner) {
        double DIALOG_WIDTH = 560;
        Dialog<EntityFormDialog.Result> dialog = new Dialog<>();
        dialog.setTitle(kind == EntityFormDialog.Kind.GROUP ? "Nhóm mới" : "Case mới");
        dialog.setHeaderText(kind == EntityFormDialog.Kind.GROUP ? "Tạo nhóm mới" : "Tạo case mới");
        dialog.setGraphic(null);
        dialog.initOwner(owner);
        DialogTheme.apply(dialog);
        dialog.setGraphic(null);
        dialog.getDialogPane().setGraphic(null);

        ButtonType okType = new ButtonType("Tạo", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(okType, cancelType);

        TextField nameField = new TextField();
        nameField.setPromptText("Nhập tên");
        nameField.setMaxWidth(Double.MAX_VALUE);

        TextArea descArea = new TextArea();
        descArea.setPromptText("Mô tả (tuỳ chọn)");
        descArea.setPrefRowCount(kind == EntityFormDialog.Kind.GROUP ? 3 : 2);
        descArea.setWrapText(true);
        descArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(descArea, Priority.NEVER);

        VBox form = new VBox(12);
        form.getStyleClass().add("entity-form");
        form.setPadding(new Insets(8, 4, 4, 4));
        form.setPrefWidth(DIALOG_WIDTH - 48);
        form.setMinWidth(DIALOG_WIDTH - 48);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        form.getChildren().add(fieldBlock("Tên", nameField));
        form.getChildren().add(fieldBlock("Mô tả", descArea));

        if (kind == EntityFormDialog.Kind.CASE) {
            ComboBox<String> statusCombo = new ComboBox<>();
            statusCombo.getItems().addAll("Draft", "Ready", "Inactive");
            statusCombo.setValue("Draft");
            statusCombo.setMaxWidth(Double.MAX_VALUE);

            TextField urlField = new TextField();
            urlField.setPromptText("https://...");
            urlField.setMaxWidth(Double.MAX_VALUE);

            CheckBox headlessCheck = new CheckBox("Ẩn trình duyệt");
            ComboBox<String> browserCombo = new ComboBox<>();
            browserCombo.getItems().addAll("Mặc định", "Chromium");
            browserCombo.setValue("Mặc định");
            browserCombo.setMaxWidth(Double.MAX_VALUE);

            form.getChildren().add(fieldBlock("Trạng thái", statusCombo));
            form.getChildren().add(fieldBlock("URL bắt đầu", urlField));
            form.getChildren().add(headlessCheck);
            form.getChildren().add(fieldBlock("Trình duyệt", browserCombo));
        }

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        form.getChildren().add(errorLabel);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);
        dialog.getDialogPane().setMinWidth(DIALOG_WIDTH);

        return dialog;
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
}
