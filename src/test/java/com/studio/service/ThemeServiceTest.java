package com.studio.service;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void testStylesheetAddedAndRemovedOnScene() {
        Scene scene = new Scene(new Pane());
        ThemeService.init(scene);

        ThemeService.setTheme(ThemeService.THEME_LIGHT);
        boolean hasLightCss = scene.getStylesheets().stream().anyMatch(s -> s.contains("studio-light.css"));
        assertTrue(hasLightCss, "Scene should contain studio-light.css when light theme active");

        ThemeService.setTheme(ThemeService.THEME_DARK);
        hasLightCss = scene.getStylesheets().stream().anyMatch(s -> s.contains("studio-light.css"));
        assertFalse(hasLightCss, "Scene should NOT contain studio-light.css when dark theme active");
    }

    private void runFx(Runnable r) throws InterruptedException {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS), "Timed out waiting for FX thread");
        if (error.get() != null) {
            if (error.get() instanceof AssertionError ae) throw ae;
            fail("FX task threw exception", error.get());
        }
    }

    @Test
    void testInitNotifiesListeners() throws Exception {
        runFx(() -> {
            AtomicReference<String> notifiedTheme = new AtomicReference<>();
            ThemeService.addListener(notifiedTheme::set);

            Scene scene = new Scene(new Pane());
            ThemeService.init(scene);

            assertNotNull(notifiedTheme.get(), "Init should notify listeners with current theme");
            assertEquals(ThemeService.getCurrentTheme(), notifiedTheme.get());
        });
    }

    @Test
    void testApplyThemeToDialogPane() throws Exception {
        runFx(() -> {
            Dialog<Void> dialog = new Dialog<>();
            DialogPane pane = dialog.getDialogPane();
            pane.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());

            ThemeService.setTheme(ThemeService.THEME_LIGHT);
            ThemeService.applyThemeToDialog(dialog);

            boolean hasLightCss = pane.getStylesheets().stream().anyMatch(s -> s.contains("studio-light.css"));
            assertTrue(hasLightCss, "Dialog pane stylesheets should contain studio-light.css in light mode");

            ThemeService.setTheme(ThemeService.THEME_DARK);
            ThemeService.applyThemeToDialog(dialog);

            hasLightCss = pane.getStylesheets().stream().anyMatch(s -> s.contains("studio-light.css"));
            assertFalse(hasLightCss, "Dialog pane stylesheets should not contain studio-light.css in dark mode");
        });
    }

    @Test
    void testStudioLightCssContainsAllCriticalSelectors() throws Exception {
        try (var is = getClass().getResourceAsStream("/css/studio-light.css")) {
            assertNotNull(is, "studio-light.css not found");
            String css = new String(is.readAllBytes());
            String[] requiredSelectors = {
                    ".project-tree",
                    ".tree-panel",
                    ".header-breadcrumb-box",
                    ".header-help-btn",
                    ".toolbar-btn",
                    ".toolbar-search",
                    ".filter-chip",
                    ".case-list",
                    ".reports-table",
                    ".report-detail-page",
                    ".workspace-tabs",
                    ".dialog-pane > .content",
                    ".ai-target-box",
                    ".ai-assistant-page"
            };
            for (String sel : requiredSelectors) {
                assertTrue(css.contains(sel), "studio-light.css must define " + sel + " to prevent dark theme leakage");
            }
        }
    }
}

