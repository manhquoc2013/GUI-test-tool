package com.studio.controller;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiSettingsPresetsTest {

    @Test
    void testOpenAiDefaultPresets() {
        List<String> presets = AiSettingsDialog.getPresetsForBaseUrl(null);
        assertTrue(presets.contains("gpt-4o"));
        assertTrue(presets.contains("gpt-4o-mini"));

        List<String> presetsEmpty = AiSettingsDialog.getPresetsForBaseUrl("");
        assertTrue(presetsEmpty.contains("gpt-4o"));

        List<String> presetsOfficial = AiSettingsDialog.getPresetsForBaseUrl("https://api.openai.com/v1");
        assertTrue(presetsOfficial.contains("gpt-4o"));
    }

    @Test
    void testOpenRouterPresets() {
        List<String> presets = AiSettingsDialog.getPresetsForBaseUrl("https://openrouter.ai/api/v1");
        assertTrue(presets.contains("deepseek/deepseek-r1"));
        assertTrue(presets.contains("anthropic/claude-3.5-sonnet"));
        assertTrue(presets.contains("openai/gpt-4o"));
    }

    @Test
    void testDeepSeekPresets() {
        List<String> presets = AiSettingsDialog.getPresetsForBaseUrl("https://api.deepseek.com");
        assertTrue(presets.contains("deepseek-chat"));
        assertTrue(presets.contains("deepseek-reasoner"));
    }

    @Test
    void testLocalOllamaPresets() {
        List<String> presets = AiSettingsDialog.getPresetsForBaseUrl("http://localhost:11434/v1");
        assertTrue(presets.contains("llama3.2"));
        assertTrue(presets.contains("qwen2.5-coder"));
    }

    @Test
    void testGeminiPresets() {
        List<String> presets = AiSettingsDialog.getPresetsForBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/");
        assertTrue(presets.contains("gemini-2.0-flash"));
        assertTrue(presets.contains("gemini-3.8-flash"));
    }
}

