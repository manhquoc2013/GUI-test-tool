package com.studio.service;

import com.studio.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResourceReleaseServiceTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        ResourceReleaseService.resetForTesting();
        DatabaseManager.setInstance(null);
    }

    @Test
    void testRegisterAndExecuteCleanupTask() {
        AtomicBoolean executed = new AtomicBoolean(false);
        ResourceReleaseService.registerCleanupTask("test-task", () -> executed.set(true));

        ResourceReleaseService.releaseAllOnShutdown();

        assertTrue(executed.get(), "Registered cleanup task must be executed during shutdown");
    }

    @Test
    void testUnregisterCleanupTask() {
        AtomicBoolean executed = new AtomicBoolean(false);
        ResourceReleaseService.registerCleanupTask("unreg-task", () -> executed.set(true));
        ResourceReleaseService.unregisterCleanupTask("unreg-task");

        ResourceReleaseService.releaseAllOnShutdown();

        assertFalse(executed.get(), "Unregistered task must not execute");
    }

    @Test
    void testReleaseAfterTestRunDoesNotThrow() {
        assertDoesNotThrow(() -> {
            ResourceReleaseService.releaseAfterTestRun();
        });
    }

    @Test
    void testReleaseAfterExportDoesNotThrow() {
        assertDoesNotThrow(() -> {
            ResourceReleaseService.releaseAfterExport();
        });
    }

    @Test
    void testReleaseIdleResourcesDoesNotThrow() {
        assertDoesNotThrow(() -> {
            ResourceReleaseService.releaseIdleResources();
        });
    }

    @Test
    void testDatabaseManagerCompactAndShutdown() {
        DatabaseManager memDb = DatabaseManager.createInMemory("resource_test_db");
        assertDoesNotThrow(() -> {
            memDb.compactAndReleaseMemory();
            memDb.shutdown();
        }, "Database compact and shutdown must execute cleanly");
    }
}

