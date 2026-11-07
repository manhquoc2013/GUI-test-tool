package com.studio.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UiIconsTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already started
        }
    }

    @Test
    void testIconCreation() {
        Node icon = UiIcons.icon(UiIcons.RECORD, 16);
        assertNotNull(icon);

        Node coloredIcon = UiIcons.icon(UiIcons.PLAY, 14, "#9ece6a");
        assertNotNull(coloredIcon);
    }

    @Test
    void testSetGraphicOnButton() {
        Button btn = new Button("Ghi hình");
        UiIcons.setGraphic(btn, UiIcons.RECORD, 14, "#f7768e");
        assertNotNull(btn.getGraphic());
    }

    @Test
    void testConstantPathsNotNullOrEmpty() {
        assertNotNull(UiIcons.RECORD);
        assertFalse(UiIcons.RECORD.isBlank());
        assertNotNull(UiIcons.PLAY);
        assertNotNull(UiIcons.SAVE);
        assertNotNull(UiIcons.AI_SPARKLES);
        assertNotNull(UiIcons.SUN);
        assertNotNull(UiIcons.MOON);
        assertNotNull(UiIcons.SETTINGS);
        assertNotNull(UiIcons.MENU);
        assertNotNull(UiIcons.FOLDER);
        assertNotNull(UiIcons.FILE);
        assertNotNull(UiIcons.CLIP);
    }
}
