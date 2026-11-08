package com.studio.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiConfigTest {

    @Test
    void testDefaults() {
        AiConfig config = new AiConfig();
        assertEquals(AiConfig.DEFAULT_BASE_URL, config.getBaseUrl());
        assertEquals("gpt-4o", config.getModel());
        assertEquals(0.2, config.getTemperature());
        assertEquals(0, config.getMaxTokens());
        assertEquals("", config.getApiKey());
    }

    @Test
    void testZeroMaxTokensMeansUnlimited() {
        AiConfig config = new AiConfig();
        config.setMaxTokens(0);
        assertEquals(0, config.getMaxTokens());

        config.setMaxTokens(-10);
        assertEquals(0, config.getMaxTokens());
    }

    @Test
    void testCustomConfiguration() {
        AiConfig config = new AiConfig();
        config.setApiKey("sk-test-12345");
        config.setBaseUrl("https://api.openai.com/v1/");
        config.setModel("gpt-4o-mini");
        config.setTemperature(0.7);
        config.setMaxTokens(2048);

        assertEquals("sk-test-12345", config.getApiKey());
        assertEquals("https://api.openai.com/v1", config.getBaseUrl()); // trailing slash stripped
        assertEquals("gpt-4o-mini", config.getModel());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
        assertTrue(config.hasApiKey());
        assertEquals("sk-test-12345", config.getEffectiveApiKey());
        assertEquals("https://api.openai.com/v1", config.getEffectiveBaseUrl());
        assertEquals("gpt-4o-mini", config.getEffectiveModel());
    }

    @Test
    void testTemperatureClamping() {
        AiConfig config = new AiConfig();
        config.setTemperature(-1.0);
        assertEquals(0.0, config.getTemperature());

        config.setTemperature(3.5);
        assertEquals(2.0, config.getTemperature());
    }
}

