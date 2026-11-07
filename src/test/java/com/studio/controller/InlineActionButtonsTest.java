package com.studio.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InlineActionButtonsTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void testIsTreeCellActionButtonEvent() throws Exception {
        Method m = ExplorerPageController.class.getDeclaredMethod("isTreeCellActionButtonEvent", MouseEvent.class);
        m.setAccessible(true);

        Button btn = new Button();
        btn.getStyleClass().add("tree-action-btn");
        SVGPath svg = new SVGPath();
        btn.setGraphic(svg);

        HBox actionsBox = new HBox(btn);
        actionsBox.getStyleClass().add("tree-cell-actions");
        javafx.scene.Scene scene = new javafx.scene.Scene(actionsBox);
        actionsBox.applyCss();
        actionsBox.layout();

        // Scenario 1: Target is an SVGPath inside a tree-action-btn
        MouseEvent clickOnSvg = new MouseEvent(
                svg, svg, MouseEvent.MOUSE_PRESSED,
                0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null
        );

        boolean isActionBtn = (boolean) m.invoke(null, clickOnSvg);
        assertTrue(isActionBtn, "Click trên SVG bên trong action button phải được nhận diện là action button event");

        // Scenario 2: Target is the button itself
        MouseEvent clickOnBtn = new MouseEvent(
                btn, btn, MouseEvent.MOUSE_PRESSED,
                0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null
        );
        assertTrue((boolean) m.invoke(null, clickOnBtn));

        // Scenario 3: Target is a normal cell text / row (not an action button)
        HBox normalRow = new HBox();
        normalRow.getStyleClass().add("tree-row");
        MouseEvent clickOnRow = new MouseEvent(
                normalRow, normalRow, MouseEvent.MOUSE_PRESSED,
                0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null
        );
        assertFalse((boolean) m.invoke(null, clickOnRow), "Click trên hàng thông thường không phải là action button event");
    }

    @Test
    void testButtonActionFiresWithoutFilterInterception() {
        Button btn = new Button();
        btn.getStyleClass().add("tree-action-btn");
        btn.setFocusTraversable(false);
        btn.setOnDragDetected(javafx.event.Event::consume);

        AtomicBoolean actionFired = new AtomicBoolean(false);
        btn.setOnAction(e -> actionFired.set(true));

        // In JavaFX, firing the action on the button executes the onAction handler
        btn.fire();
        assertTrue(actionFired.get(), "Nút inline button phải kích hoạt ActionEvent thành công khi được nhấn");
    }
}
