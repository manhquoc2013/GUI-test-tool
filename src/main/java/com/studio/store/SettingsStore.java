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

    private final Path configDir;

    public SettingsStore() {
        this(StudioPaths.getConfigDir());
    }

    public SettingsStore(Path configDir) {
        this.configDir = configDir != null ? configDir : StudioPaths.getConfigDir();
    }

    /**
     * Load settings with Dual-Persistence fallback:
     * 1. Checks file `data/config/settings.json`.
     * 2. If missing or corrupted, recovers from H2 table `system_settings`.
     * 3. If both missing, initializes defaults and mirrors to both.
     */
    public Settings load() throws IOException {
        Path settingsFile = configDir.resolve(SETTINGS_JSON);

        // 1. Try reading from disk
        if (Files.exists(settingsFile)) {
            try {
                Settings s = mapper.readValue(settingsFile.toFile(), Settings.class);
                decryptInMemory(s);
                saveToDatabase(prepareForStorage(s)); // Ensure DB mirror is up to date
                return s;
            } catch (Exception ignored) {
                // Disk file corrupted, proceed to DB fallback
            }
        }

        // 2. Try recovering from H2 Database
        Settings fromDb = loadFromDatabase();
        if (fromDb != null) {
            decryptInMemory(fromDb);
            // Restore disk file from database mirror
            try {
                Files.createDirectories(configDir);
                mapper.writeValue(settingsFile.toFile(), prepareForStorage(fromDb));
            } catch (Exception e) {
                System.err.println("[SettingsStore] Không thể khôi phục file settings từ DB: " + e.getMessage());
            }
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
        } catch (Exception e) {
            System.err.println("[SettingsStore] Không thể lưu cài đặt mặc định: " + e.getMessage());
        }

        return defaults;
    }

    /**
     * Save settings to both disk and embedded H2 database for absolute durability.
     */
    public void save(Settings s) throws IOException {
        if (s == null)
            return;

        Settings persistent = prepareForStorage(s);

        // 1. Save to disk
        Files.createDirectories(configDir);
        Path settingsFile = configDir.resolve(SETTINGS_JSON);
        mapper.writeValue(settingsFile.toFile(), persistent);

        // 2. Mirror into H2 database
        saveToDatabase(persistent);
    }

    /**
     * Export current system settings to an external JSON file with optional API key
     * masking.
     */
    public void exportSettings(Path targetFile) throws IOException {
        exportSettings(targetFile, true);
    }

    public void exportSettings(Path targetFile, boolean maskApiKey) throws IOException {
        if (targetFile == null)
            return;
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Settings current = load();
        if (maskApiKey && current.getAiConfig() != null) {
            String plainKey = current.getAiConfig().getApiKey();
            current.getAiConfig().setApiKey(com.studio.util.ApiKeyCipher.mask(plainKey));
        }
        mapper.writeValue(targetFile.toFile(), current);
    }

    /**
     * Import system settings from an external JSON file and persist.
     * If the imported file contains a masked key placeholder, local credentials are
     * preserved.
     */
    public Settings importSettings(Path sourceFile) throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("Không tìm thấy file cấu hình: " + sourceFile);
        }
        Settings imported = mapper.readValue(sourceFile.toFile(), Settings.class);
        decryptInMemory(imported);

        if (imported.getAiConfig() != null) {
            String importedKey = imported.getAiConfig().getApiKey();
            if (com.studio.util.ApiKeyCipher.isMasked(importedKey)) {
                Settings existing = load();
                if (existing != null && existing.getAiConfig() != null
                        && existing.getAiConfig().getApiKey() != null
                        && !existing.getAiConfig().getApiKey().isBlank()) {
                    imported.getAiConfig().setApiKey(existing.getAiConfig().getApiKey());
                } else {
                    imported.getAiConfig().setApiKey("");
                }
            }
        }

        save(imported);
        return imported;
    }

    private Settings prepareForStorage(Settings source) {
        if (source == null)
            return null;
        Settings copy = new Settings();
        copy.setBrowser(source.getBrowser());
        copy.setHeadlessDefault(source.isHeadlessDefault());
        copy.setShareContextDefault(source.isShareContextDefault());
        copy.setStopOnFirstFailureDefault(source.isStopOnFirstFailureDefault());
        copy.setTheme(source.getTheme());
        if (source.getAiConfig() != null) {
            AiConfig srcAi = source.getAiConfig();
            String plainKey = srcAi.getApiKey();
            String encKey = (plainKey != null && !plainKey.isBlank())
                    ? com.studio.util.ApiKeyCipher.encrypt(plainKey)
                    : "";
            AiConfig copyAi = new AiConfig(encKey, srcAi.getBaseUrl(), srcAi.getModel());
            copyAi.setTemperature(srcAi.getTemperature());
            copyAi.setMaxTokens(srcAi.getMaxTokens());
            copy.setAiConfig(copyAi);
        }
        return copy;
    }

    private void decryptInMemory(Settings s) {
        if (s != null && s.getAiConfig() != null) {
            String rawKey = s.getAiConfig().getApiKey();
            if (rawKey != null && !rawKey.isBlank()) {
                String decrypted = com.studio.util.ApiKeyCipher.decrypt(rawKey);
                if (decrypted.isEmpty() && com.studio.util.ApiKeyCipher.isEncrypted(rawKey)) {
                    System.err.println("[SettingsStore] Cảnh báo: Không thể giải mã API key đã lưu trữ (sai máy hoặc dữ liệu bị thay đổi).");
                } else {
                    s.getAiConfig().setApiKey(decrypted);
                }
            }
        }
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
        } catch (Exception e) {
            System.err.println("[SettingsStore] Không thể lưu cài đặt vào database: " + e.getMessage());
        }
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
        } catch (Exception e) {
            System.err.println("[SettingsStore] Không thể đọc cài đặt từ database: " + e.getMessage());
        }
        return null;
    }
}
