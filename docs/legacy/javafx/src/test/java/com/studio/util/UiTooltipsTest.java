package com.studio.util;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UiTooltipsTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void testCreateTooltipWithFastDelay() {
        Tooltip tip = UiTooltips.create("Sample tooltip");
        assertNotNull(tip);
        assertEquals("Sample tooltip", tip.getText());
        assertEquals(Duration.millis(150), tip.getShowDelay());
        assertEquals(Duration.seconds(10), tip.getShowDuration());
    }

    @Test
    void testCreateBlankOrNull() {
        assertNull(UiTooltips.create(null));
        assertNull(UiTooltips.create("   "));
    }

    @Test
    void testInstallAndClearOnButton() {
        Button btn = new Button("Test");
        Tooltip tip = UiTooltips.install(btn, "Action description");
        assertNotNull(tip);
        assertSame(tip, btn.getTooltip());
        assertEquals("Action description", btn.getTooltip().getText());

        UiTooltips.clear(btn);
        assertNull(btn.getTooltip());
    }

    @Test
    void testInstallOnLabel() {
        Label lbl = new Label("Label test");
        Tooltip tip = UiTooltips.install(lbl, "Help text");
        assertNotNull(tip);
        assertSame(tip, lbl.getTooltip());

        UiTooltips.install(lbl, null);
        assertNull(lbl.getTooltip());
    }
}

