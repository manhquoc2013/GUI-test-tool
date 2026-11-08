package com.studio.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

/**
 * Native vector SVG icon library for JavaFX buttons and indicators.
 * Eliminates Unicode emoji font dependencies and tofu box ('[]') rendering
 * issues on Windows.
 * All paths are based on standard 24x24 viewBox coordinates.
 */
public final class UiIcons {

    private UiIcons() {
    }

    // ── Common Icon Path Definitions (24x24 standard coordinate system) ──

    /** Red circle for Recording */
    public static final String RECORD = "M 12 2 C 6.48 2 2 6.48 2 12 s 4.48 10 10 10 s 10 -4.48 10 -10 S 17.52 2 12 2 Z";

    /** Play triangle for Run Case / Run Action */
    public static final String PLAY = "M 8 5 v 14 l 11 -7 Z";

    /** Double play triangle for Run Suite / Run All */
    public static final String RUN_ALL = "M 4 5 v 14 l 8 -7 Z M 12 5 v 14 l 8 -7 Z";

    /** Save floppy disk */
    public static final String SAVE = "M 19 21 H 5 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 h 11 l 5 5 v 11 a 2 2 0 0 1 -2 2 Z M 7 3 v 5 h 8 V 3 Z M 7 21 h 10 v -8 H 7 Z";

    /** Excel / Spreadsheet */
    public static final String EXCEL = "M 14 2 H 6 c -1.1 0 -2 0.9 -2 2 v 16 c 0 1.1 0.9 2 2 2 h 12 c 1.1 0 2 -0.9 2 -2 V 8 l -6 -6 z M 13 9 V 3.5 L 18.5 9 H 13 z M 15.8 17.5 l -1.6 -2.5 l -1.6 2.5 h -1.8 l 2.5 -3.7 l -2.3 -3.5 h 1.8 l 1.4 2.3 l 1.4 -2.3 h 1.8 l -2.3 3.5 l 2.5 3.7 h -1.9 z";

    /** AI Sparkles */
    public static final String AI_SPARKLES = "M 12 2 L 14.2 7.8 L 20 10 L 14.2 12.2 L 12 18 L 9.8 12.2 L 4 10 L 9.8 7.8 Z M 19 2 L 20 5 L 23 6 L 20 7 L 19 10 L 18 7 L 15 6 L 18 5 Z M 5 18 L 6 20 L 8 21 L 6 22 L 5 24 L 4 22 L 2 21 L 4 20 Z";

    /** Split view / 2 columns */
    public static final String SPLIT = "M 3 4 h 18 v 16 H 3 Z M 11 6 H 5 v 12 h 6 Z M 19 6 h -6 v 12 h 6 Z";

    /** Full screen / expand */
    public static final String FULLSCREEN = "M 7 14 H 5 v 5 h 5 v -2 H 7 Z M 5 10 h 2 V 7 h 3 V 5 H 5 Z M 19 14 h -2 v 3 h -3 v 2 h 5 Z M 14 5 v 2 h 3 v 3 h 2 V 5 Z";

    /** Sun for Light Mode */
    public static final String SUN = "M 12 7 A 5 5 0 1 0 12 17 A 5 5 0 1 0 12 7 Z M 11 1 h 2 v 3 h -2 Z M 11 20 h 2 v 3 h -2 Z M 3.5 4.9 l 1.4 -1.4 l 2.1 2.1 l -1.4 1.4 Z M 16.9 18.3 l 1.4 -1.4 l 2.1 2.1 l -1.4 1.4 Z M 1 11 h 3 v 2 H 1 Z M 20 11 h 3 v 2 h -3 Z M 4.9 20.5 l -1.4 -1.4 l 2.1 -2.1 l 1.4 1.4 Z M 18.3 7.1 l -1.4 -1.4 l 2.1 -2.1 l 1.4 1.4 Z";

    /** Moon for Dark Mode */
    public static final String MOON = "M 12.3 2 a 10 10 0 0 0 -1.9 0.18 A 10 10 0 1 0 21.8 13.6 A 8.5 8.5 0 0 1 12.3 2 Z";

