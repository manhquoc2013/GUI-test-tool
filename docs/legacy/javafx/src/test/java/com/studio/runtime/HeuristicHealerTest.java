package com.studio.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicHealerTest {

    @Test
    @DisplayName("Dynamic ID should generate stripped prefix candidates")
    void testDynamicIdStripping() {
        List<String> candidates = HeuristicHealer.generateCandidates("#btn-login-948291");
        assertNotNull(candidates);
        assertTrue(candidates.contains("[id^='btn-login']"));
        assertTrue(candidates.contains("[id*='btn-login']"));
    }

    @Test
    @DisplayName("Text and has-text patterns generate button and role candidates")
    void testTextPatterns() {
        List<String> candidates = HeuristicHealer.generateCandidates("text='Đăng nhập'");
        assertNotNull(candidates);
        assertTrue(candidates.contains("button:has-text(\"Đăng nhập\")"));
        assertTrue(candidates.contains("button:has-text('Đăng nhập')"));
        assertTrue(candidates.contains("a:has-text(\"Đăng nhập\")"));
        assertTrue(candidates.contains("[role='button']:has-text(\"Đăng nhập\")"));
    }

    @Test
    @DisplayName("Name attribute generates testid and aria-label fallbacks")
    void testNameAttributePatterns() {
        List<String> candidates = HeuristicHealer.generateCandidates("input[name='username']");
        assertNotNull(candidates);
        assertTrue(candidates.contains("[data-testid='username']"));
        assertTrue(candidates.contains("[aria-label='username']"));
    }

    @Test
    @DisplayName("CSS class hash generates cleaned class candidate")
    void testClassHashCleanup() {
        List<String> candidates = HeuristicHealer.generateCandidates(".login-btn__3a8f");
        assertNotNull(candidates);
        assertTrue(candidates.contains(".login-btn"));
    }

    @Test
    @DisplayName("Text with single quotes is safely escaped in candidates")
    void testTextWithSingleQuotes() {
        List<String> candidates = HeuristicHealer.generateCandidates("text=\"Men's Shoes\"");
        assertNotNull(candidates);
        assertTrue(candidates.contains("button:has-text(\"Men's Shoes\")"));
        assertTrue(candidates.contains("button:has-text('Men\\'s Shoes')"));
    }
}
