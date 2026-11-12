package com.studio.util;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Utility for creating and installing fast, responsive Tooltips across the application.
 * Uses a crisp 150ms show delay (compared to JavaFX default 1000ms) and keeps it visible for 10s.
 */
public final class UiTooltips {

    public static final Duration SHOW_DELAY = Duration.millis(150);
    public static final Duration SHOW_DURATION = Duration.seconds(10);
    public static final Duration HIDE_DELAY = Duration.millis(100);

    private UiTooltips() {}

    /**
     * Creates a standard fast-responding Tooltip.
     */
    public static Tooltip create(String text) {
        if (text == null || text.isBlank()) return null;
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(SHOW_DELAY);
        tip.setShowDuration(SHOW_DURATION);
        tip.setHideDelay(HIDE_DELAY);
        return tip;
    }

    /**
     * Installs or updates a Tooltip on any JavaFX Node / Control.
     */
    public static Tooltip install(Node node, String text) {
        if (node == null) return null;
        if (text == null || text.isBlank()) {
            clear(node);
            return null;
        }
        Tooltip tip = create(text);
        Tooltip.install(node, tip);
        if (node instanceof Control control) {
            control.setTooltip(tip);
            // Icon-only controls carry no text of their own; the tooltip is the only name a
            // screen reader can announce, so mirror it into the accessible text.
            if (control.getAccessibleText() == null) {
                control.setAccessibleText(text);
            }
        } else if (node instanceof Label label) {
            label.setTooltip(tip);
            if (label.getAccessibleText() == null) {
                label.setAccessibleText(text);
            }
        }
        return tip;
    }

    /**
     * Clears any existing tooltip on a Node / Control.
     */
    public static void clear(Node node) {
        if (node == null) return;
        if (node instanceof Control control) {
            Tooltip existing = control.getTooltip();
            if (existing != null) {
                Tooltip.uninstall(control, existing);
            }
            control.setTooltip(null);
        } else if (node instanceof Label label) {
            Tooltip existing = label.getTooltip();
            if (existing != null) {
                Tooltip.uninstall(label, existing);
            }
            label.setTooltip(null);
        }
    }
}

