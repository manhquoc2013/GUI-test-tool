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

    @Test
    @DisplayName("Anti-thrashing guard halts speculative mutations after 3 patches on the same case")
    void testAntiThrashingGuardHaltsAfterThreeMutations(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        TestCase tc = new TestCase("tc_thrash", "ThrashCase", "Desc", "ThrashCase.java", "Ready");
        java.nio.file.Path scriptFile = tempDir.resolve("ThrashCase.java");
        java.nio.file.Files.writeString(scriptFile, "package com.test;\npublic class ThrashCase {\n    String s = \"btn_0\";\n}");

        OptimizedSelfHealingExecutor executor = new OptimizedSelfHealingExecutor(
                null, null, null, null, tempDir, null, tc
        );

        java.lang.reflect.Method m = OptimizedSelfHealingExecutor.class.getDeclaredMethod("persistMutation", String.class, String.class);
        m.setAccessible(true);

        // Patch 1
        m.invoke(executor, "btn_0", "btn_1");
        assertEquals(1, OptimizedSelfHealingExecutor.getMutationCount("tc_thrash"));
        assertTrue(java.nio.file.Files.readString(scriptFile).contains("btn_1"));

        // Patch 2
        m.invoke(executor, "btn_1", "btn_2");
        assertEquals(2, OptimizedSelfHealingExecutor.getMutationCount("tc_thrash"));
        assertTrue(java.nio.file.Files.readString(scriptFile).contains("btn_2"));

        // Patch 3
        m.invoke(executor, "btn_2", "btn_3");
        assertEquals(3, OptimizedSelfHealingExecutor.getMutationCount("tc_thrash"));
        assertTrue(java.nio.file.Files.readString(scriptFile).contains("btn_3"));

        // Patch 4 -> Should be BLOCKED by Anti-Thrashing Guard!
        m.invoke(executor, "btn_3", "btn_4_should_not_exist");
        assertEquals(3, OptimizedSelfHealingExecutor.getMutationCount("tc_thrash"));
        String finalContent = java.nio.file.Files.readString(scriptFile);
        assertFalse(finalContent.contains("btn_4_should_not_exist"), "4th mutation should have been halted by anti-thrashing guard");
        assertTrue(finalContent.contains("btn_3"), "Script should retain the 3rd mutation");

        // Verify forensic audit log entry was added
        List<HealedStepMeta> logs = executor.getAuditLogs();
        assertTrue(logs.stream().anyMatch(l -> "ANTI_THRASHING_GUARD".equals(l.getMethod())));
    }
}
