package com.studio.service;

import com.studio.model.TestStep;
import com.studio.store.ProjectStore;
import com.studio.ui.StepScriptBidirectionalSync;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A case can be created from a specification — steps authored, no Java written yet. The workspace fills
 * the script in from those steps, so the artifact a save produces has to compile both as stored and
 * after the harness transform; otherwise the builder's own work can never run.
 */
class CaseScriptSkeletonCompilesTest {

    private static final String CASE_NAME = "TC02_Kich_Ban_Co_Buoc";
    private static final Pattern CLASS_NAME = Pattern.compile("public class (\\w+)");

    @Test
    void stepsAuthoredIntoAnEmptyCaseCompileAsSavedAndAsHarness() throws Exception {
        List<TestStep> steps = List.of(
                new TestStep(1, "NAVIGATE", "https://example.com/form", "", "Mở form", ""),
                new TestStep(2, "CLICK", "#submit", "", "Bấm gửi", ""));

        String script = StepScriptBidirectionalSync
                .parseDocument(ProjectStore.scriptSkeleton(CASE_NAME))
                .render(steps, "https://example.com/form");

        assertEquals(2, StepScriptBidirectionalSync.parseDocument(script).getSteps().size(),
                "The rendered script must read back as the same two steps");
        assertCompiles(script);

        String screenshotBase = Files.createTempDirectory("case-script-shot").resolve("shot").toString();
        String harness = ScriptHarnessTransformer.transform(script, false, screenshotBase, "TC02_Run");
        assertFalse(harness.contains("newPage()"),
                "The harness must resolve page from the Studio runtime: " + harness);
        assertCompiles(harness);
    }

    private static void assertCompiles(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "These tests need a JDK: the runner compiles scripts the same way");
        Matcher matcher = CLASS_NAME.matcher(source);
        assertTrue(matcher.find(), "No class in the generated script:\n" + source);

        Path dir = Files.createTempDirectory("case-script-compile");
        Path file = dir.resolve(matcher.group(1) + ".java");
        Files.writeString(file, source);

        int exit = compiler.run(null, null, null, "-d", dir.toString(), "-encoding", "UTF-8",
                "-cp", System.getProperty("java.class.path"), file.toString());

        assertEquals(0, exit, "javac rejected the generated script:\n" + source);
    }
}
