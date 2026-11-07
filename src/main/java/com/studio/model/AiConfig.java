package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiConfig {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o";
    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_TOKENS = 4096;

    private String apiKey = "";
    private String baseUrl = DEFAULT_BASE_URL;
    private String model = DEFAULT_MODEL;
    private double temperature = DEFAULT_TEMPERATURE;
    private int maxTokens = DEFAULT_MAX_TOKENS;

    public AiConfig() {
    }

    public AiConfig(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.baseUrl = DEFAULT_BASE_URL;
        } else {
            String trimmed = baseUrl.trim();
            if (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            this.baseUrl = trimmed;
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = (model != null && !model.isBlank()) ? model.trim() : DEFAULT_MODEL;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
    }

    @JsonIgnore
    public String getEffectiveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String env = System.getenv("OPENAI_API_KEY");
        return env != null ? env.trim() : "";
    }

    @JsonIgnore
    public String getEffectiveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank() && !DEFAULT_BASE_URL.equalsIgnoreCase(baseUrl.trim())) {
            return baseUrl.trim();
        }
        String env = System.getenv("OPENAI_BASE_URL");
        if (env != null && !env.isBlank()) {
            String trimmed = env.trim();
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        return (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_BASE_URL;
    }

    @JsonIgnore
    public String getEffectiveModel() {
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        String env = System.getenv("OPENAI_MODEL");
        return (env != null && !env.isBlank()) ? env.trim() : DEFAULT_MODEL;
    }

    @JsonIgnore
    public boolean hasApiKey() {
        return !getEffectiveApiKey().isEmpty();
    }
}
