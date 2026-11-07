package com.studio.util;

import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;

/**
 * Icons + CSS style classes for lifecycle and run statuses.
 * Icons chosen for contrast on dark Tokyo Night backgrounds.
 */
public final class StatusDisplay {

    public static final String ALL_LABEL = "Tất cả";

    private static final String[] ALL_STYLE_CLASSES = {
            "status-active", "status-draft", "status-inactive", "status-ready",
            "status-passed", "status-failed", "status-skipped", "status-error", "status-pending",
            "status-running", "status-unknown"
    };

    private StatusDisplay() {}

    public static boolean isAll(String value) {
        if (value == null || value.isBlank()) return true;
        String v = value.trim();
        // Canonical run-scope codes must not count as the "show everything" sentinel
        if ("ALL".equals(v) || "GROUP".equals(v) || "SUITE".equals(v) || "CASE".equals(v)) {
            return false;
        }
        return "Tất cả".equals(v) || ALL_LABEL.equals(v) || v.contains("Tất cả");
    }

    public static String normalize(String status) {
        if (status == null || status.isBlank()) return "";
        return status.trim().toUpperCase();
    }

    public static String icon(String status) {
        return switch (normalize(status)) {
            case "ACTIVE" -> StudioIcons.STATUS_ACTIVE;
            case "READY" -> StudioIcons.STATUS_READY;
            case "DRAFT" -> StudioIcons.STATUS_DRAFT;
            case "INACTIVE" -> StudioIcons.STATUS_INACTIVE;
            case "PASSED", "PASS", "OK" -> StudioIcons.STATUS_PASSED;
            case "FAILED", "FAIL" -> StudioIcons.STATUS_FAILED;
            case "SKIPPED", "SKIP" -> StudioIcons.STATUS_SKIPPED;
            case "ERROR" -> StudioIcons.STATUS_ERROR;
            case "PENDING" -> StudioIcons.STATUS_PENDING;
            case "RUNNING" -> StudioIcons.STATUS_RUNNING;
            default -> "•";
        };
    }

    public static String text(String status) {
        return switch (normalize(status)) {
            case "ACTIVE" -> "Đang hoạt động";
            case "READY" -> "Sẵn sàng";
            case "DRAFT" -> "Bản nháp";
            case "INACTIVE" -> "Không hoạt động";
            case "PASSED", "PASS", "OK" -> "Đạt";
            case "FAILED", "FAIL" -> "Thất bại";
            case "SKIPPED", "SKIP" -> "Bỏ qua";
            case "ERROR" -> "Lỗi";
            case "PENDING" -> "Chờ chạy";
            case "RUNNING" -> "Đang chạy";
            case "" -> "—";
            default -> status.trim();
        };
    }

    public static String label(String status) {
        String key = normalize(status);
        if (key.isEmpty()) return "• —";
        return icon(status) + " " + text(status);
    }

    public static String styleClass(String status) {
        return switch (normalize(status)) {
            case "ACTIVE" -> "status-active";
            case "READY" -> "status-ready";
            case "DRAFT" -> "status-draft";
            case "INACTIVE" -> "status-inactive";
            case "PASSED", "PASS", "OK" -> "status-passed";
            case "FAILED", "FAIL" -> "status-failed";
            case "SKIPPED", "SKIP" -> "status-skipped";
            case "ERROR" -> "status-error";
            case "PENDING" -> "status-pending";
            case "RUNNING" -> "status-running";
            default -> "status-unknown";
        };
    }

    /** Suite must be Active before any run actions. */
    public static boolean isActive(String status) {
        return "ACTIVE".equals(normalize(status));
    }

    /** Case can be run when Ready/Active and parent suite is Active. */
    public static boolean canRunCase(String suiteStatus, String caseStatus) {
        return isActive(suiteStatus) && isRunnable(caseStatus);
    }

    /** Case Ready or Active (ignores suite gate). */
    public static boolean isRunnable(String status) {
        String key = normalize(status);
        return "READY".equals(key) || "ACTIVE".equals(key);
    }

    public static void apply(Labeled node, String status) {
        if (node == null) return;
        node.getStyleClass().removeAll(ALL_STYLE_CLASSES);
        if (status == null || status.isBlank()) {
            node.setText("");
            return;
        }
        node.setText(label(status));
        node.getStyleClass().add(styleClass(status));
    }

    public static void apply(TableCell<?, String> cell, String status) {
        if (cell == null) return;
        cell.getStyleClass().removeAll(ALL_STYLE_CLASSES);
        if (status == null || status.isBlank()) {
            cell.setText(null);
            return;
        }
        cell.setText(label(status));
        cell.getStyleClass().add(styleClass(status));
    }

    public static String treeBadge(String status) {
        String key = normalize(status);
        if (key.isEmpty()) return "";
        return icon(status);
    }
}
