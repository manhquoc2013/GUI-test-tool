package com.studio.store;

import com.studio.model.AiConfig;
import com.studio.util.StudioPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsStore {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String SETTINGS_JSON = "settings.json";

    public static class Settings {
        private String browser = "CHROMIUM";
        private boolean headlessDefault = false;
        private boolean shareContextDefault = true;
        private boolean stopOnFirstFailureDefault = false;
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

    public Settings load() throws IOException {
        Path settingsFile = StudioPaths.getConfigDir().resolve(SETTINGS_JSON);
        if (!Files.exists(settingsFile)) {
            Settings defaults = new Settings();
            defaults.setBrowser("CHROMIUM");
            defaults.setHeadlessDefault(false);
            defaults.setShareContextDefault(true);
            defaults.setStopOnFirstFailureDefault(false);
            return defaults;
        }
        return mapper.readValue(settingsFile.toFile(), Settings.class);
    }

    public void save(Settings s) throws IOException {
        Files.createDirectories(StudioPaths.getConfigDir());
        mapper.writeValue(StudioPaths.getConfigDir().resolve(SETTINGS_JSON).toFile(), s);
    }
}
