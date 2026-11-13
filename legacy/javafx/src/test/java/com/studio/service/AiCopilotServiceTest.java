package com.studio.service;

import com.studio.model.TestStep;
import com.studio.model.TestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCopilotServiceTest {

    private TestCase sampleCase() {
        TestCase testCase = new TestCase();
        testCase.setName("Đăng nhập");
        testCase.setStartUrl("https://example.com/login");
        return testCase;
    }

    @Test
    void selectorPromptCarriesStepContextAndDom() {
        TestStep step = new TestStep(2, "CLICK", "button.login", "", "", "");
        String prompt = AiCopilotService.buildSelectorFixPrompt(
                sampleCase(), step, "[{\"tag\":\"button\",\"text\":\"Đăng nhập\"}]", "nút nằm trong form");

        assertTrue(prompt.contains("button.login"));
        assertTrue(prompt.contains("Đăng nhập"));
        assertTrue(prompt.contains("nút nằm trong form"));
        assertTrue(prompt.contains("Chỉ trả về DUY NHẤT một selector"));
    }

    @Test
    void assertionPromptAsksForSinglePlaywrightLine() {
        TestStep step = new TestStep(1, "CLICK", "#save", "", "", "");
        String prompt = AiCopilotService.buildAssertionPrompt(
                sampleCase(), step, "page.locator(\"#save\").click();", "[]", "");

        assertTrue(prompt.contains("assertThat(page.locator"));
        assertTrue(prompt.contains("MỘT assertion"));
        assertTrue(prompt.contains("page.locator(\"#save\").click();"));
    }

    @Test
    void trimDomCapsContextAndIgnoresEmptySnapshot() {
        assertEquals("", AiCopilotService.trimDom(null));
        assertEquals("", AiCopilotService.trimDom("[]"));

        String huge = "x".repeat(AiCopilotService.MAX_DOM_CONTEXT_CHARS + 500);
        assertEquals(AiCopilotService.MAX_DOM_CONTEXT_CHARS, AiCopilotService.trimDom(huge).length());
    }

    @Test
    void cleanModelOutputUnwrapsFencedBlocks() {
        String raw = "```java\nassertThat(page.locator(\".ok\")).isVisible();\n```";
        assertEquals("assertThat(page.locator(\".ok\")).isVisible();", AiCopilotService.cleanModelOutput(raw));
    }

    @Test
    void extractSelectorPrefersLocatorCall() {
        String raw = "Selector: page.locator(\"#submit-btn\")";
        assertEquals("#submit-btn", AiCopilotService.extractSelector(raw));
    }

    @Test
    void extractSelectorStripsLabelsQuotesAndSemicolons() {
        assertEquals(".card > .title", AiCopilotService.extractSelector("Selector: `.card > .title`;"));
        assertEquals("text=Đăng nhập", AiCopilotService.extractSelector("text=Đăng nhập\nGiải thích..."));
    }

    @Test
    void extractSelectorRejectsUrlsAndEmptyReplies() {
        assertNull(AiCopilotService.extractSelector("https://example.com/login"));
        assertNull(AiCopilotService.extractSelector("   "));
        assertNull(AiCopilotService.extractSelector(null));
        assertNull(AiCopilotService.extractSelector("x".repeat(AiCopilotService.MAX_SELECTOR_CHARS + 1)));
    }

    @Test
    void asMessagesSendsSystemContractThenPrompt() {
        var messages = AiCopilotService.asMessages("Xin chào");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("user", messages.get(1).getRole());
        assertEquals("Xin chào", messages.get(1).getTextContent());
        assertNotNull(AiCopilotService.SYSTEM_PROMPT);
        assertFalse(AiCopilotService.SYSTEM_PROMPT.isBlank());
    }
}
