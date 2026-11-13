package com.studio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptHarnessTransformerTest {

    private static final String CODEGEN = """
            import com.microsoft.playwright.*;
            import com.microsoft.playwright.options.*;
            import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
            import java.util.*;

            public class Example {
              public static void main(String[] args) {
                try (Playwright playwright = Playwright.create()) {
                  Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false));
                  BrowserContext context = browser.newContext();
                  Page page = context.newPage();
                  page.navigate("https://example.com");
                  page.close();
                }
              }
            }
            """;

    @Test
    void transformsTryWithResourcesCodegenIntoCompilableHarness() {
        String out = ScriptHarnessTransformer.transform(
                CODEGEN, false, "/tmp/shot", "tc_abc_Run");

        assertTrue(out.contains("public class tc_abc_Run"), out);
        assertFalse(out.contains("Playwright.create()"), out);
        assertFalse(out.contains(".launch("), out);
        assertFalse(out.contains("newContext()"), out);
        assertFalse(out.contains("newPage()"), out);
        assertTrue(out.contains("StudioRuntime.page()"), out);
        assertTrue(out.contains("try {"), out);
        assertTrue(out.contains("} finally {"), out);
        assertTrue(out.contains("page.navigate(\"https://example.com\")"), out);
    }

    @Test
    void sanitizeClassNameReplacesHyphens() {
        assertEquals("tc_74692232_Run", ScriptHarnessTransformer.sanitizeClassName("tc-74692232_Run"));
        assertEquals("C_123", ScriptHarnessTransformer.sanitizeClassName("123"));
        assertEquals("case_2", ScriptHarnessTransformer.sanitizeClassName("case 2"));
        assertEquals("C_case", ScriptHarnessTransformer.sanitizeClassName("case"));
        assertEquals("C_default", ScriptHarnessTransformer.sanitizeClassName("default"));
    }

    @Test
    void transformsClassWithSpacesOrKeywordsIntoValidHarnessClass() {
        String spacedClassCode = """
                package com.test;
                import com.microsoft.playwright.*;
                public class case 2 {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                        page.navigate("https://example.com");
                    }
                }
                """;

        String out = ScriptHarnessTransformer.transform(spacedClassCode, false, "/tmp/shot", "tc_case2_Run");
        assertTrue(out.contains("public class tc_case2_Run {"), out);
        assertFalse(out.contains("public class case 2"), out);
        assertFalse(out.contains("Run 2"), out);
        assertTrue(out.contains("com.studio.runtime.StudioRuntime.page().onDialog"), out);
    }

    @Test
    void transformsClassWithoutPublicModifier() {
        String noPublicCode = """
                package com.test;
                import com.microsoft.playwright.*;
                class UnadornedClass {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                    }
                }
                """;

        String out = ScriptHarnessTransformer.transform(noPublicCode, false, "/tmp/shot", "tc_fixed_Run");
        assertTrue(out.contains("public class tc_fixed_Run {"), out);
    }

    @Test
    void transformsLocatorCallsToSelfHealingLocator() {
        String script = """
                public class Example {
                  public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    page.locator("button#submit-btn").click();
                    page.locator("input[name='user']").fill("admin");
                  }
                }
                """;

        String out = ScriptHarnessTransformer.transform(script, false, "/tmp/shot", "tc_heal_Run");
        assertTrue(out.contains("StudioRuntime.healLocator(\"button#submit-btn\").click()"), out);
        assertTrue(out.contains("StudioRuntime.healLocator(\"input[name='user']\").fill(\"admin\")"), out);
    }

    @Test
    void transformsPlaywrightAssertionsToStudioAssertions() {
        String script = """
                package com.test;
                import com.microsoft.playwright.*;
                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
                public class Example {
                  public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    assertThat(page.locator("button#submit")).isVisible();
                  }
                }
                """;

        String out = ScriptHarnessTransformer.transform(script, false, "/tmp/shot", "tc_assert_Run");
        assertTrue(out.contains("import static com.studio.runtime.StudioAssertions.assertThat;"), out);
        assertFalse(out.contains("PlaywrightAssertions.assertThat"), out);
        assertTrue(out.contains("StudioRuntime.healLocator(\"button#submit\")"), out);
    }

    @Test
    void transformsInvalidHasUrlLambdaToValidPattern() {
        String script = """
                package com.test;
                import com.microsoft.playwright.*;
                public class Example {
                  public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    assertThat(page).hasURL(url -> url.contains("/dashboard"));
                  }
                }
                """;

        String out = ScriptHarnessTransformer.transform(script, false, "/tmp/shot", "tc_url_Run");
        assertFalse(out.contains("url -> url.contains"), out);
        assertTrue(out.contains("Pattern.compile"), out);
        assertTrue(out.contains("/dashboard"), out);
    }

    /**
     * The AI sometimes writes a file of Vietnamese notes instead of code when it has no URL to test.
     * Such a file is not a script: there is nothing to rename, so nothing can be compiled. The runner
     * has to recognise it before pretending the case is runnable.
     */
    @Test
    void aNotesOnlyFileDeclaresNoClass() {
        String notes = """
                // [THÔNG BÁO] Không tạo test script tự động vì yêu cầu không có URL/link trang web cần test.
                // Bộ test case đã được thiết kế đầy đủ tại các tab bên cạnh:
                // - Tab 'Các bước': Danh sách bước thao tác
                // - Tab 'Assertions': Tiêu chí xác thực kết quả
                """;

        assertFalse(ScriptHarnessTransformer.declaresClass(notes), notes);
        assertFalse(ScriptHarnessTransformer.declaresClass("/* class Example is only mentioned here */"),
                "A block comment cannot declare a class");
        assertFalse(ScriptHarnessTransformer.declaresClass(""), "An empty file declares nothing");
        assertFalse(ScriptHarnessTransformer.declaresClass(null), "A missing file declares nothing");
    }

    @Test
    void anyClassDeclarationIsAScript() {
        assertTrue(ScriptHarnessTransformer.declaresClass(CODEGEN));
        assertTrue(ScriptHarnessTransformer.declaresClass("class PackagePrivate { }"),
                "A package-private unit still compiles and gets renamed by the harness");
        // A URL in a string literal contains "//": stripping it must not eat a class on that line.
        assertTrue(ScriptHarnessTransformer.declaresClass(
                "public class A { void m() { String u = \"https://x.test\"; } }"));
    }
}
