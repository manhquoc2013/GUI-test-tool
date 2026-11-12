package com.studio.ui;

import com.studio.model.TestStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StepScriptBidirectionalSyncTest {

    @Test
    void testStepsToScript_generatesValidPlaywrightCalls() {
        List<TestStep> steps = new ArrayList<>();
        steps.add(new TestStep(1, "NAVIGATE", "https://example.com", "", "Go to site", ""));
        steps.add(new TestStep(2, "FILL", "input#user", "admin", "Enter user", ""));
        steps.add(new TestStep(3, "CLICK", "button#submit", "", "Submit", ""));
        steps.add(new TestStep(4, "ASSERT_VISIBLE", ".welcome", "", "Verify welcome", ""));

        String script = StepScriptBidirectionalSync.stepsToScript(steps, "");

        assertNotNull(script);
        assertTrue(script.contains("page.navigate(\"https://example.com\");"));
        assertTrue(script.contains("page.locator(\"input#user\").fill(\"admin\");"));
        assertTrue(script.contains("page.locator(\"button#submit\").click();"));
        assertTrue(script.contains("assertThat(page.locator(\".welcome\")).isVisible();"));
    }

    @Test
    void testScriptToSteps_parsesPlaywrightCalls() {
        String script = """
                // Auto test
                page.navigate("https://login.example.com");
                page.locator("input#username").fill("tester");
                page.locator("button.login-btn").click();
                assertThat(page.locator("div.alert")).isVisible();
                assertThat(page.locator("span.user-name")).hasText("tester");
                """;

        List<TestStep> steps = StepScriptBidirectionalSync.scriptToSteps(script);

        assertEquals(6, steps.size(), "The leading comment is kept as a note so nothing is lost");
        assertEquals(StepScriptBidirectionalSync.ACTION_NOTE, steps.get(0).getAction());
        assertEquals("Auto test", steps.get(0).getTarget());

        assertEquals("NAVIGATE", steps.get(1).getAction());
        assertEquals("https://login.example.com", steps.get(1).getTarget());

        assertEquals("FILL", steps.get(2).getAction());
        assertEquals("input#username", steps.get(2).getTarget());
        assertEquals("tester", steps.get(2).getTestData());

        assertEquals("CLICK", steps.get(3).getAction());
        assertEquals("button.login-btn", steps.get(3).getTarget());

        assertEquals("ASSERT_VISIBLE", steps.get(4).getAction());
        assertEquals("div.alert", steps.get(4).getTarget());

        assertEquals("ASSERT_TEXT", steps.get(5).getAction());
        assertEquals("span.user-name", steps.get(5).getTarget());
        assertEquals("tester", steps.get(5).getTestData());
    }

    @Test
    void disabledStepsStayInTheModelButNeverReachTheScript() {
        TestStep disabled = new TestStep(2, "CLICK", "button.delete", "", "", "");
        disabled.setEnabled(false);
        List<TestStep> steps = new ArrayList<>();
        steps.add(new TestStep(1, "NAVIGATE", "https://example.com", "", "", ""));
        steps.add(disabled);

        String script = StepScriptBidirectionalSync.stepsToScript(steps, "");

        assertTrue(script.contains("page.navigate(\"https://example.com\");"));
        assertFalse(script.contains("button.delete"),
                "A disabled step must not be emitted into the executable script");
        assertEquals(2, steps.size(), "Generating a script must not drop disabled steps from the model");
    }

    @Test
    void invalidDelayFallsBackToDefaultInsteadOfInjectingCode() {
        TestStep badDelay = new TestStep(1, "DELAY", "", "1000); System.exit(0", "", "");
        String script = StepScriptBidirectionalSync.stepsToScript(List.of(badDelay), "");

        assertEquals("page.waitForTimeout(" + StepScriptBidirectionalSync.DEFAULT_DELAY_MS + ");\n", script);
    }

    @Test
    void delayIsClampedToTheMaximum() {
        TestStep hugeDelay = new TestStep(1, "DELAY", "", String.valueOf(StepScriptBidirectionalSync.MAX_DELAY_MS * 10), "", "");
        String script = StepScriptBidirectionalSync.stepsToScript(List.of(hugeDelay), "");

        assertEquals("page.waitForTimeout(" + StepScriptBidirectionalSync.MAX_DELAY_MS + ");\n", script);
    }

    @Test
    void quotesAndBackslashesAreEscapedInGeneratedCode() {
        TestStep fill = new TestStep(1, "FILL", "input[name=\"q\"]", "a\\b \"c\"", "", "");
        String script = StepScriptBidirectionalSync.stepsToScript(List.of(fill), "");

        assertTrue(script.contains("page.locator(\"input[name=\\\"q\\\"]\").fill(\"a\\\\b \\\"c\\\"\");"));
    }

    @Test
    void generatedScriptRoundTripsSelectorsContainingQuotes() {
        TestStep click = new TestStep(1, "CLICK", "[data-test=\"submit\"]", "", "", "");
        String script = StepScriptBidirectionalSync.stepsToScript(List.of(click), "");

        List<TestStep> parsed = StepScriptBidirectionalSync.scriptToSteps(script);

        assertEquals(1, parsed.size(), "An escaped quote must not hide the step from the parser");
        assertEquals("[data-test=\"submit\"]", parsed.get(0).getTarget());
        assertEquals("CLICK", parsed.get(0).getAction());
    }

    @Test
    void multiLineValuesAreEscapedIntoOneValidLiteral() {
        TestStep fill = new TestStep(1, "FILL", "#notes", "line1\nline2", "", "");
        String script = StepScriptBidirectionalSync.stepsToScript(List.of(fill), "");

        assertFalse(script.contains("line1\nline2"), "A raw newline would break the Java string literal");
        assertTrue(script.contains("line1\\nline2"));

        List<TestStep> parsed = StepScriptBidirectionalSync.scriptToSteps(script);
        assertEquals("line1\nline2", parsed.get(0).getTestData());
    }

    @Test
    void startUrlIsNotEmittedTwice() {
        List<TestStep> steps = List.of(new TestStep(1, "NAVIGATE", "https://a.test", "", "", ""));

        String script = StepScriptBidirectionalSync.stepsToScript(steps, "https://a.test");

        assertEquals(1, script.lines().count(), "NAVIGATE step + startUrl must navigate exactly once");
    }

    @Test
    void waitAndEvaluateActionsProduceRealPlaywrightCalls() {
        TestStep wait = new TestStep(1, "WAIT_FOR_ELEMENT", "#toast", "", "", "");
        TestStep evaluate = new TestStep(2, "CUSTOM_JS", "", "window.scrollTo(0,0)", "", "");

        assertEquals("page.locator(\"#toast\").waitFor();", StepScriptBidirectionalSync.statement(wait));
        assertEquals("page.evaluate(\"window.scrollTo(0,0)\");", StepScriptBidirectionalSync.statement(evaluate));
    }

    @Test
    void recordedJavaClassEnvelopeSurvivesARegeneration() {
        String source = """
                package com.test;

                import com.microsoft.playwright.*;

                public class TC01 {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                        page.locator("#add").click();
                    }
                }
                """;

        StepScriptBidirectionalSync.Document document = StepScriptBidirectionalSync.parseDocument(source);
        assertTrue(document.isWrappedInClass());
        assertEquals(1, document.getSteps().size());

        document.getSteps().get(0).setTarget("#add-to-cart");
        String rendered = document.render(document.getSteps(), "");

        assertTrue(rendered.contains("package com.test;"), "The package declaration must be preserved");
        assertTrue(rendered.contains("import com.microsoft.playwright.*;"));
        assertTrue(rendered.contains("public class TC01"));
        assertTrue(rendered.contains("Page page = com.studio.runtime.StudioRuntime.page();"));
        assertTrue(rendered.contains("page.locator(\"#add-to-cart\").click();"));
        assertEquals(2, rendered.lines().filter(line -> line.strip().equals("}")).count(),
                "Both class and main closing braces must survive");
    }

    @Test
    void unmodelledStatementsAreKeptAsRawSteps() {
        String script = "page.getByRole(AriaRole.BUTTON).click();\n// ghi chú\npage.locator(\"#ok\").click();";

        List<TestStep> steps = StepScriptBidirectionalSync.scriptToSteps(script);

        assertEquals(3, steps.size());
        assertEquals(StepScriptBidirectionalSync.ACTION_RAW, steps.get(0).getAction());
        // A comment is a note, not a raw statement: it still emits a comment, but the card shows a
        // sentence instead of asking the tester to read Java.
        assertEquals(StepScriptBidirectionalSync.ACTION_NOTE, steps.get(1).getAction());
        assertEquals("ghi chú", steps.get(1).getTarget());
        assertEquals("CLICK", steps.get(2).getAction());

        String rendered = StepScriptBidirectionalSync.stepsToScript(steps, "");
        assertTrue(rendered.contains("page.getByRole(AriaRole.BUTTON).click();"),
                "Hand-written Playwright must not be deleted by a step edit");
        assertTrue(rendered.contains("// ghi chú"));
    }

    @Test
    void newerPlaywrightCallsBecomeRealStepsInsteadOfRawCode() {
        String script = """
                page.reload();
                page.locator("#agree").check();
                page.locator("#q").press("Enter");
                page.goBack();
                """;

        List<TestStep> steps = StepScriptBidirectionalSync.scriptToSteps(script);

        assertEquals(List.of("RELOAD", "CHECK", "PRESS_KEY", "GO_BACK"),
                steps.stream().map(TestStep::getAction).toList(),
                "A recorded statement the builder understands must not land in a raw code card");
        assertEquals("#agree", steps.get(1).getTarget());
        assertEquals("Enter", steps.get(2).getTestData());
        assertEquals(script, StepScriptBidirectionalSync.stepsToScript(steps, ""),
                "Re-modelling must still regenerate the same script");
    }

    @Test
    void aStatementWithOptionsKeepsItsOptionsVerbatim() {
        String line = "page.locator(\"#go\").click(new Page.ClickOptions().setForce(true));";

        List<TestStep> steps = StepScriptBidirectionalSync.scriptToSteps(line);

        assertEquals(StepScriptBidirectionalSync.ACTION_RAW, steps.get(0).getAction(),
                "The generator cannot re-emit setForce(true), so the line must stay verbatim");
        assertEquals(line + "\n", StepScriptBidirectionalSync.stepsToScript(steps, ""));
        // It stays readable even though it is not re-modelled.
        assertEquals("nhấp vào phần tử «#go»",
                StepAction.fromCode(steps.get(0).getAction())
                        .describe(steps.get(0).getTarget(), steps.get(0).getTestData()));
    }

    @Test
    void everyLineOfANoteIsCommentedAndIndented() {
        String script = """
                package demo;

                public class Recorded {
                    static void main(String[] args) {
                        // dòng 1
                    }
                }
                """;

        List<TestStep> steps = StepScriptBidirectionalSync.scriptToSteps(script);
        steps.get(0).setTarget("dòng 1\ndòng 2");

        String rendered = StepScriptBidirectionalSync.parseDocument(script).render(steps, "");

        assertTrue(rendered.contains("        // dòng 1\n        // dòng 2\n"),
                "Every line of a note must be commented and indented, was: " + rendered);
    }

    @Test
    void everyAssertActionRoundTripsThroughItsPlaywrightStatement() {
        List<TestStep> steps = List.of(
                new TestStep(1, "ASSERT_HIDDEN", ".spinner", "", "", ""),
                new TestStep(2, "ASSERT_VALUE", "input#email", "a@b.c", "", ""),
                new TestStep(3, "ASSERT_URL", "/dashboard", "", "", ""),
                new TestStep(4, "ASSERT_TITLE", "Hồ sơ", "", "", ""));

        String script = StepScriptBidirectionalSync.stepsToScript(steps, "");

        assertTrue(script.contains("assertThat(page.locator(\".spinner\")).isHidden();"));
        assertTrue(script.contains("assertThat(page.locator(\"input#email\")).hasValue(\"a@b.c\");"));
        assertTrue(script.contains("assertThat(page).hasURL(\"/dashboard\");"));
        assertTrue(script.contains("assertThat(page).hasTitle(\"Hồ sơ\");"));

        List<TestStep> parsed = StepScriptBidirectionalSync.scriptToSteps(script);

        assertEquals(List.of("ASSERT_HIDDEN", "ASSERT_VALUE", "ASSERT_URL", "ASSERT_TITLE"),
                parsed.stream().map(TestStep::getAction).toList(),
                "An assertion edited on the card must survive a script round trip");
        assertEquals(".spinner", parsed.get(0).getTarget());
        assertEquals("a@b.c", parsed.get(1).getTestData());
        assertEquals("/dashboard", parsed.get(2).getTarget());
        assertEquals("Hồ sơ", parsed.get(3).getTarget());
    }
}

