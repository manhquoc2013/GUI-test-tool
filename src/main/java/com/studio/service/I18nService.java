package com.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.ExportLanguage;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized Internationalization (i18n) Service for Playwright Automation Studio.
 * Loads language manifests and localized translation resources dynamically from /i18n/.
 */
public final class I18nService {

    private static final String DEFAULT_LANG_CODE = "vi";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Properties> BUNDLES = new ConcurrentHashMap<>();
    private static volatile List<ExportLanguage> availableLanguages = null;

    private I18nService() {}

    /**
     * Retrieve all available export languages configured in /i18n/languages.json.
     * Falls back to [VIETNAMESE, ENGLISH] if the resource is unavailable.
     */
    public static List<ExportLanguage> getAvailableLanguages() {
        if (availableLanguages != null) {
            return availableLanguages;
        }
        synchronized (I18nService.class) {
            if (availableLanguages != null) {
                return availableLanguages;
            }
            availableLanguages = loadLanguagesFromResource();
            return availableLanguages;
        }
    }

    private static List<ExportLanguage> loadLanguagesFromResource() {
        try (InputStream is = I18nService.class.getResourceAsStream("/i18n/languages.json")) {
            if (is != null) {
                List<ExportLanguage> list = MAPPER.readValue(is, new TypeReference<List<ExportLanguage>>() {});
                if (list != null && !list.isEmpty()) {
                    return Collections.unmodifiableList(list);
                }
            }
        } catch (Exception e) {
            System.err.println("[I18nService] Could not load /i18n/languages.json: " + e.getMessage());
        }
        // Fallback
        return List.of(ExportLanguage.VIETNAMESE, ExportLanguage.ENGLISH);
    }

    public static ExportLanguage getDefaultLanguage() {
        for (ExportLanguage l : getAvailableLanguages()) {
            if (l.isDefault()) {
                return l;
            }
        }
        return ExportLanguage.VIETNAMESE;
    }

    /**
     * Resolves a localized message for the given language and translation key.
     */
    public static String get(ExportLanguage lang, String key, Object... args) {
        String code = (lang != null && lang.getCode() != null) ? lang.getCode() : DEFAULT_LANG_CODE;
        return get(code, key, args);
    }

    /**
     * Resolves a localized message by language code (e.g. "vi", "en", "ja").
     */
    public static String get(String langCode, String key, Object... args) {
        if (key == null) return "";
        String code = (langCode != null && !langCode.isBlank()) ? langCode.trim().toLowerCase() : DEFAULT_LANG_CODE;

        Properties props = getPropertiesFor(code);
        String val = props.getProperty(key);

        if (val == null && !DEFAULT_LANG_CODE.equals(code)) {
            // Fallback to default (Vietnamese)
            val = getPropertiesFor(DEFAULT_LANG_CODE).getProperty(key);
        }

        if (val == null) {
            return key; // return key if no translation found
        }

        if (args != null && args.length > 0) {
            try {
                return MessageFormat.format(val, args);
            } catch (Exception e) {
                return val;
            }
        }
        return val;
    }

    private static Properties getPropertiesFor(String code) {
        return BUNDLES.computeIfAbsent(code, c -> {
            Properties p = new Properties();
            String path = "/i18n/export_" + c + ".properties";
            try (InputStream is = I18nService.class.getResourceAsStream(path)) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        p.load(reader);
                    }
                } else if (DEFAULT_LANG_CODE.equals(c)) {
                    System.err.println("[I18nService] Missing translation resource for default locale: " + path);
                }
            } catch (Exception e) {
                System.err.println("[I18nService] Failed to load " + path + ": " + e.getMessage());
            }
            return p;
        });
    }

    /**
     * Clear caches (useful for dynamic reload and tests).
     */
    public static void reload() {
        synchronized (I18nService.class) {
            availableLanguages = null;
            BUNDLES.clear();
        }
    }
}
