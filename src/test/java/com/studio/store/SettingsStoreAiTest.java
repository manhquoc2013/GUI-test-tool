package com.studio.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreAiTest {

    @TempDir
    Path tempDir;

    @Test
    void testSerializationWithAiConfig() throws IOException {
        SettingsStore.Settings settings = new SettingsStore.Settings();
        settings.getAiConfig().setApiKey("sk-custom-token");
        settings.getAiConfig().setModel("o3-mini");
        settings.getAiConfig().setBaseUrl("https://custom.openai.proxy/v1");

        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = tempDir.resolve("settings.json").toFile();
        mapper.writeValue(jsonFile, settings);

        SettingsStore.Settings loaded = mapper.readValue(jsonFile, SettingsStore.Settings.class);
        assertNotNull(loaded.getAiConfig());
        assertEquals("sk-custom-token", loaded.getAiConfig().getApiKey());
        assertEquals("o3-mini", loaded.getAiConfig().getModel());
        assertEquals("https://custom.openai.proxy/v1", loaded.getAiConfig().getBaseUrl());
    }
}
