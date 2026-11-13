package com.studio.model;

import java.util.List;

/**
 * Standard Test Priority levels (P0 - P3) based on risk matrix and business impact.
 */
public final class TestPriority {

    public static final String P0 = "P0"; // Critical / Smoke: Auth, Payment, Core Blocker
    public static final String P1 = "P1"; // High / Core Flow: Essential business workflows & CRUD
    public static final String P2 = "P2"; // Medium / Regression: Secondary flows, filters, pagination
    public static final String P3 = "P3"; // Low / Edge Case: UI polish, tooltips, rare edge cases

    public static final List<String> ALL = List.of(P0, P1, P2, P3);

    private TestPriority() {}

    /**
     * Normalizes various priority expressions to standard P0, P1, P2, P3.
     * Defaults to P1 if null, empty, or unrecognized.
     */
    public static String normalize(String priority) {
        if (priority == null || priority.isBlank()) {
            return P1;
        }
        String p = priority.trim().toUpperCase();
        if ("P0".equals(p) || "CRITICAL".equals(p) || "SMOKE".equals(p) || "BLOCKER".equals(p)) {
            return P0;
        }
        if ("P1".equals(p) || "HIGH".equals(p) || "CORE".equals(p) || "MAJOR".equals(p)) {
            return P1;
        }
        if ("P2".equals(p) || "MEDIUM".equals(p) || "NORMAL".equals(p) || "REGRESSION".equals(p)) {
            return P2;
        }
        if ("P3".equals(p) || "LOW".equals(p) || "MINOR".equals(p) || "EDGE".equals(p) || "TRIVIAL".equals(p)) {
            return P3;
        }
        return P1;
    }

    public static String getLabel(String priority) {
        String p = normalize(priority);
        return switch (p) {
            case P0 -> "P0 - Critical / Smoke";
            case P1 -> "P1 - High / Core Flow";
            case P2 -> "P2 - Medium / Regression";
            case P3 -> "P3 - Low / Edge Case";
            default -> "P1 - High / Core Flow";
        };
    }

    public static String getBadgeColor(String priority) {
        String p = normalize(priority);
        return switch (p) {
            case P0 -> "#f38ba8"; // Crimson Red
            case P1 -> "#fab387"; // Vibrant Peach/Orange
            case P2 -> "#89b4fa"; // Sky Blue
            case P3 -> "#a6adc8"; // Slate Gray
            default -> "#fab387";
        };
    }

    public static boolean isValid(String priority) {
        if (priority == null) return false;
        String p = priority.trim().toUpperCase();
        return ALL.contains(p);
    }
}

