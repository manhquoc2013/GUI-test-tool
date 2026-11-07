package com.studio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.studio.service.I18nService;

import java.util.List;
import java.util.Objects;

/**
 * Model representing an export language for Test Case Specification and Execution Reports.
 * Loaded dynamically from resources (i18n/languages.json) with Vietnamese (Tiếng Việt) as primary default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportLanguage {

    public static final ExportLanguage VIETNAMESE = new ExportLanguage(
            "vi", "Tiếng Việt (Mặc định)", "Báo cáo và kịch bản hiển thị tiêu đề, trạng thái và tổng hợp bằng Tiếng Việt.", true);

    public static final ExportLanguage ENGLISH = new ExportLanguage(
            "en", "English (Tiếng Anh)", "Report and test cases formatted with standard English headers and ISTQB/IEEE terms.", false);

    private final String code;
    private final String displayName;
    private final String description;
    private final boolean isDefault;

    public ExportLanguage() {
        this("vi", "Tiếng Việt", "", true);
    }

    public ExportLanguage(String code, String displayName, String description) {
        this(code, displayName, description, false);
    }

    @JsonCreator
    public ExportLanguage(
            @JsonProperty("code") String code,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("description") String description,
            @JsonProperty("isDefault") boolean isDefault) {
        this.code = code != null ? code.trim().toLowerCase() : "vi";
        this.displayName = displayName != null ? displayName : this.code;
        this.description = description != null ? description : "";
        this.isDefault = isDefault;
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

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isVietnamese() {
        return "vi".equalsIgnoreCase(code);
    }

    public boolean isEnglish() {
        return "en".equalsIgnoreCase(code);
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportLanguage that = (ExportLanguage) o;
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    public static ExportLanguage[] values() {
        List<ExportLanguage> langs = I18nService.getAvailableLanguages();
        return langs.toArray(new ExportLanguage[0]);
    }

    public static List<ExportLanguage> getAll() {
        return I18nService.getAvailableLanguages();
    }

    public static ExportLanguage fromCode(String code) {
        if (code == null || code.isBlank()) return VIETNAMESE;
        String clean = code.trim().toLowerCase();
        for (ExportLanguage l : I18nService.getAvailableLanguages()) {
            if (l.getCode().equalsIgnoreCase(clean)) {
                return l;
            }
        }
        if (ENGLISH.getCode().equalsIgnoreCase(clean)) {
            return ENGLISH;
        }
        return VIETNAMESE;
    }
}
