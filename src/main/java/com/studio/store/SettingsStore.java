package com.studio.store;

import com.studio.db.DatabaseManager;
import com.studio.model.AiConfig;
import com.studio.util.StudioPaths;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SettingsStore {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String SETTINGS_JSON = "settings.json";
    private static final String DB_SETTINGS_KEY = "app_settings";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        private String browser = "CHROMIUM";
        private boolean headlessDefault = false;
        private boolean shareContextDefault = true;
        private boolean stopOnFirstFailureDefault = false;
        private String theme = "dark";
        private AiConfig aiConfig = new AiConfig();

        public Settings() {
        }

        public String getBrowser() {
            return browser;
        }

        public void setBrowser(String browser) {
            this.browser = browser;
        }

        public boolean isHeadlessDefault() {
            return headlessDefault;
        }

        public void setHeadlessDefault(boolean headlessDefault) {
            this.headlessDefault = headlessDefault;
        }

        public boolean isShareContextDefault() {
            return shareContextDefault;
        }

        public void setShareContextDefault(boolean shareContextDefault) {
            this.shareContextDefault = shareContextDefault;
        }

        public boolean isStopOnFirstFailureDefault() {
            return stopOnFirstFailureDefault;
        }

        public void setStopOnFirstFailureDefault(boolean stopOnFirstFailureDefault) {
            this.stopOnFirstFailureDefault = stopOnFirstFailureDefault;
        }

        public String getTheme() {
            return theme != null ? theme : "dark";
        }

        public void setTheme(String theme) {
            this.theme = "light".equalsIgnoreCase(theme) ? "light" : "dark";
        }

        @JsonIgnore
        public boolean isDarkTheme() {
            return !"light".equalsIgnoreCase(theme);
        }

        public AiConfig getAiConfig() {
            if (aiConfig == null) {
                aiConfig = new AiConfig();
            }
            return aiConfig;
        }

        public void setAiConfig(AiConfig aiConfig) {
            this.aiConfig = aiConfig != null ? aiConfig : new AiConfig();
        }
    }

    public SettingsStore() {
    }

    /**
     * Load settings with Dual-Persistence fallback:
     * 1. Checks file `data/config/settings.json`.
     * 2. If missing or corrupted, recovers from H2 table `system_settings`.
     * 3. If both missing, initializes defaults and mirrors to both.
     */
    public Settings load() throws IOException {
        Path settingsFile = StudioPaths.getConfigDir().resolve(SETTINGS_JSON);

        // 1. Try reading from disk
        if (Files.exists(settingsFile)) {
            try {
                Settings s = mapper.readValue(settingsFile.toFile(), Settings.class);
                saveToDatabase(s); // Ensure DB mirror is up to date
                return s;
            } catch (Exception ignored) {
                // Disk file corrupted, proceed to DB fallback
            }
        }

        // 2. Try recovering from H2 Database
        Settings fromDb = loadFromDatabase();
        if (fromDb != null) {
            // Restore disk file from database mirror
            try {
                Files.createDirectories(StudioPaths.getConfigDir());
                mapper.writeValue(settingsFile.toFile(), fromDb);
            } catch (Exception ignored) {}
            return fromDb;
        }

        // 3. Fallback to default settings
        Settings defaults = new Settings();
        defaults.setBrowser("CHROMIUM");
        defaults.setHeadlessDefault(false);
        defaults.setShareContextDefault(true);
        defaults.setStopOnFirstFailureDefault(false);
        defaults.setTheme("dark");

        try {
            save(defaults);
        } catch (Exception ignored) {}

        return defaults;
    }

    /**
     * Save settings to both disk and embedded H2 database for absolute durability.
     */
    public void save(Settings s) throws IOException {
        if (s == null) return;

        // 1. Save to disk
        Files.createDirectories(StudioPaths.getConfigDir());
        Path settingsFile = StudioPaths.getConfigDir().resolve(SETTINGS_JSON);
        mapper.writeValue(settingsFile.toFile(), s);

        // 2. Mirror into H2 database
        saveToDatabase(s);
    }

    /**
     * Export current system settings to an external JSON file.
     */
    public void exportSettings(Path targetFile) throws IOException {
        if (targetFile == null) return;
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Settings current = load();
        mapper.writeValue(targetFile.toFile(), current);
    }

    /**
     * Import system settings from an external JSON file and persist.
     */
    public Settings importSettings(Path sourceFile) throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("Không tìm thấy file cấu hình: " + sourceFile);
        }
        Settings imported = mapper.readValue(sourceFile.toFile(), Settings.class);
        save(imported);
        return imported;
    }

    private void saveToDatabase(Settings s) {
        String sql = "MERGE INTO system_settings (setting_key, setting_value, updated_at) KEY(setting_key) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String json = mapper.writeValueAsString(s);
            ps.setString(1, DB_SETTINGS_KEY);
            ps.setString(2, json);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private Settings loadFromDatabase() {
        String sql = "SELECT setting_value FROM system_settings WHERE setting_key = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DB_SETTINGS_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("setting_value");
                    if (json != null && !json.isBlank()) {
                        return mapper.readValue(json, Settings.class);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
