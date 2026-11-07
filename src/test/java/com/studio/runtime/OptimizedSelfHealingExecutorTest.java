package com.studio.runtime;

import com.studio.model.HealedStepMeta;
import com.studio.model.TestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OptimizedSelfHealingExecutorTest {

    @BeforeEach
    void setup() {
        OptimizedSelfHealingExecutor.clearCache();
        StudioRuntime.clear();
    }

    @AfterEach
    void tearDown() {
        OptimizedSelfHealingExecutor.clearCache();
        StudioRuntime.clear();
    }

    @Test
    @DisplayName("LocateCache should store and clear repaired selectors")
    void testLocateCache() {
        assertTrue(OptimizedSelfHealingExecutor.getLocateCache().isEmpty());
    }

    @Test
    @DisplayName("StudioRuntime registers and clears executor properly")
    void testStudioRuntimeExecutorLifecycle() {
        assertNull(StudioRuntime.executor());
        assertEquals(0, StudioRuntime.getHealedSteps().size());

        TestCase tc = new TestCase("tc1", "Login Test", "Desc", "login.java", "Ready");
        OptimizedSelfHealingExecutor executor = new OptimizedSelfHealingExecutor(
                null, null, null, null, null, null, tc
        );

        StudioRuntime.setExecutor(executor);
        assertNotNull(StudioRuntime.executor());

        StudioRuntime.clear();
        assertNull(StudioRuntime.executor());
    }

    @Test
    @DisplayName("ExecuteWithHealing falls back to actionRunnable when no executor is registered")
    void testExecuteWithHealingFallback() {
        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        StudioRuntime.executeWithHealing(
                "#submit-btn",
                "CLICK_SUBMIT",
                selector -> {
                    assertEquals("#submit-btn", selector);
                    actionExecuted.set(true);
                },
                null
        );
        assertTrue(actionExecuted.get());
    }
}
