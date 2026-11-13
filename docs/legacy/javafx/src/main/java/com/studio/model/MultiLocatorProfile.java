package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Multi-Locator representation inspired by Testsigma & Midscene.
 * Stores a primary locator alongside prioritized alternative fallback candidates
 * to enable sub-15ms in-browser pre-emptive self-healing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MultiLocatorProfile {

    private String primary = "";
    private List<String> fallbacks = new ArrayList<>();

    public MultiLocatorProfile() {
    }

    public MultiLocatorProfile(String primary) {
        this.primary = primary != null ? primary.trim() : "";
    }

    public MultiLocatorProfile(String primary, List<String> fallbacks) {
        this.primary = primary != null ? primary.trim() : "";
        if (fallbacks != null) {
            for (String f : fallbacks) {
                if (f != null && !f.isBlank() && !f.trim().equals(this.primary)) {
                    this.fallbacks.add(f.trim());
                }
            }
        }
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary != null ? primary.trim() : "";
    }

    public List<String> getFallbacks() {
        return fallbacks;
    }

    public void setFallbacks(List<String> fallbacks) {
        this.fallbacks = fallbacks != null ? new ArrayList<>(fallbacks) : new ArrayList<>();
    }

    public void addFallback(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            String trimmed = candidate.trim();
            if (!trimmed.equals(primary) && !fallbacks.contains(trimmed)) {
                fallbacks.add(trimmed);
            }
        }
    }

    /**
     * Returns an ordered, deduplicated sequence of candidates starting with primary.
     */
    @JsonIgnore
    public List<String> getOrderedCandidates() {
        Set<String> set = new LinkedHashSet<>();
        if (primary != null && !primary.isBlank()) {
            set.add(primary);
        }
        if (fallbacks != null) {
            for (String f : fallbacks) {
                if (f != null && !f.isBlank()) {
                    set.add(f);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(set));
    }

    /**
     * Returns a new profile promoting a candidate to primary and moving old primary to fallbacks.
     */
    public MultiLocatorProfile promote(String newPrimary) {
        if (newPrimary == null || newPrimary.isBlank()) return this;
        String trimmed = newPrimary.trim();
        List<String> nextFallbacks = new ArrayList<>();
        if (this.primary != null && !this.primary.isBlank() && !this.primary.equals(trimmed)) {
            nextFallbacks.add(this.primary);
        }
        for (String f : this.fallbacks) {
            if (!f.equals(trimmed) && !nextFallbacks.contains(f)) {
                nextFallbacks.add(f);
            }
        }
        return new MultiLocatorProfile(trimmed, nextFallbacks);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiLocatorProfile that = (MultiLocatorProfile) o;
        return Objects.equals(primary, that.primary) && Objects.equals(fallbacks, that.fallbacks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primary, fallbacks);
    }

    @Override
    public String toString() {
        return "MultiLocatorProfile{primary='" + primary + "', fallbacks=" + fallbacks.size() + '}';
    }
}
