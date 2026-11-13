package com.studio.service;

import com.studio.model.ExportLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class I18nServiceTest {

    @BeforeEach
    void setUp() {
        I18nService.reload();
    }

    @Test
    void testGetAvailableLanguages() {
        List<ExportLanguage> languages = I18nService.getAvailableLanguages();
        assertNotNull(languages);
        assertFalse(languages.isEmpty(), "Available languages must not be empty");

        boolean hasVi = languages.stream().anyMatch(l -> "vi".equalsIgnoreCase(l.getCode()));
        boolean hasEn = languages.stream().anyMatch(l -> "en".equalsIgnoreCase(l.getCode()));
        assertTrue(hasVi, "Tiếng Việt ('vi') must be configured");
        assertTrue(hasEn, "English ('en') must be configured");

        ExportLanguage def = I18nService.getDefaultLanguage();
        assertNotNull(def);
        assertEquals("vi", def.getCode(), "Default language must be Vietnamese");
    }

    @Test
    void testVietnameseTranslations() {
        ExportLanguage vi = ExportLanguage.VIETNAMESE;
        assertEquals("Tổng quan", I18nService.get(vi, "export.tc.sheet.overview"));
        assertEquals("Danh sách Test Cases", I18nService.get(vi, "export.tc.sheet.cases"));
        assertEquals("Chi tiết các bước", I18nService.get(vi, "export.tc.sheet.steps"));
        assertEquals("Có", I18nService.get(vi, "export.common.yes"));
        assertEquals("Không", I18nService.get(vi, "export.common.no"));
    }

    @Test
    void testEnglishTranslations() {
        ExportLanguage en = ExportLanguage.ENGLISH;
        assertEquals("Overview", I18nService.get(en, "export.tc.sheet.overview"));
        assertEquals("Test Cases", I18nService.get(en, "export.tc.sheet.cases"));
        assertEquals("Step Details", I18nService.get(en, "export.tc.sheet.steps"));
        assertEquals("Yes", I18nService.get(en, "export.common.yes"));
        assertEquals("No", I18nService.get(en, "export.common.no"));
    }

    @Test
    void testMessageFormattingWithArguments() {
        ExportLanguage vi = ExportLanguage.VIETNAMESE;
        String formattedVi = I18nService.get(vi, "export.common.scope.suite", "LoginSuite");
        assertEquals("BỘ KIỂM THỬ: LoginSuite", formattedVi);

        ExportLanguage en = ExportLanguage.ENGLISH;
        String formattedEn = I18nService.get(en, "export.common.scope.suite", "LoginSuite");
        assertEquals("TEST SUITE: LoginSuite", formattedEn);
    }

    @Test
    void testNullLanguageFallsBackToDefault() {
        String val = I18nService.get((ExportLanguage) null, "export.common.yes");
        assertEquals("Có", val, "Null language should fall back to default (Vietnamese)");
    }

    @Test
    void testMissingKeyReturnsKeyOrFallback() {
        ExportLanguage vi = ExportLanguage.VIETNAMESE;
        String missing = I18nService.get(vi, "non.existent.key.xyz");
        assertEquals("non.existent.key.xyz", missing, "Unknown key should return the key itself");
    }

    @Test
    void testExtensibleCustomLanguage() {
        // Create a new dynamic language (e.g. Japanese)
        ExportLanguage ja = new ExportLanguage("ja", "日本語 (Japanese)", "QA レポートおよびテストケースのエクスポート", false);
        assertEquals("ja", ja.getCode());
        assertFalse(ja.isVietnamese());
        assertFalse(ja.isEnglish());

        // Since ja bundle doesn't exist yet, it should cleanly fall back to Vietnamese default values
        String fallbackValue = I18nService.get(ja, "export.tc.sheet.overview");
        assertEquals("Tổng quan", fallbackValue, "Custom language without dedicated resource should gracefully fall back to default");

        String formatted = I18nService.get(ja, "export.common.scope.suite", "OrderSuite");
        assertEquals("BỘ KIỂM THỬ: OrderSuite", formatted);
    }

    @Test
    void testExportLanguageHelperMethods() {
        ExportLanguage vi = ExportLanguage.fromCode("vi");
        assertNotNull(vi);
        assertTrue(vi.isVietnamese());
        assertFalse(vi.isEnglish());

        ExportLanguage en = ExportLanguage.fromCode("EN");
        assertNotNull(en);
        assertTrue(en.isEnglish());
        assertFalse(en.isVietnamese());

        ExportLanguage unknown = ExportLanguage.fromCode("unknown");
        assertEquals("vi", unknown.getCode(), "Unknown code should fall back to default language");

        ExportLanguage[] all = ExportLanguage.values();
        assertTrue(all.length >= 2);
    }
}
