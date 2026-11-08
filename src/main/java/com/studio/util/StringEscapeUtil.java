package com.studio.util;

public class StringEscapeUtil {
    public static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Sanitizes a string to be safely used as a filename on all operating systems (Windows, macOS, Linux).
     * Strips path traversal sequences (..) and forbidden filesystem characters (\, /, :, *, ?, ", <, >, |).
     */
    public static String safeFileName(String name) {
        return safeFileName(name, "file");
    }

    public static String safeFileName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return (fallback != null && !fallback.isBlank()) ? fallback : "file";
        }
        String clean = name.trim().replace("..", "_");
        String safe = clean.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safe.isBlank()) {
            return (fallback != null && !fallback.isBlank()) ? fallback : "file";
        }
        return safe;
    }

    /**
     * Sanitizes a string into a valid Java class identifier.
     */
    public static String sanitizeJavaIdentifier(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return (fallback != null && !fallback.isBlank()) ? fallback : "HarnessRun";
        }
        String safe = name.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_");
        if (safe.isBlank() || "_".equals(safe)) {
            return (fallback != null && !fallback.isBlank()) ? fallback : "HarnessRun";
        }
        if (!Character.isJavaIdentifierStart(safe.charAt(0)) || javax.lang.model.SourceVersion.isKeyword(safe)) {
            safe = "C_" + safe;
        }
        return safe;
    }
}

