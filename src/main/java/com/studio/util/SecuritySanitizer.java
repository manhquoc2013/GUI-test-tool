package com.studio.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log, terminal, and message sanitizer that strips / redacts API keys and auth tokens.
 */
public final class SecuritySanitizer {

    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("\\b(sk-[a-zA-Z0-9_-]{12,})\\b");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)([a-zA-Z0-9_\\-\\.\\:]{12,})");
    private static final Pattern QUERY_KEY_PATTERN = Pattern.compile("(?i)(key=)([a-zA-Z0-9_\\-]{16,})");

    private SecuritySanitizer() {}

    /**
     * Sanitizes known API key patterns (OpenAI, Anthropic, Bearer tokens, query param keys).
     */
    public static String sanitize(String message) {
        return sanitize(message, (String[]) null);
    }

    /**
     * Sanitizes patterns as well as any specific known secret strings.
     */
    public static String sanitize(String message, String... knownSecrets) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;

        // 1. Sanitize specific known secrets first if provided
        if (knownSecrets != null) {
            for (String secret : knownSecrets) {
                if (secret != null && secret.length() >= 6 && result.contains(secret)) {
                    result = result.replace(secret, ApiKeyCipher.mask(secret));
                }
            }
        }

        // 2. Sanitize Bearer tokens
        Matcher bearerMatcher = BEARER_PATTERN.matcher(result);
        if (bearerMatcher.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                String token = bearerMatcher.group(2);
                String masked = ApiKeyCipher.mask(token);
                bearerMatcher.appendReplacement(sb, Matcher.quoteReplacement(bearerMatcher.group(1) + masked));
            } while (bearerMatcher.find());
            bearerMatcher.appendTail(sb);
            result = sb.toString();
        }

        // 3. Sanitize OpenAI / Anthropic sk- pattern keys
        Matcher keyMatcher = OPENAI_KEY_PATTERN.matcher(result);
        if (keyMatcher.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                String key = keyMatcher.group(1);
                String masked = ApiKeyCipher.mask(key);
                keyMatcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
            } while (keyMatcher.find());
            keyMatcher.appendTail(sb);
            result = sb.toString();
        }

        // 4. Sanitize query params like ?key=...
        Matcher queryMatcher = QUERY_KEY_PATTERN.matcher(result);
        if (queryMatcher.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                String key = queryMatcher.group(2);
                String masked = ApiKeyCipher.mask(key);
                queryMatcher.appendReplacement(sb, Matcher.quoteReplacement(queryMatcher.group(1) + masked));
            } while (queryMatcher.find());
            queryMatcher.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }
}