    /** Settings gear */
    public static final String SETTINGS = "M 19.4 13 c 0.04 -0.32 0.06 -0.66 0.06 -1 s -0.02 -0.68 -0.07 -1 l 2.1 -1.65 c 0.2 -0.15 0.24 -0.42 0.12 -0.64 l -2 -3.46 c -0.12 -0.22 -0.39 -0.3 -0.61 -0.22 l -2.5 1 c -0.52 -0.4 -1.08 -0.73 -1.7 -0.98 l -0.37 -2.65 A 0.5 0.5 0 0 0 13.9 2 h -4 c -0.25 0 -0.46 0.18 -0.49 0.42 l -0.38 2.65 c -0.61 0.25 -1.17 0.59 -1.69 0.98 l -2.5 -1 c -0.23 -0.09 -0.49 0 -0.61 0.22 l -2 3.46 c -0.13 0.22 -0.07 0.49 0.12 0.64 l 2.11 1.65 c -0.05 0.33 -0.07 0.66 -0.07 1 s 0.02 0.68 0.07 1 l -2.11 1.65 c -0.19 0.15 -0.25 0.42 -0.12 0.64 l 2 3.46 c 0.12 0.22 0.39 0.3 0.61 0.22 l 2.5 -1 c 0.52 0.4 1.08 0.74 1.69 0.99 l 0.38 2.65 c 0.03 0.24 0.24 0.42 0.49 0.42 h 4 c 0.25 0 0.45 -0.18 0.49 -0.42 l 0.38 -2.65 c 0.61 -0.25 1.17 -0.59 1.69 -0.99 l 2.5 1 c 0.23 0.09 0.49 0 0.61 -0.22 l 2 -3.46 c 0.12 -0.22 0.07 -0.49 -0.12 -0.64 Z M 12 15.5 A 3.5 3.5 0 1 1 12 8.5 A 3.5 3.5 0 0 1 12 15.5 Z";

    /** Help circle */
    public static final String HELP = "M 12 2 C 6.48 2 2 6.48 2 12 s 4.48 10 10 10 s 10 -4.48 10 -10 S 17.52 2 12 2 Z m 1 16 h -2 v -2 h 2 Z m 1.07 -7.75 l -0.9 0.92 C 12.45 11.9 12 12.5 12 14 h -2 v -0.5 c 0 -1.1 0.45 -2.1 1.17 -2.83 l 1.24 -1.26 c 0.37 -0.36 0.59 -0.86 0.59 -1.41 c 0 -1.1 -0.9 -2 -2 -2 s -2 0.9 -2 2 H 7 c 0 -2.76 2.24 -5 5 -5 s 5 2.24 5 5 c 0 1.04 -0.42 1.99 -1.1 2.68 Z";

    /** Plus / Add */
    public static final String PLUS = "M 19 13 h -6 v 6 h -2 v -6 H 5 v -2 h 6 V 5 h 2 v 6 h 6 Z";

    /** Trash / Delete */
    public static final String TRASH = "M 6 19 c 0 1.1 0.9 2 2 2 h 8 c 1.1 0 2 -0.9 2 -2 V 7 H 6 Z M 19 4 h -3.5 l -1 -1 h -5 l -1 1 H 5 v 2 h 14 Z";

    /** Pencil / Edit */
    public static final String EDIT = "M 3 17.25 V 21 h 3.75 L 17.81 9.94 l -3.75 -3.75 Z M 20.71 7.04 a 1 1 0 0 0 0 -1.41 l -2.34 -2.34 a 1 1 0 0 0 -1.41 0 l -1.83 1.83 l 3.75 3.75 Z";

    /** Copy */
    public static final String COPY = "M 16 1 H 4 c -1.1 0 -2 0.9 -2 2 v 14 h 2 V 3 h 12 Z m 3 4 H 8 c -1.1 0 -2 0.9 -2 2 v 14 c 0 1.1 0.9 2 2 2 h 11 c 1.1 0 2 -0.9 2 -2 V 7 c 0 -1.1 -0.9 -2 -2 -2 Z m 0 16 H 8 V 7 h 11 Z";

    /** Undo / Revert */
    public static final String UNDO = "M 12.5 8 c -2.65 0 -5.05 1 -6.9 2.6 L 2 7 v 9 h 9 l -3.62 -3.62 c 1.39 -1.16 3.16 -1.88 5.12 -1.88 c 3.54 0 6.55 2.31 7.6 5.5 l 2.37 -0.78 C 21.08 11.03 17.15 8 12.5 8 Z";

