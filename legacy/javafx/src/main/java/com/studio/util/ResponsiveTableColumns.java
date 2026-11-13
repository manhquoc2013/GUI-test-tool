package com.studio.util;

import javafx.application.Platform;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Keeps TableView columns filling the available width without horizontal overflow.
 * Fixed columns keep a constant width; flex columns share the remainder by weight.
 */
public final class ResponsiveTableColumns {

    public record Spec(TableColumn<?, ?> column, double weight, double minWidth, Double fixedWidth) {
        public static Spec fixed(TableColumn<?, ?> column, double width) {
            return new Spec(column, 0, width, width);
        }

        public static Spec flex(TableColumn<?, ?> column, double weight, double minWidth) {
            return new Spec(column, weight, minWidth, null);
        }
    }

    private ResponsiveTableColumns() {}

    public static void install(TableView<?> table, Spec... specs) {
        if (table == null || specs == null || specs.length == 0) {
            return;
        }
        // Consume default resize — we own widths via the width listener.
        table.setColumnResizePolicy(resize -> true);
        Runnable apply = () -> layout(table, specs);
        table.widthProperty().addListener((obs, oldW, newW) -> apply.run());
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(apply));
        Platform.runLater(apply);
    }

    private static void layout(TableView<?> table, Spec[] specs) {
        double tableWidth = table.getWidth();
        if (tableWidth <= 0) {
            return;
        }
        // Leave a small gutter so the vertical scrollbar never forces overflow.
        double available = Math.max(0, tableWidth - 14);

        double fixedSum = 0;
        double weightSum = 0;
        double flexMinSum = 0;
        for (Spec spec : specs) {
            if (spec.fixedWidth() != null) {
                fixedSum += spec.fixedWidth();
            } else {
                weightSum += Math.max(0.01, spec.weight());
                flexMinSum += Math.max(0, spec.minWidth());
            }
        }

        double flexPool = Math.max(0, available - fixedSum);
        double scale = 1.0;
        if (flexMinSum > flexPool && flexMinSum > 0) {
            scale = flexPool / flexMinSum;
        }

        for (Spec spec : specs) {
            TableColumn<?, ?> column = spec.column();
            if (spec.fixedWidth() != null) {
                double width = spec.fixedWidth();
                column.setMinWidth(width);
                column.setPrefWidth(width);
                column.setMaxWidth(width);
                column.setResizable(false);
            } else {
                double ideal = flexPool * (Math.max(0.01, spec.weight()) / weightSum);
                double width = Math.max(spec.minWidth() * scale, ideal);
                column.setMinWidth(Math.max(40, spec.minWidth() * scale));
                column.setPrefWidth(width);
                column.setMaxWidth(Double.MAX_VALUE);
                column.setResizable(true);
            }
        }
    }
}
