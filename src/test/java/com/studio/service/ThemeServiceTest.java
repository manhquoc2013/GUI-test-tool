package com.studio.service;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeServiceTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void testThemeToggle() {
        Scene scene = new Scene(new Pane());
        ThemeService.init(scene);

        boolean initialDark = ThemeService.isDark();
        String toggled = ThemeService.toggleTheme();

        assertNotEquals(initialDark ? ThemeService.THEME_DARK : ThemeService.THEME_LIGHT, toggled);
        assertEquals(!initialDark, ThemeService.isDark());

        // Toggle back
        ThemeService.toggleTheme();
        assertEquals(initialDark, ThemeService.isDark());
    }

    @Test
    void testSetThemeExplicit() {
        Scene scene = new Scene(new Pane());
        ThemeService.init(scene);

        ThemeService.setTheme(ThemeService.THEME_LIGHT);
        assertFalse(ThemeService.isDark());
        assertEquals(ThemeService.THEME_LIGHT, ThemeService.getCurrentTheme());

        ThemeService.setTheme(ThemeService.THEME_DARK);
        assertTrue(ThemeService.isDark());
        assertEquals(ThemeService.THEME_DARK, ThemeService.getCurrentTheme());
    }
}

