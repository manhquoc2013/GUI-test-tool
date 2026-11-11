package com.studio.util;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeView;

/**
 * Slims every scroll bar in a scene to {@link #THICKNESS} px.
 *
 * <p>A stylesheet cannot do this. A {@code ScrollBar} inside a
 * {@code VirtualFlow} - the skin of every ListView, TableView and TreeView - is
 * sized by that container's layout pass, which writes the cross-axis size
 * directly and wins over CSS {@code -fx-pref-width}. Measured: the bar reports
 * {@code prefWidth=10} from CSS yet lays out at 20px. Setting the size on the
 * control after its skin exists is the route that holds.
 */
public final class CompactScrollBars {

    /** Bar thickness in px. Thin but still a usable drag target. */
    public static final double THICKNESS = 10;

    private CompactScrollBars() {
    }

    /**
     * Applies compact sizing to every scroll bar reachable from {@code root},
     * including bars that appear later (list skins build lazily as data loads).
     */
    public static void install(Node root) {
        if (root == null) {
            return;
        }
        applyTo(root);
        // List/table/tree skins are created on first layout; re-apply when the
        // skin arrives so lazily built bars are covered too.
        if (root instanceof ListView<?> lv) {
            lv.skinProperty().addListener((obs, o, n) -> applyTo(root));
        } else if (root instanceof TableView<?> tv) {
            tv.skinProperty().addListener((obs, o, n) -> applyTo(root));
        } else if (root instanceof TreeView<?> tv) {
            tv.skinProperty().addListener((obs, o, n) -> applyTo(root));
        }
    }

    /** Installs on a whole scene. */
    public static void install(Scene scene) {
        if (scene != null && scene.getRoot() != null) {
            install(scene.getRoot());
        }
    }

    private static void applyTo(Node root) {
        for (Node n : root.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar bar) {
                shrink(bar);
            }
        }
    }

    /** Pins one bar's cross-axis thickness. */
    public static void shrink(ScrollBar bar) {
        if (bar == null) {
            return;
        }
        if (bar.getOrientation() == Orientation.VERTICAL) {
            bar.setPrefWidth(THICKNESS);
            bar.setMinWidth(THICKNESS);
            bar.setMaxWidth(THICKNESS);
        } else {
            bar.setPrefHeight(THICKNESS);
            bar.setMinHeight(THICKNESS);
            bar.setMaxHeight(THICKNESS);
        }
        bar.setPadding(Insets.EMPTY);
    }
}
