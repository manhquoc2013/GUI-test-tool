package com.studio.service;

import com.studio.ui.StepScriptBidirectionalSync;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tracer numbers steps, and the builder numbers the cards. If those two disagree, a failed run
 * paints a check mark on the wrong step — worse than showing nothing. These tests pin the numbering
 * contract on both shapes of script the app stores: bare statements and a recorded Java class.
 */
class StepTraceInstrumenterTest {

    private static final String BARE = """
            page.navigate("https://example.com");
            page.locator("#email").fill("admin");
            // 3. Kiểm tra trang chủ
            page.locator("#submit").click();
            """;

    private static final String WRAPPED = """
            package com.test;

            import com.microsoft.playwright.*;

            public class Recorded {
                public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    page.navigate("https://example.com");
                    page.locator("#email").fill("admin");
                    // 3. Kiểm tra trang chủ
                    page.locator("#submit").click();
                }
            }
            """;

    @Test
    void everyStepGetsItsOwnMarkerNumberedFromOne() {
        StepTraceInstrumenter.Instrumented result = StepTraceInstrumenter.instrument(BARE);
        String script = result.script();

        assertTrue(script.contains("com.studio.runtime.StudioRuntime.stepEnter(1);"));
        assertTrue(script.contains("com.studio.runtime.StudioRuntime.stepEnter(2);"));
        assertTrue(script.contains("com.studio.runtime.StudioRuntime.stepEnter(4);"),
                "The step after the comment keeps its own card number");
        // A note is not an action, so it carries no start marker but keeps its slot.
        assertFalse(script.contains("stepEnter(3);"), "A comment must not become a step marker");
        assertEquals(5, result.statements().size(), "statements() is 1-based, index 0 unused");
        assertEquals("// 3. Kiểm tra trang chủ", result.statements().get(3),
                "The comment still occupies its step slot so later numbers do not shift");
        assertEquals("page.locator(\"#submit\").click();", result.statements().get(4));
    }

    @Test
    void markerNumbersMatchTheBuilderCardNumbers() {
        for (String script : List.of(BARE, WRAPPED)) {
            StepTraceInstrumenter.Instrumented result = StepTraceInstrumenter.instrument(script);
            int builderSteps = StepScriptBidirectionalSync.scriptToSteps(script).size();
            assertEquals(builderSteps, result.statements().size() - 1,
                    "The tracer must report exactly the steps the builder shows on cards");
        }
    }

    @Test
    void markersStayInsideTheMainBodyOfARecordedClass() {
        StepTraceInstrumenter.Instrumented result = StepTraceInstrumenter.instrument(WRAPPED);
        String script = result.script();

        int navMarker = script.indexOf("stepEnter(1);");
        assertTrue(navMarker > script.indexOf("public static void main"),
                "A marker outside main() would not compile");
        assertTrue(navMarker < script.indexOf("page.navigate"), "The marker must precede its step");
        assertTrue(script.contains("package com.test;"), "The class envelope must survive");
        assertTrue(script.contains("Page page = com.studio.runtime.StudioRuntime.page();"));
    }

    @Test
    void aWrappedStatementKeepsTheBuilderCardNumbering() {
        String script = """
                page.locator("#email")
                    .fill("admin");
                page.locator("#submit").click();
                """;

        StepTraceInstrumenter.Instrumented result = StepTraceInstrumenter.instrument(script);

        // The builder shows one card per line, so a wrapped statement is two cards. Tracing it as
        // one step would shift every later verdict onto the wrong card.
        assertEquals(StepScriptBidirectionalSync.scriptToSteps(script).size(),
                result.statements().size() - 1, "Step count must equal the builder's card count");
        assertEquals(3, result.statements().size() - 1);
        assertEquals("page.locator(\"#email\")", result.statements().get(1));
        assertEquals(".fill(\"admin\");", result.statements().get(2),
                "The continuation is reported as the card the tester sees");

        assertTrue(result.script().contains("stepEnter(1);"));
        assertFalse(result.script().contains("stepEnter(2);"),
                "A marker before a continuation line does not compile");
        assertTrue(result.script().contains("stepEnter(3);"));
    }

    @Test
    void controlFlowContinuationsNeverGetAMarkerThatWouldBreakCompilation() {
        String script = """
                if (ready) {
                    page.reload();
                } else {
                    page.goBack();
                }
                """;

        String instrumented = StepTraceInstrumenter.instrument(script).script();

        assertFalse(instrumented.contains("stepEnter(1);\n                } else"),
                "A marker between } and else does not compile");
        assertFalse(instrumented.matches("(?s).*stepEnter\\(\\d+\\);\\s*else.*"),
                "No marker may separate the else from its brace");
    }

    @Test
    void aStepGroupIncludeKeepsItsMarkerBecauseItRunsRealStatements() {
        String script = """
                // @include("grp-login")
                page.reload();
                """;

        StepTraceInstrumenter.Instrumented result = StepTraceInstrumenter.instrument(script);

        assertTrue(result.script().contains("stepEnter(1);"),
                "The group card executes statements, so its step must be traceable");
        assertTrue(result.script().contains("// @include(\"grp-login\")"),
                "The include itself must stay intact so expansion still finds it");
        assertEquals(2, result.statements().size() - 1);
    }

    /**
     * The marker is Java source the browser-side run has to compile. Instrumentation that reads well
     * but does not build would only surface at run time, on a user's machine — this compiles the
     * real pipeline (instrument, then the harness transform) with the real classpath.
     */
    @Test
    void anInstrumentedScriptStillCompilesAfterTheHarnessTransform(@TempDir Path tmp) throws Exception {
        String instrumented = StepTraceInstrumenter.instrument(WRAPPED).script();
        String harness = ScriptHarnessTransformer.transform(instrumented, false,
                tmp.resolve("shot").toString(), "TracedHarness");

        assertTrue(harness.contains("StudioRuntime.stepEnter(1);"), harness);
        assertTrue(compiles(harness, tmp), "Instrumented harness does not compile");
    }

    private static boolean compiles(String source, Path dir) throws Exception {
        Path file = dir.resolve("TracedHarness.java");
        Files.writeString(file, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on a JDK, not a JRE");
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = new ArrayList<>(List.of(
                    "-d", dir.toString(), "-encoding", "UTF-8",
                    "-cp", System.getProperty("java.class.path")));
            boolean ok = Boolean.TRUE.equals(
                    compiler.getTask(null, fm, null, options, null, fm.getJavaFileObjects(file.toFile()))
                            .call());
            if (!ok) {
                System.out.println(source);
            }
            return ok;
        }
    }

    @Test
    void anEmptyOrNullScriptIsLeftAlone() {
        StepTraceInstrumenter.Instrumented empty = StepTraceInstrumenter.instrument("");
        assertEquals("", empty.script());
        assertEquals(1, empty.statements().size());

        StepTraceInstrumenter.Instrumented missing = StepTraceInstrumenter.instrument(null);
        assertEquals("", missing.script());
    }
}
