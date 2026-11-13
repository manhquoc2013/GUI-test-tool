package com.studio.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Applies studio dark stylesheet, modality, click-outside, and enter/exit motion. */
public final class DialogTheme {

    private static final String SCRIM_INSTALLED = "studio.dialogScrim";
    private static final String DIALOG_SHELL = "studio.dialogShell";
    private static final String SCRIM_NODE = "studio.dialogScrimNode";
    public static final String SHELL_NODE = "studio.dialogShellNode";
    /** Close may proceed without theme exit animation (custom handler already animated / confirmed). */
    public static final String EXIT_ARMED = "studio.dialogExitArmed";
    private static final String CLOSE_FILTER = "studio.dialogCloseFilter";
    private static final String SKIP_ANIM_ONCE = "studio.dialogSkipAnimOnce";
    private static final String ENTER_DONE = "studio.dialogEnterDone";

    private DialogTheme() {}

    /**
     * Bounds a dialog's content to the screen so nothing is cut off on a short
     * display: wraps it in a ScrollPane with a max height derived from the
     * screen's visual bounds, and makes the dialog resizable so the user can
     * widen or maximise it. Without this a tall form simply clipped its lower
     * controls with no way to reach them.
     */
    public static void fitToScreen(Dialog<?> dialog, double prefWidth) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }
        DialogPane pane = dialog.getDialogPane();
        Node content = pane.getContent();
        if (content == null) {
            return;
        }
        javafx.geometry.Rectangle2D screen =
                javafx.stage.Screen.getPrimary().getVisualBounds();
        // Leave room for the title bar, header text and the button bar.
        double maxContentHeight = Math.max(220, screen.getHeight() - 260);

        ScrollPane scroller = new ScrollPane(content);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setMaxHeight(maxContentHeight);
        scroller.getStyleClass().add("dialog-scroll");
        // Transparent chrome so the dialog surface shows through, matching the
        // panels behind it.
        scroller.setStyle("-fx-background-color: transparent;");

        pane.setContent(scroller);
        pane.setPrefWidth(prefWidth);
        dialog.setResizable(true);
    }

    public static void apply(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        String css = DialogTheme.class.getResource("/css/studio.css").toExternalForm();
        if (!pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
        com.studio.service.ThemeService.applyThemeToDialog(dialog);
        pane.getStyleClass().add("studio-dialog");
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                com.studio.service.ThemeService.applyThemeToScene(newScene);
                com.studio.service.ThemeService.applyThemeToDialog(dialog);
            }
        });
        dialog.setGraphic(null);
        pane.setGraphic(null);

        try {
            dialog.initModality(Modality.APPLICATION_MODAL);
        } catch (IllegalStateException ignored) {
        }
        Window owner = findOwnerWindow();
        if (owner != null) {
            try {
                dialog.initOwner(owner);
            } catch (IllegalStateException ignored) {
            }
        }
        try {
            dialog.initStyle(StageStyle.TRANSPARENT);
        } catch (IllegalStateException ignored) {
        }

        // Hide before first paint so deferred scrim install + enter don't flash
        pane.setOpacity(0);
        pane.getProperties().remove(ENTER_DONE);
        prepareTransparentStage(dialog);

        dialog.setOnShowing(e -> {
            prepareTransparentStage(dialog);
            com.studio.service.ThemeService.applyThemeToDialog(dialog);
        });

        // IMPORTANT: Dialog.DIALOG_SHOWN fires BEFORE HeavyweightDialog.showAndWait()
        // calls scene.setRoot(dialogPane). Installing scrim synchronously here parents
        // the pane and crashes setRoot. Defer to the next pulse (inside nested loop).
        dialog.setOnShown(e -> Platform.runLater(() -> {
            if (!dialog.isShowing()) {
                return;
            }
            dialog.setGraphic(null);
            pane.setGraphic(null);
            styleButtons(pane);
            // Keep stage invisible until scrim replaces the default white scene root
            prepareTransparentStage(dialog);
            installOutsideClickClose(dialog);
            Node shell = asNode(pane.getProperties().get(SHELL_NODE));
            Node scrim = asNode(pane.getProperties().get(SCRIM_NODE));
            if (shell != null) {
                pane.setOpacity(1);
                shell.setOpacity(0);
                shell.setScaleX(0.96);
                shell.setScaleY(0.96);
                shell.setTranslateY(12);
            } else {
                pane.setOpacity(0);
            }
            if (scrim != null) {
                scrim.setOpacity(0);
            }
            installAnimatedClose(dialog);
            installHiddenCleanup(dialog);
            playEnterOnce(dialog);
            // Reveal only after transparent fill + scrim + enter start values are set
            Window window = pane.getScene() != null ? pane.getScene().getWindow() : null;
            if (window instanceof Stage stage) {
                stage.setOpacity(1);
            }
        }));
    }

    /**
     * Scene defaults to white fill; HeavyweightDialog also centerOnScreen() to dialog
     * size before show — both cause a white flash. Force transparent + owner-sized +
     * invisible until scrim/enter are ready.
     */
    private static void prepareTransparentStage(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        Scene scene = pane.getScene();
        if (scene == null) {
            return;
        }
        com.studio.service.ThemeService.applyThemeToScene(scene);
        com.studio.service.ThemeService.applyThemeToDialog(dialog);
        scene.setFill(Color.TRANSPARENT);
        if (!(scene.getWindow() instanceof Stage stage)) {
            return;
        }
        stage.setOpacity(0);
        sizeStageToOwner(stage, dialog.getOwner());

        if (!Boolean.TRUE.equals(pane.getProperties().get("studio.stageShowHook"))) {
            pane.getProperties().put("studio.stageShowHook", Boolean.TRUE);
            // Runs after centerOnScreen(), before first visible frame
            stage.addEventFilter(WindowEvent.WINDOW_SHOWING, e -> {
                scene.setFill(Color.TRANSPARENT);
                stage.setOpacity(0);
                sizeStageToOwner(stage, dialog.getOwner());
            });
        }
    }

    private static void playEnterOnce(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (Boolean.TRUE.equals(pane.getProperties().get(ENTER_DONE))) {
            return;
        }
        pane.getProperties().put(ENTER_DONE, Boolean.TRUE);
        playEnter(dialog);
    }

    /** Mark dialog so the next close skips theme animation (caller animates or already confirmed). */
    public static void armExit(Dialog<?> dialog) {
        if (dialog != null) {
            dialog.getDialogPane().getProperties().put(EXIT_ARMED, Boolean.TRUE);
        }
    }

    /** Run exit motion then {@code after}. Interrupts enter animation if still running. */
    public static void animateExit(Dialog<?> dialog, Runnable after) {
        if (dialog == null) {
            if (after != null) after.run();
            return;
        }
        DialogPane pane = dialog.getDialogPane();
        Node scrim = asNode(pane.getProperties().get(SCRIM_NODE));
        Node shell = asNode(pane.getProperties().get(SHELL_NODE));
        Node content = shell != null ? shell : pane;
        UiMotion.clearBusy(scrim);
        UiMotion.clearBusy(content);
        UiMotion.playDialogExit(scrim, content, after);
    }

    private static void installOutsideClickClose(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        Scene scene = pane.getScene();
        if (scene == null) return;

        if (Boolean.TRUE.equals(pane.getProperties().get(SCRIM_INSTALLED))) {
            syncStageToOwner(dialog);
            return;
        }
        // Pane must not already have a parent or scene.setRoot / Group will fail
        detachPaneFromShell(pane);
        pane.getProperties().put(SCRIM_INSTALLED, Boolean.TRUE);

        Group shell = new Group(pane);
        shell.getProperties().put(DIALOG_SHELL, Boolean.TRUE);

        StackPane scrim = new StackPane(shell);
        scrim.getStyleClass().add("dialog-scrim");
        String css = DialogTheme.class.getResource("/css/studio.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
        StackPane.setAlignment(shell, Pos.CENTER);
        scene.setRoot(scrim);
        scene.setFill(Color.TRANSPARENT);
        com.studio.service.ThemeService.applyThemeToScene(scene);
        com.studio.service.ThemeService.applyThemeToDialog(dialog);

        pane.maxWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(300.0, scrim.getWidth() - 40.0), scrim.widthProperty()));
        pane.maxHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(240.0, scrim.getHeight() - 40.0), scrim.heightProperty()));

        pane.applyCss();
        pane.layout();
        pane.autosize();

        if (!Boolean.TRUE.equals(pane.getProperties().get("studio.dialogAutosizeHook"))) {
            pane.getProperties().put("studio.dialogAutosizeHook", Boolean.TRUE);
            pane.needsLayoutProperty().addListener((obs, was, is) -> {
                if (is) {
                    Platform.runLater(() -> {
                        if (pane.getParent() instanceof Group) {
                            pane.autosize();
                        }
                    });
                }
            });
        }

        pane.getProperties().put(SCRIM_NODE, scrim);
        pane.getProperties().put(SHELL_NODE, shell);

        scrim.addEventHandler(MouseEvent.MOUSE_CLICKED, ev -> {
            if (ev.getTarget() != scrim) return;
            ev.consume();
            dismissFromOutside(dialog);
        });

        syncStageToOwner(dialog);
        Window owner = dialog.getOwner();
        if (owner != null && !Boolean.TRUE.equals(pane.getProperties().get("studio.dialogOwnerSync"))) {
            pane.getProperties().put("studio.dialogOwnerSync", Boolean.TRUE);
            Runnable sync = () -> {
                if (dialog.isShowing()) {
                    syncStageToOwner(dialog);
                }
            };
            owner.xProperty().addListener((o, a, b) -> sync.run());
            owner.yProperty().addListener((o, a, b) -> sync.run());
            owner.widthProperty().addListener((o, a, b) -> sync.run());
            owner.heightProperty().addListener((o, a, b) -> sync.run());
        }
    }

    /**
     * After close, HeavyweightDialog sets scene root to a dummy node but leaves DialogPane
     * parented under our Group — next showAndWait then fails setRoot. Detach on hide.
     */
    private static void installHiddenCleanup(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (Boolean.TRUE.equals(pane.getProperties().get("studio.dialogHiddenCleanup"))) {
            return;
        }
        if (pane.getScene() == null || pane.getScene().getWindow() == null) {
            return;
        }
        pane.getProperties().put("studio.dialogHiddenCleanup", Boolean.TRUE);
        Window window = pane.getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> {
            detachPaneFromShell(pane);
            pane.getProperties().remove(SCRIM_INSTALLED);
            pane.getProperties().remove(ENTER_DONE);
            // Keep CLOSE_FILTER — filters stay on dialog/window for reuse; do not re-register
            pane.getProperties().remove(EXIT_ARMED);
            pane.getProperties().remove(SKIP_ANIM_ONCE);
            pane.setOpacity(1);
            if (window instanceof Stage stage) {
                stage.setOpacity(1);
            }
        });
    }

    private static void detachPaneFromShell(DialogPane pane) {
        pane.maxWidthProperty().unbind();
        pane.maxHeightProperty().unbind();
        pane.setMaxWidth(Region.USE_PREF_SIZE);
        pane.setMaxHeight(Region.USE_PREF_SIZE);
        pane.getProperties().remove(SCRIM_NODE);
        pane.getProperties().remove(SHELL_NODE);
        if (pane.getParent() instanceof Group group) {
            group.getChildren().remove(pane);
        }
    }

    private static void installAnimatedClose(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (Boolean.TRUE.equals(pane.getProperties().get(CLOSE_FILTER))) {
            return;
        }
        pane.getProperties().put(CLOSE_FILTER, Boolean.TRUE);

        // Button OK/Hủy → Dialog.close() → DIALOG_CLOSE_REQUEST (not WINDOW_CLOSE_REQUEST)
        dialog.addEventFilter(DialogEvent.DIALOG_CLOSE_REQUEST, e -> {
            if (Boolean.TRUE.equals(pane.getProperties().get(EXIT_ARMED))) {
                return;
            }
            if (Boolean.TRUE.equals(pane.getProperties().remove(SKIP_ANIM_ONCE))) {
                return;
            }
            e.consume();
            animateExit(dialog, () -> {
                pane.getProperties().put(EXIT_ARMED, Boolean.TRUE);
                dialog.close();
            });
        });

        // Window chrome / Esc may hit the stage first
        if (pane.getScene() != null && pane.getScene().getWindow() != null) {
            Window window = pane.getScene().getWindow();
            window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
                if (Boolean.TRUE.equals(pane.getProperties().get(EXIT_ARMED))) {
                    return;
                }
                if (Boolean.TRUE.equals(pane.getProperties().remove(SKIP_ANIM_ONCE))) {
                    return;
                }
                e.consume();
                animateExit(dialog, () -> {
                    pane.getProperties().put(EXIT_ARMED, Boolean.TRUE);
                    dialog.close();
                });
            });
        }
    }

    private static void playEnter(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        Node scrim = asNode(pane.getProperties().get(SCRIM_NODE));
        Node shell = asNode(pane.getProperties().get(SHELL_NODE));
        Node content = shell != null ? shell : pane;
        UiMotion.playDialogEnter(scrim, content, null);
    }

    private static void syncStageToOwner(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (pane.getScene() == null) return;
        Window window = pane.getScene().getWindow();
        if (!(window instanceof Stage stage)) return;
        sizeStageToOwner(stage, dialog.getOwner());
    }

    private static void sizeStageToOwner(Stage stage, Window owner) {
        if (owner != null && owner.isShowing()) {
            stage.setX(owner.getX());
            stage.setY(owner.getY());
            stage.setWidth(owner.getWidth());
            stage.setHeight(owner.getHeight());
            return;
        }
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    /**
     * Prefer Cancel button (lets dirty handlers run). Otherwise close with exit motion.
     */
    private static void dismissFromOutside(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        for (ButtonType type : pane.getButtonTypes()) {
            if (type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                Button cancel = (Button) pane.lookupButton(type);
                if (cancel != null) {
                    cancel.fire();
                    return;
                }
            }
        }
        // Let custom WINDOW_CLOSE_REQUEST handlers (e.g. dirty confirm) run first
        if (pane.getScene() != null && pane.getScene().getWindow() != null) {
            pane.getProperties().put(SKIP_ANIM_ONCE, Boolean.TRUE);
            Window window = pane.getScene().getWindow();
            WindowEvent closeRequest = new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST);
            window.fireEvent(closeRequest);
            if (closeRequest.isConsumed() || !dialog.isShowing()) {
                return;
            }
        }
        animateExit(dialog, () -> {
            pane.getProperties().put(EXIT_ARMED, Boolean.TRUE);
            dialog.close();
        });
    }

    private static Node asNode(Object value) {
        return value instanceof Node n ? n : null;
    }

    private static Window findOwnerWindow() {
        Window focused = null;
        for (Window w : Window.getWindows()) {
            if (!w.isShowing()) continue;
            if (w instanceof Stage && w.getScene() != null) {
                if (w.isFocused()) {
                    return w;
                }
                if (focused == null) {
                    focused = w;
                }
            }
        }
        return focused;
    }

    private static void styleButtons(DialogPane pane) {
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        if (ok != null) {
            String currentText = ok.getText();
            if (currentText == null || currentText.isBlank() || "OK".equalsIgnoreCase(currentText.trim())) {
                ok.setText("OK");
            }
            UiIcons.setGraphic(ok, UiIcons.CHECK, 13);
            if (!ok.getStyleClass().contains("btn-primary")) {
                ok.getStyleClass().add("btn-primary");
            }
            ButtonBar.setButtonData(ok, ButtonBar.ButtonData.OK_DONE);
            ok.setMinWidth(Region.USE_PREF_SIZE);
            ButtonBar.setButtonUniformSize(ok, false);
        }
        Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
        if (cancel != null) {
            cancel.setText("Hủy");
            cancel.getStyleClass().add("btn-secondary");
            cancel.setMinWidth(Region.USE_PREF_SIZE);
            ButtonBar.setButtonUniformSize(cancel, false);
        }
        Button apply = (Button) pane.lookupButton(ButtonType.APPLY);
        if (apply != null) {
            apply.setText("Áp dụng");
            UiIcons.setGraphic(apply, UiIcons.CHECK, 13);
            apply.setMinWidth(Region.USE_PREF_SIZE);
            ButtonBar.setButtonUniformSize(apply, false);
        }
        Button yes = (Button) pane.lookupButton(ButtonType.YES);
        if (yes != null) {
            yes.setText("Có");
            UiIcons.setGraphic(yes, UiIcons.CHECK, 13);
            yes.getStyleClass().add("btn-primary");
            yes.setMinWidth(Region.USE_PREF_SIZE);
            ButtonBar.setButtonUniformSize(yes, false);
        }
        Button no = (Button) pane.lookupButton(ButtonType.NO);
        if (no != null) {
            no.setText("Không");
            no.getStyleClass().add("btn-secondary");
            no.setMinWidth(Region.USE_PREF_SIZE);
            ButtonBar.setButtonUniformSize(no, false);
        }
        Button close = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (close != null) {
            close.setText("Đóng");
            close.setMinWidth(80);
            UiIcons.setGraphic(close, UiIcons.CLOSE, 12);
            close.getStyleClass().add("btn-secondary");
            ButtonBar.setButtonUniformSize(close, false);
        }
    }
}
