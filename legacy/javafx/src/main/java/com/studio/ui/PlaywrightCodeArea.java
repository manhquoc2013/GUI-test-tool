package com.studio.ui;

import javafx.beans.property.StringProperty;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Lightweight native JavaFX Playwright code editor with synchronized line numbers.
 * Fully compatible with JavaFX 26+ and JDK 26 without external bytecode conflicts.
 *
 * <p>All geometry and colours come from {@code studio.css} ({@code .playwright-code-area},
 * {@code .code-gutter}, {@code .code-editor-inner}) so dark/light themes stay in sync.</p>
 */
public final class PlaywrightCodeArea extends HBox {

    private final TextArea lineGutter;
    private final TextArea codeEditor;

    public PlaywrightCodeArea() {
        setSpacing(0);
        getStyleClass().add("playwright-code-area");

        lineGutter = new TextArea("1\n");
        lineGutter.setEditable(false);
        lineGutter.setFocusTraversable(false);
        lineGutter.setWrapText(false);
        lineGutter.setMouseTransparent(true);
        lineGutter.getStyleClass().addAll("lineno-gutter", "edge-to-edge");

        codeEditor = new TextArea();
        codeEditor.setWrapText(false);
        codeEditor.getStyleClass().addAll("code-editor-inner", "edge-to-edge");
        HBox.setHgrow(codeEditor, Priority.ALWAYS);

        codeEditor.textProperty().addListener((obs, oldText, newText) -> updateLineNumbers(newText));

        // Keep the gutter aligned with the editor while scrolling.
        codeEditor.scrollTopProperty().addListener((obs, oldV, newV) ->
                lineGutter.setScrollTop(newV.doubleValue()));

        getChildren().addAll(lineGutter, codeEditor);
    }

    private void updateLineNumbers(String text) {
        int lineCount = countLines(text);
        StringBuilder sb = new StringBuilder(lineCount * 4);
        for (int i = 1; i <= lineCount; i++) {
            sb.append(i).append('\n');
        }
        lineGutter.setText(sb.toString());
    }

    /** Counts logical lines in O(n) without allocating a split array on every keystroke. */
    public static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    public String getText() {
        return codeEditor.getText();
    }

    public void replaceText(String text) {
        codeEditor.setText(text != null ? text : "");
    }

    /**
     * Replaces the whole buffer while keeping the caret and scroll position, so regenerating the
     * script from the step builder does not yank the user's viewport back to the top.
     */
    public void replaceTextPreservingView(String text) {
        String next = text != null ? text : "";
        int caret = codeEditor.getCaretPosition();
        double scrollTop = codeEditor.getScrollTop();
        codeEditor.setText(next);
        codeEditor.positionCaret(Math.min(caret, next.length()));
        codeEditor.setScrollTop(scrollTop);
    }

    public StringProperty textProperty() {
        return codeEditor.textProperty();
    }

    /** Guidance shown while the buffer is empty — it is a hint, never content that can be saved. */
    public void setPromptText(String prompt) {
        codeEditor.setPromptText(prompt);
    }
}