    /** Search */
    public static final String SEARCH = "M 15.5 14 h -0.79 l -0.28 -0.27 A 6.47 6.47 0 0 0 16 9.5 A 6.5 6.5 0 1 0 9.5 16 c 1.61 0 3.09 -0.59 4.23 -1.57 l 0.27 0.28 v 0.79 l 5 4.99 L 20.49 19 Z m -6 0 C 7.01 14 5 11.99 5 9.5 S 7.01 5 9.5 5 S 14 7.01 14 9.5 S 11.99 14 9.5 14 Z";

    /** Refresh */
    public static final String REFRESH = "M 17.65 6.35 A 7.95 7.95 0 0 0 12 4 c -4.42 0 -7.99 3.58 -7.99 8 s 3.57 8 7.99 8 c 3.73 0 6.84 -2.55 7.73 -6 h -2.08 A 5.99 5.99 0 0 1 12 18 c -3.31 0 -6 -2.69 -6 -6 s 2.69 -6 6 -6 c 1.66 0 3.14 0.69 4.22 1.78 L 13 11 h 7 V 4 Z";

    /** Close X */
    public static final String CLOSE = "M 19 6.41 L 17.59 5 L 12 10.59 L 6.41 5 L 5 6.41 L 10.59 12 L 5 17.59 L 6.41 19 L 12 13.41 L 17.59 19 L 19 17.59 L 13.41 12 Z";

    /** Checkmark */
    public static final String CHECK = "M 9 16.17 L 4.83 12 l -1.42 1.41 L 9 19 L 21 7 l -1.41 -1.41 Z";

    /** Terminal prompt */
    public static final String TERMINAL = "M 20 4 H 4 c -1.1 0 -2 0.9 -2 2 v 12 c 0 1.1 0.9 2 2 2 h 16 c 1.1 0 2 -0.9 2 -2 V 6 c 0 -1.1 -0.9 -2 -2 -2 Z m 0 14 H 4 V 8 h 16 Z M 6 10 l 4 3 l -4 3 v -2 l 1.5 -1 l -1.5 -1 Z m 6 5 h 6 v 2 h -6 Z";

    /** Web Globe / URL */
    public static final String GLOBE = "M 12 2 C 6.48 2 2 6.48 2 12 s 4.48 10 10 10 s 10 -4.48 10 -10 S 17.52 2 12 2 Z m -1 17.93 c -3.95 -0.49 -7 -3.85 -7 -7.93 c 0 -0.62 0.08 -1.21 0.21 -1.79 L 9 15 v 1 c 0 1.1 0.9 2 2 2 Z m 6.9 -2.54 c -0.26 -0.81 -1 -1.39 -1.9 -1.39 h -1 v -3 c 0 -0.55 -0.45 -1 -1 -1 H 8 v -2 h 2 c 0.55 0 1 -0.45 1 -1 V 7 h 2 c 1.1 0 2 -0.9 2 -2 v -0.41 c 2.93 1.19 5 4.06 5 7.41 c 0 2.08 -0.8 3.97 -2.1 5.39 Z";

    /** Eye (Visible) */
    public static final String EYE = "M 12 4.5 C 7 4.5 2.73 7.61 1 12 c 1.73 4.39 6 7.5 11 7.5 s 9.27 -3.11 11 -7.5 c -1.73 -4.39 -6 -7.5 -11 -7.5 Z m 0 12.5 c -2.76 0 -5 -2.24 -5 -5 s 2.24 -5 5 -5 s 5 2.24 5 5 s -2.24 5 -5 5 Z m 0 -8 c -1.66 0 -3 1.34 -3 3 s 1.34 3 3 3 s 3 -1.34 3 -3 s -1.34 -3 -3 -3 Z";

