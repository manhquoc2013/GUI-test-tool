package com.studio.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The output ceiling used to be one hard-coded 16384 for every endpoint: too high for a small local
 * model (which rejects the request) and too low for a large-output model (which gets cut). These tests
 * pin the per-model resolution and the manual override.
 */
class AiModelCapabilitiesTest {

    private static AiConfig configFor(String model) {
        AiConfig config = new AiConfig();
        config.setModel(model);
        return config;
    }

    @Test
    void knownModelsReportTheirOwnOutputLimit() {
        assertEquals(16384, AiModelCapabilities.outputCeilingForModel("gpt-4o"));
        assertEquals(16384, AiModelCapabilities.outputCeilingForModel("gpt-4o-mini"));
        assertEquals(32768, AiModelCapabilities.outputCeilingForModel("gpt-4.1"),
                "gpt-4.1 must not fall through to the gpt-4 entry");
        assertEquals(32768, AiModelCapabilities.outputCeilingForModel("o3-mini"));
        assertEquals(8192, AiModelCapabilities.outputCeilingForModel("deepseek-chat"));
        assertEquals(4096, AiModelCapabilities.outputCeilingForModel("gpt-3.5-turbo"));
    }

    @Test
    void unknownOrBlankModelKeepsTheHistoricalDefault() {
        assertEquals(AiModelCapabilities.DEFAULT_OUTPUT_CEILING,
                AiModelCapabilities.outputCeilingForModel("my-private-endpoint"));
        assertEquals(AiModelCapabilities.DEFAULT_OUTPUT_CEILING,
                AiModelCapabilities.outputCeilingForModel("  "));
        assertEquals(AiModelCapabilities.DEFAULT_OUTPUT_CEILING,
                AiModelCapabilities.outputCeilingForModel(null));
    }

    @Test
    void explicitCeilingWinsAndIsClampedToASaneRange() {
        AiConfig config = configFor("gpt-4o");
        config.setOutputTokenCeiling(40000);
        assertEquals(40000, config.getEffectiveOutputTokenCeiling(),
                "a manual ceiling must override the model default");

        config.setOutputTokenCeiling(0);
        assertEquals(16384, config.getEffectiveOutputTokenCeiling(),
                "0 means 'derive from the model'");

        config.setOutputTokenCeiling(1);
        assertEquals(AiModelCapabilities.MIN_OUTPUT_CEILING, config.getEffectiveOutputTokenCeiling(),
                "too small a ceiling cannot hold a test case");

        config.setOutputTokenCeiling(Integer.MAX_VALUE);
        assertEquals(AiModelCapabilities.ABSOLUTE_OUTPUT_CEILING,
                config.getEffectiveOutputTokenCeiling(),
                "a typo cannot push the ceiling past the absolute bound");
    }

    @Test
    void ceilingFollowsTheModelWhenConfigReportsNoOverride() {
        assertEquals(8192, configFor("qwen2.5-72b").getEffectiveOutputTokenCeiling());
        assertEquals(AiModelCapabilities.DEFAULT_OUTPUT_CEILING,
                configFor("custom-model").getEffectiveOutputTokenCeiling());
    }
}
