package com.studio.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TabManagerTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already initialized
        }
    }

    private void runFx(Runnable r) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread timed out");
    }

    @Test
    void testOpenSelectAndCloseTab() throws InterruptedException {
        runFx(() -> {
            HBox tabContainer = new HBox();
            StackPane contentArea = new StackPane();
            TabManager manager = new TabManager(tabContainer, contentArea);

            Label content1 = new Label("Content 1");
            Label content2 = new Label("Content 2");

            AtomicReference<String> selectedTab = new AtomicReference<>();
            manager.setOnTabSelectedListener(tab -> selectedTab.set(tab.getId()));

            manager.openTab("tab-1", "Tab 1", "📄", content1, true, null, null);
            assertTrue(manager.hasTab("tab-1"));
            assertEquals("tab-1", manager.getActiveTabId());
            assertEquals("tab-1", selectedTab.get());
            assertEquals(1, tabContainer.getChildren().size());
            assertEquals(content1, contentArea.getChildren().get(0));

            manager.openTab("tab-2", "Tab 2", "📊", content2, true, null, null);
            assertTrue(manager.hasTab("tab-2"));
            assertEquals("tab-2", manager.getActiveTabId());
            assertEquals("tab-2", selectedTab.get());
            assertEquals(2, tabContainer.getChildren().size());
            assertEquals(content2, contentArea.getChildren().get(0));

            // Select tab-1 again
            manager.selectTab("tab-1");
            assertEquals("tab-1", manager.getActiveTabId());
            assertEquals("tab-1", selectedTab.get());

            // Set dirty
            manager.setDirty("tab-1", true);
            assertTrue(manager.getTab("tab-1").isDirty());

            // Close tab-1
            manager.closeTab("tab-1");
            assertFalse(manager.hasTab("tab-1"));
            assertEquals("tab-2", manager.getActiveTabId());
            assertEquals(1, tabContainer.getChildren().size());
        });
    }

    @Test
    void testCloseCallback() throws InterruptedException {
        runFx(() -> {
            HBox tabContainer = new HBox();
            StackPane contentArea = new StackPane();
            TabManager manager = new TabManager(tabContainer, contentArea);

            AtomicBoolean closed = new AtomicBoolean(false);
            manager.openTab("closable-tab", "Closable", "✕", new Label("Test"), true, () -> closed.set(true), null);

            manager.closeTab("closable-tab");
            assertTrue(closed.get());
            assertFalse(manager.hasTab("closable-tab"));
        });
    }
}
