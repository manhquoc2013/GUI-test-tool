package com.studio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecuritySanitizerTest {

    @Test
    void testSanitizeOpenAiKeyPattern() {
        String log = "Connecting to OpenAI with key sk-proj-1234567890abcdefghijKLMN at endpoint";
        String sanitized = SecuritySanitizer.sanitize(log);

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("1234567890abcdefghij"), "Sanitized log must not contain raw secret key");
        assertTrue(sanitized.contains("sk-"), "Prefix sk- should remain");
        assertTrue(sanitized.contains("KLMN"), "Last 4 characters can remain for debugging");
        assertTrue(sanitized.contains("••••"), "Secret portion must be masked with bullets");
    }

    @Test
    void testSanitizeBearerHeaderPattern() {
        String log = "Authorization: Bearer sk-ant-api03-abcdef1234567890abcdef123456";
        String sanitized = SecuritySanitizer.sanitize(log);

        assertFalse(sanitized.contains("abcdef1234567890"), "Bearer token must be sanitized");
        assertTrue(sanitized.contains("Bearer"));
        assertTrue(sanitized.contains("••••"));
    }

    @Test
    void testSanitizeKnownCustomSecret() {
        String customKey = "custom_super_secret_groq_key_999888";
        String log = "Error 401: Unauthorized request with key custom_super_secret_groq_key_999888 failed.";

        String sanitized = SecuritySanitizer.sanitize(log, customKey);
        assertFalse(sanitized.contains(customKey), "Known secret must be masked even if not matching sk- pattern");
        assertTrue(sanitized.contains("••••"));
    }

    @Test
    void testNullAndEmptySafe() {
        assertNull(SecuritySanitizer.sanitize(null));
        assertEquals("", SecuritySanitizer.sanitize(""));
        assertEquals("Normal log with no secrets", SecuritySanitizer.sanitize("Normal log with no secrets"));
    }
}

