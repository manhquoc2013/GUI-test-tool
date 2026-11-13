package com.studio.model;

import java.util.List;
import java.util.Locale;

/**
 * What a chat model can emit in one reply, so the escalation ceiling is a property of the model
 * instead of one hard-coded number.
 *
 * <p>Escalating past a model's own output limit makes the provider reject the request outright, which
 * is why the previous fixed 16384 both under-served large-output models and over-reached small ones.
 * An unknown model keeps the historical default, so a custom or local endpoint behaves exactly as
 * before unless the user sets an explicit ceiling in {@link AiConfig#setOutputTokenCeiling(int)}.</p>
 */
public final class AiModelCapabilities {

    /** Ceiling used for any model the table does not know. */
    public static final int DEFAULT_OUTPUT_CEILING = 16384;
    /** A ceiling below this cannot hold a single generated test case. */
    public static final int MIN_OUTPUT_CEILING = 1024;
    /** Absolute upper bound, independent of the model: protects against a typo in settings. */
    public static final int ABSOLUTE_OUTPUT_CEILING = 131_072;

    private record ModelOutputLimit(String modelMarker, int ceiling) {
    }

    /**
     * Ordered from most specific to least, because {@code gpt-4.1}/{@code gpt-4o} both start with
     * {@code gpt-4}. First match wins.
     */
    private static final List<ModelOutputLimit> MODEL_LIMITS = List.of(
            new ModelOutputLimit("gpt-4.1", 32_768),
            new ModelOutputLimit("gpt-4o", 16_384),
            new ModelOutputLimit("gpt-4-turbo", 4_096),
            new ModelOutputLimit("gpt-4", 8_192),
            new ModelOutputLimit("gpt-3.5", 4_096),
            new ModelOutputLimit("o4", 32_768),
            new ModelOutputLimit("o3", 32_768),
            new ModelOutputLimit("o1", 32_768),
            new ModelOutputLimit("claude", 32_000),
            new ModelOutputLimit("gemini-2.5", 65_536),
            new ModelOutputLimit("gemini", 8_192),
            new ModelOutputLimit("deepseek", 8_192),
            new ModelOutputLimit("qwen", 8_192),
            new ModelOutputLimit("llama", 8_192),
            new ModelOutputLimit("mistral", 8_192)
    );

    private AiModelCapabilities() {
    }

    /** The one-reply output ceiling for a model id; {@link #DEFAULT_OUTPUT_CEILING} when unknown. */
    public static int outputCeilingForModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return DEFAULT_OUTPUT_CEILING;
        }
        String probe = modelName.toLowerCase(Locale.ROOT).trim();
        for (ModelOutputLimit limit : MODEL_LIMITS) {
            if (probe.contains(limit.modelMarker())) {
                return limit.ceiling();
            }
        }
        return DEFAULT_OUTPUT_CEILING;
    }

    /**
     * The ceiling in force for a config: an explicit {@code outputTokenCeiling} wins, otherwise the
     * model's own capability. Always clamped into {@code [MIN, ABSOLUTE]}.
     */
    public static int resolveOutputCeiling(AiConfig config) {
        if (config == null) {
            return DEFAULT_OUTPUT_CEILING;
        }
        int configured = config.getOutputTokenCeiling();
        int candidate = configured > 0 ? configured : outputCeilingForModel(config.getEffectiveModel());
        return clampCeiling(candidate);
    }

    /** Keeps a ceiling inside the range a request can actually carry. */
    public static int clampCeiling(int ceiling) {
        return Math.max(MIN_OUTPUT_CEILING, Math.min(ABSOLUTE_OUTPUT_CEILING, ceiling));
    }
}
