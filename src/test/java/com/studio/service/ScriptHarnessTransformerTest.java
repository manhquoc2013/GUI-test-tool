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
}
