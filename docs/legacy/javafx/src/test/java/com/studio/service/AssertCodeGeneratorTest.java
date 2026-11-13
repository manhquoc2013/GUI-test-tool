package com.studio.service;

import com.studio.model.Assertion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssertCodeGeneratorTest {

    @Test
    void testPageTitleContainsIsSupported() {
        assertTrue(AssertCodeGenerator.isSupported("PAGE_TITLE_CONTAINS"));
        assertTrue(AssertCodeGenerator.isSupported("TITLE_CONTAINS"));
        assertTrue(AssertCodeGenerator.isSupported("PAGE_CONTAINS_TEXT"));
        assertTrue(AssertCodeGenerator.isSupported("PAGE_NOT_CONTAINS_TEXT"));
        assertTrue(AssertCodeGenerator.isSupported("ELEMENT_EXISTS"));
        assertTrue(AssertCodeGenerator.isSupported("ELEMENT_NOT_VISIBLE"));
        assertFalse(AssertCodeGenerator.isSupported("NON_EXISTENT_TYPE_XYZ"));
    }

    @Test
    void testUnsupportedTypeDoesNotGenerateUnreachableStatement() {
        Assertion unknown = new Assertion();
        unknown.setType("NON_EXISTENT_TYPE_XYZ");
        unknown.setValue("val");
        unknown.setStatus("ACTIVE");

        String block = AssertCodeGenerator.generateAssertBlock(List.of(unknown));
        // Must record as FAILED without an unconditional throw inside try
        assertTrue(block.contains("Unsupported or unknown assertion type"));
        assertFalse(block.contains("throw new AssertionError(\"Unknown assert type"));
    }

    @Test
    void testCompilesScriptWithAiGeneratedAssertions(@TempDir Path tempDir) throws Exception {
        String scriptCode = """
                package com.test;

                import com.microsoft.playwright.*;

                public class SampleCase {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                        page.navigate("https://example.com");
                    }
                }
                """;

        Assertion a1 = new Assertion();
        a1.setType("PAGE_TITLE_CONTAINS");
        a1.setValue("example");
        a1.setStatus("ACTIVE");

        Assertion a2 = new Assertion();
        a2.setType("ELEMENT_VISIBLE");
        a2.setLocator("h1");
        a2.setStatus("ACTIVE");

        Assertion a3 = new Assertion();
        a3.setType("SOME_UNKNOWN_TYPE");
        a3.setStatus("ACTIVE");

        Assertion a4 = new Assertion();
        a4.setType("TABLE_ROW_COUNT");
        a4.setLocator("table#orders");
        a4.setValue(">= 3 rows");
        a4.setStatus("ACTIVE");

        Assertion a5 = new Assertion();
        a5.setType("IMAGE_COUNT");
        a5.setValue("");
        a5.setStatus("ACTIVE");

        Assertion a6 = new Assertion();
        a6.setType("ELEMENT_COUNT");
        a6.setLocator(".item");
        a6.setValue("!= 0");
        a6.setStatus("ACTIVE");

        String harnessClass = "SampleCase_Run";
        String transformed = ScriptHarnessTransformer.transform(
                scriptCode, false, tempDir.resolve("sample").toString(), harnessClass);

        String assertBlock = AssertCodeGenerator.generateAssertBlock(List.of(a1, a2, a3, a4, a5, a6));
        String marker = "\n        } finally {";
        int idx = transformed.lastIndexOf(marker);
        assertTrue(idx >= 0, "Transformer should produce finally marker");
        String fullSource = transformed.substring(0, idx) + assertBlock + transformed.substring(idx);

        Path sourceFile = tempDir.resolve(harnessClass + ".java");
        Files.writeString(sourceFile, fullSource);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "System Java compiler required for harness compilation");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> options = new ArrayList<>(Arrays.asList(
                "-d", tempDir.toString(),
                "-encoding", "UTF-8",
                "-cp", System.getProperty("java.class.path")));

        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(sourceFile.toFile());
        JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostics, options, null, units);
        boolean success = Boolean.TRUE.equals(task.call());
        fm.close();

        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                fail("Compilation error line " + d.getLineNumber() + ": " + d.getMessage(null));
            }
        }

        assertTrue(success,
                "Generated harness with AI assertions must compile cleanly without unreachable statement errors");
        assertTrue(Files.exists(tempDir.resolve("com/test/" + harnessClass + ".class")),
                "Compiled class file must be generated");
    }

    @Test
    void testAiTestGeneratorNormalizesSynonymTypes() {
        assertEquals("TITLE_CONTAINS", AiTestGeneratorService.normalizeAssertionType("PAGE_TITLE_CONTAINS"));
        assertEquals("TITLE_EQUALS", AiTestGeneratorService.normalizeAssertionType("PAGE_TITLE_EQUALS"));
        assertEquals("URL_CONTAINS", AiTestGeneratorService.normalizeAssertionType("PAGE_URL_CONTAINS"));
        assertEquals("URL_EQUALS", AiTestGeneratorService.normalizeAssertionType("PAGE_URL_EQUALS"));
        assertEquals("PAGE_HAS_TEXT", AiTestGeneratorService.normalizeAssertionType("PAGE_CONTAINS_TEXT"));
        assertEquals("ELEMENT_VISIBLE", AiTestGeneratorService.normalizeAssertionType("ELEMENT_EXISTS"));
        assertEquals("ELEMENT_HIDDEN", AiTestGeneratorService.normalizeAssertionType("ELEMENT_NOT_VISIBLE"));
        assertEquals("ELEMENT_HAS_TEXT", AiTestGeneratorService.normalizeAssertionType("ELEMENT_TEXT_CONTAINS"));
        assertEquals("ELEMENT_CHECKED", AiTestGeneratorService.normalizeAssertionType("CHECKBOX_CHECKED"));
        assertEquals("ATTRIBUTE_EQUALS", AiTestGeneratorService.normalizeAssertionType("ELEMENT_HAS_ATTRIBUTE"));
        assertEquals("CUSTOM_JS", AiTestGeneratorService.normalizeAssertionType("CUSTOM_JS_TRUE"));
    }
}