    /** Eye Off (Hidden) */
    public static final String EYE_OFF = "M 12 7 c 2.76 0 5 2.24 5 5 c 0 0.65 -0.13 1.26 -0.36 1.83 l 2.92 2.92 c 1.51 -1.26 2.7 -2.89 3.44 -4.75 c -1.73 -4.39 -6 -7.5 -11 -7.5 c -1.4 0 -2.74 0.25 -3.98 0.7 l 2.16 2.16 C 10.74 7.13 11.35 7 12 7 Z M 2 4.27 l 2.28 2.28 l 0.46 0.46 A 11.8 11.8 0 0 0 1 12 c 1.73 4.39 6 7.5 11 7.5 c 1.55 0 3.03 -0.3 4.38 -0.84 l 0.42 0.42 L 19.73 22 L 21 20.73 L 3.27 3 Z M 7.53 9.8 l 1.55 1.55 c -0.05 0.21 -0.08 0.43 -0.08 0.65 c 0 1.66 1.34 3 3 3 c 0.22 0 0.44 -0.03 0.65 -0.08 l 1.55 1.55 c -0.67 0.33 -1.41 0.53 -2.2 0.53 c -2.76 0 -5 -2.24 -5 -5 c 0 -0.79 0.2 -1.53 0.53 -2.2 Z";

    /** Back arrow */
    public static final String BACK = "M 20 11 H 7.83 l 5.59 -5.59 L 12 4 l -8 8 l 8 8 l 1.41 -1.41 L 7.83 13 H 20 Z";

    /** Paperclip / Attachment */
    public static final String CLIP = "M 16.5 6 v 11.5 c 0 2.21 -1.79 4 -4 4 s -4 -1.79 -4 -4 V 5 a 2.5 2.5 0 0 1 5 0 v 10.5 c 0 0.55 -0.45 1 -1 1 s -1 -0.45 -1 -1 V 6 H 10 v 9.5 a 2.5 2.5 0 0 0 5 0 V 5 c 0 -2.21 -1.79 -4 -4 -4 S 7 2.79 7 5 v 12.5 c 0 3.04 2.46 5.5 5.5 5.5 s 5.5 -2.46 5.5 -5.5 V 6 Z";

    /** Hamburger Menu */
    public static final String MENU = "M 3 18 h 18 v -2 H 3 Z m 0 -5 h 18 v -2 H 3 Z m 0 -7 v 2 h 18 V 6 Z";

    /** Folder */
    public static final String FOLDER = "M 10 4 H 4 c -1.1 0 -1.99 0.9 -1.99 2 L 2 18 c 0 1.1 0.9 2 2 2 h 16 c 1.1 0 2 -0.9 2 -2 V 8 c 0 -1.1 -0.9 -2 -2 -2 h -8 l -2 -2 Z";

    /** Document / File */
    public static final String FILE = "M 14 2 H 6 c -1.1 0 -1.99 0.9 -1.99 2 L 4 20 c 0 1.1 0.89 2 1.99 2 H 18 c 1.1 0 2 -0.9 2 -2 V 8 Z m 2 16 H 8 v -2 h 8 Z m 0 -4 H 8 v -2 h 8 Z m -3 -5 V 3.5 L 18.5 9 Z";

    // ── Factory Methods ──

    /**
     * Create a scalable vector graphic node.
     * 
     * @param pathData SVG path coordinates in 24x24 viewBox
     * @param size     display size in pixels (e.g. 14, 16)
     * @param fill     fill paint, or null to inherit CSS text-fill
     * @return a Node suitable for Button.setGraphic()
     */
    public static Node icon(String pathData, double size, Paint fill) {
        SVGPath path = new SVGPath();
        path.setContent(pathData);
        if (fill != null) {
            path.setFill(fill);
        } else {
            path.getStyleClass().add("ui-svg-icon");
        }

        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);

        StackPane container = new StackPane(path);
        container.setMinSize(size, size);
        container.setPrefSize(size, size);
        container.setMaxSize(size, size);
        container.setAlignment(Pos.CENTER);
        return container;
    }

    /**
     * Create an icon that uses CSS styling for its fill color.
     */
    public static Node icon(String pathData, double size) {
        return icon(pathData, size, (Paint) null);
    }

    /**
     * Create an icon with a specific hex color (e.g. "#f7768e").
     */
    public static Node icon(String pathData, double size, String hexColor) {
        return icon(pathData, size, hexColor != null ? Color.web(hexColor) : null);
    }

    /**
     * Set an SVG graphic directly on a button or labeled control.
     */
    public static void setGraphic(Labeled control, String pathData, double size, String hexColor) {
        if (control == null)
            return;
        control.setGraphic(icon(pathData, size, hexColor));
    }

    public static void setGraphic(Labeled control, String pathData, double size) {
        setGraphic(control, pathData, size, null);
    }
}
