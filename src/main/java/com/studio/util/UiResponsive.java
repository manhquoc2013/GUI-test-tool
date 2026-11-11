package com.studio.util;

import javafx.beans.InvalidationListener;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/**
 * Breakpoint-driven responsive behaviour for the desktop shell and its pages.
 *
 * <p>JavaFX CSS has no {@code @media} support, so responsive layout is driven
 * from Java: a node registers a callback and it runs whenever the scene width
 * crosses a breakpoint boundary. The callback fires on crossing only, not on
 * every resize, so a layout mode is applied once per transition.
 *
 * <p>Breakpoints are defined once here so every screen agrees on the same
 * thresholds:
 * <ul>
 *   <li>{@link #COMPACT} 900px — narrow window: chrome must squeeze (labels
 *       shorten, sidebars collapse, secondary text drops).</li>
 *   <li>{@link #REGULAR} 1280px — the app's default design width; dense
 *       toolbars can show everything they have.</li>
 * </ul>
 */
public final class UiResponsive {

    /** Below this scene width the shell and pages enter compact mode. */
    public static final double COMPACT = 900;

    /** At or above this scene width toolbars may show their full detail. */
    public static final double REGULAR = 1280;

    public enum Mode {
        /** Narrow window — secondary chrome is dropped or abbreviated. */
        COMPACT,
        /** Normal window — everything fits with room to spare. */
        REGULAR
    }

    private UiResponsive() {
    }

    /** Resolves the mode for an arbitrary width. */
    public static Mode modeFor(double width) {
        return width < COMPACT ? Mode.COMPACT : Mode.REGULAR;
    }

    /**
     * Runs {@code onModeChange} whenever the scene width's mode differs from the
     * previously applied one. The callback also runs once on attach so the
     * initial state is always established.
     *
     * <p>The scene listener lives on the {@link Scene}, which is owned by the
     * window rather than the page; a page that is reopened re-installs onto the
     * same scene, so callers keep one installation per page instance.
     */
    public static void install(Region node, Consumer<Mode> onModeChange) {
        if (node == null || onModeChange == null) {
            return;
        }
        Mode[] applied = {null};
        InvalidationListener listener = obs -> {
            Scene scene = node.getScene();
            if (scene == null || scene.getWidth() <= 0) {
                return;
            }
            Mode next = modeFor(scene.getWidth());
            if (next != applied[0]) {
                applied[0] = next;
                onModeChange.accept(next);
            }
        };
        node.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(listener);
            }
            if (newScene != null) {
                newScene.widthProperty().addListener(listener);
                listener.invalidated(newScene.widthProperty());
            }
        });
        if (node.getScene() != null) {
            node.getScene().widthProperty().addListener(listener);
            listener.invalidated(node.getScene().widthProperty());
        }
    }
}
