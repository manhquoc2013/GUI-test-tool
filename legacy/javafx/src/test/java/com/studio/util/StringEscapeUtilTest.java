package com.studio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringEscapeUtilTest {

    @Test
    void testSafeFileNamePreservesNormalNamesAndSpaces() {
        assertEquals("Login Case 1", StringEscapeUtil.safeFileName("Login Case 1"));
        assertEquals("test-case_ok", StringEscapeUtil.safeFileName("test-case_ok"));
    }

    @Test
    void testSafeFileNameStripsForbiddenAndTraversalChars() {
        assertEquals("Login _ Logout", StringEscapeUtil.safeFileName("Login / Logout"));
        assertEquals("C__test_run", StringEscapeUtil.safeFileName("C:\\test:run"));
        assertEquals("what_ _special_", StringEscapeUtil.safeFileName("what? *special*"));
        assertEquals("____secret_file", StringEscapeUtil.safeFileName("../../secret/file"));
    }

    @Test
    void testSafeFileNameFallback() {
        assertEquals("case_fallback", StringEscapeUtil.safeFileName(null, "case_fallback"));
        assertEquals("case_fallback", StringEscapeUtil.safeFileName("", "case_fallback"));
        assertEquals("case_fallback", StringEscapeUtil.safeFileName("   ", "case_fallback"));
    }

    @Test
    void testSanitizeJavaIdentifier() {
        assertEquals("LoginCase", StringEscapeUtil.sanitizeJavaIdentifier("LoginCase", "HarnessRun"));
        assertEquals("case_1", StringEscapeUtil.sanitizeJavaIdentifier("case 1", "HarnessRun"));
        // Java keywords must be prefixed with C_
        assertEquals("C_case", StringEscapeUtil.sanitizeJavaIdentifier("case", "HarnessRun"));
        assertEquals("C_switch", StringEscapeUtil.sanitizeJavaIdentifier("switch", "HarnessRun"));
        // Numbers at start must be prefixed
        assertEquals("C_123Test", StringEscapeUtil.sanitizeJavaIdentifier("123Test", "HarnessRun"));
    }

    @Test
    void testEscapeJavaAndJson() {
        assertEquals("hello\\\"world\\n", StringEscapeUtil.escapeJava("hello\"world\n"));
        assertEquals("hello\\\"world\\n", StringEscapeUtil.escapeJson("hello\"world\n"));
        assertEquals("", StringEscapeUtil.escapeJava(null));
        assertEquals("", StringEscapeUtil.escapeJson(null));
    }
}
