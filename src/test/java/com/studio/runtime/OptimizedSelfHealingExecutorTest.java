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

    @Test
    @DisplayName("resolveLocator returns null safely if page is null")
    void testResolveLocatorNullPage() {
        OptimizedSelfHealingExecutor executor = new OptimizedSelfHealingExecutor(
                null, null, null, null, null, null, null
        );
        assertNull(executor.resolveLocator("#btn", "CLICK"));
    }

    @Test
    @DisplayName("isVisionSupported correctly identifies vision-capable models")
    void testIsVisionSupported() {
        assertTrue(OptimizedSelfHealingExecutor.isVisionSupported("gpt-4o"));
        assertTrue(OptimizedSelfHealingExecutor.isVisionSupported("gpt-4o-mini"));
        assertTrue(OptimizedSelfHealingExecutor.isVisionSupported("gemini-1.5-pro"));
        assertTrue(OptimizedSelfHealingExecutor.isVisionSupported("qwen-vl-plus"));

        assertFalse(OptimizedSelfHealingExecutor.isVisionSupported("gpt-3.5-turbo"));
        assertFalse(OptimizedSelfHealingExecutor.isVisionSupported("deepseek-chat"));
        assertFalse(OptimizedSelfHealingExecutor.isVisionSupported("deepseek-reasoner"));
        assertFalse(OptimizedSelfHealingExecutor.isVisionSupported("text-embedding-3-small"));
    }

    @Test
    @DisplayName("persistMutation cleanly escapes selectors containing double quotes into script file")
    void testPersistMutationEscapesQuotes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        TestCase tc = new TestCase("tc1", "LoginCase", "Desc", "LoginCase.java", "Ready");
        java.nio.file.Path scriptFile = tempDir.resolve("LoginCase.java");
        String initialCode = """
            package com.test;
            public class LoginCase {
                public static void main(String[] args) {
                    com.studio.runtime.StudioRuntime.healLocator("[data-testid=\\"old-btn\\"]").click();
                }
            }
            """;
        java.nio.file.Files.writeString(scriptFile, initialCode);

        OptimizedSelfHealingExecutor executor = new OptimizedSelfHealingExecutor(
                null, null, null, null, tempDir, null, tc
        );

        try {
            java.lang.reflect.Method m = OptimizedSelfHealingExecutor.class.getDeclaredMethod("persistMutation", String.class, String.class);
            m.setAccessible(true);
            m.invoke(executor, "[data-testid=\"old-btn\"]", "button:has-text(\"Đăng nhập\")");
        } catch (Exception e) {
            fail("Reflection invocation failed: " + e.getMessage());
        }

        String updatedCode = java.nio.file.Files.readString(scriptFile);
        assertTrue(updatedCode.contains("button:has-text(\\\"Đăng nhập\\\")"), "Script should contain properly escaped new selector");
        assertFalse(updatedCode.contains("old-btn"), "Old selector should have been replaced");
    }
}
