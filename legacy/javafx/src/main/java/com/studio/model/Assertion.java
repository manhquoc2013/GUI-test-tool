package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Assertion {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private String type;
    private String value;
    private String status = "ACTIVE";
    private boolean exact = false;
    private int timeoutMs = 30000;
    private String altContains;
    private String locator;
    private String attribute;
    private String expectedValue;
    private String operator;

    public Assertion() {
        this.status = "ACTIVE";
        this.exact = false;
        this.timeoutMs = 30000;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isExact() {
        return exact;
    }

    public void setExact(boolean exact) {
        this.exact = exact;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getAltContains() {
        return altContains;
    }

    public void setAltContains(String altContains) {
        this.altContains = altContains;
    }

    public String getLocator() {
        return locator;
    }

    public void setLocator(String locator) {
        this.locator = locator;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    /**
     * Returns the effective display/index value of this assertion, combining
     * {@code value}, {@code attribute}, and {@code expectedValue} when {@code value} is empty.
     */
    public String getEffectiveValue() {
        if (value != null && !value.isBlank()) {
            return value;
        }
        boolean hasAttr = attribute != null && !attribute.isBlank();
        boolean hasExpected = expectedValue != null && !expectedValue.isBlank();
        if (hasAttr && hasExpected) {
            return attribute + "=" + expectedValue;
        }
        if (hasExpected) {
            return expectedValue;
        }
        if (hasAttr) {
            return attribute;
        }
        return "";
    }

    /**
     * Resolves the attribute or storage/cookie key from {@code attribute}, {@code value} ("key=val"), or {@code locator}.
     */
    public String resolveAttribute() {
        if (attribute != null && !attribute.isBlank()) {
            return attribute.trim();
        }
        if (value != null && value.contains("=")) {
            return value.substring(0, value.indexOf('=')).trim();
        }
        if (locator != null && !locator.isBlank()) {
            return locator.trim();
        }
        return value != null ? value.trim() : "";
    }

    /**
     * Resolves the expected value from {@code expectedValue} or {@code value} ("key=val" or raw value).
     */
    public String resolveExpectedValue() {
        if (expectedValue != null && !expectedValue.isBlank()) {
            return expectedValue;
        }
        if ((attribute == null || attribute.isBlank()) && value != null && value.contains("=")) {
            return value.substring(value.indexOf('=') + 1).trim();
        }
        return value != null ? value : "";
    }

    public String getDetail() {
        String effVal = getEffectiveValue();
        if (locator != null && !locator.isBlank()) {
            return locator + (!effVal.isBlank() ? " [" + effVal + "]" : "");
        }
        return effVal;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
