package com.studio.model;

/**
 * Supported export languages for Test Case Specification and Execution Reports.
 * Vietnamese (Tiếng Việt) is the primary default.
 */
public enum ExportLanguage {
    VIETNAMESE("vi", "Tiếng Việt", "Báo cáo và kịch bản bằng Tiếng Việt (Mặc định)"),
    ENGLISH("en", "English", "Report and Test Cases specification in English");

    private final String code;
    private final String displayName;
    private final String description;

    ExportLanguage(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVietnamese() {
        return this == VIETNAMESE;
    }

    public boolean isEnglish() {
        return this == ENGLISH;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static ExportLanguage fromCode(String code) {
        if (code == null || code.isBlank()) return VIETNAMESE;
        for (ExportLanguage l : values()) {
            if (l.code.equalsIgnoreCase(code.trim()) || l.name().equalsIgnoreCase(code.trim())) {
                return l;
            }
        }
        return VIETNAMESE;
    }
}

