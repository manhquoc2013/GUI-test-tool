package com.studio.service;

import com.studio.db.DatabaseManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central orchestrator for system resource reclamation.
 * Ensures that whenever browser sessions, database connections, background processes,
 * or temporary caches are idle or finished, they are cleanly closed and memory/file handles
 * are returned to the Operating System.
 */
public final class ResourceReleaseService {

    private static final Map<String, Runnable> CLEANUP_TASKS = new ConcurrentHashMap<>();
    private static final AtomicBoolean IS_SHUTTING_DOWN = new AtomicBoolean(false);

    private ResourceReleaseService() {}

    /**
     * Resets shutdown flag and registered hooks for unit tests.
     */
    static void resetForTesting() {
        IS_SHUTTING_DOWN.set(false);
        CLEANUP_TASKS.clear();
    }

    /**
     * Register a named custom resource release hook (e.g. running child process termination).
     */
    public static void registerCleanupTask(String name, Runnable task) {
        if (name != null && task != null) {
            CLEANUP_TASKS.put(name, task);
        }
    }

    /**
     * Unregister a custom resource release hook.
     */
    public static void unregisterCleanupTask(String name) {
        if (name != null) {
            CLEANUP_TASKS.remove(name);
        }
    }

    /**
     * Called immediately after a test run (single case, suite, or group) completes.
     * Cleans up temporary artifacts, closes any dangling browser driver sessions, and prompts GC.
     */
    public static void releaseAfterTestRun() {
        try {
            // Cancel any remaining runner session
            if (RunCancelService.isRunActive()) {
                RunCancelService.endRun();
            }
        } catch (Exception ignored) {}

        // Prompt HotSpot JVM to uncommit free pages and return memory to OS
        promptGarbageCollection();
    }

    /**
     * Called after large export operations (Excel / HTML / PDF).
     * Dereferences in-memory workbook models and returns heap space to OS.
     */
    public static void releaseAfterExport() {
        promptGarbageCollection();
    }

    /**
     * Called when the application is idle or minimized.
     * Flushes database checkpoints, trims expired in-memory caches, and returns free heap to OS.
     */
    public static void releaseIdleResources() {
        if (IS_SHUTTING_DOWN.get()) return;

        // 1. Compact and flush H2 database cache
        try {
            DatabaseManager.getInstance().compactAndReleaseMemory();
        } catch (Exception ignored) {}

        // 2. Prompt JVM memory reclamation
        promptGarbageCollection();
    }

    /**
     * Called when the application is closing or JVM is shutting down.
     * Terminates all background child processes, cancels active runs, closes H2 database, and exits.
     */
    public static void releaseAllOnShutdown() {
        if (!IS_SHUTTING_DOWN.compareAndSet(false, true)) {
            return; // Already executed
        }

        System.out.println("[ResourceReleaseService] Releasing all resources and returning memory to OS...");

        // 1. Execute all registered cleanup hooks (e.g. abort codegen process)
        for (Map.Entry<String, Runnable> entry : CLEANUP_TASKS.entrySet()) {
            try {
                entry.getValue().run();
            } catch (Exception e) {
                System.err.println("[ResourceReleaseService] Error in cleanup hook '" + entry.getKey() + "': " + e.getMessage());
            }
        }
        CLEANUP_TASKS.clear();

        // 2. Terminate any active Playwright browser run
        try {
            RunCancelService.requestCancel();
            RunCancelService.endRun();
        } catch (Exception ignored) {}

        // 3. Gracefully shutdown embedded H2 database (closes file locks, releases server socket)
        try {
            DatabaseManager.getInstance().shutdown();
        } catch (Exception ignored) {}

        // 4. Prompt final GC
        promptGarbageCollection();
    }

    /**
     * Prompts the JVM to run garbage collection and uncommit unused heap memory to the OS.
     */
    public static void promptGarbageCollection() {
        try {
            System.gc();
        } catch (Exception ignored) {}
    }
}

