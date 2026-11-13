package com.studio.runtime;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.studio.model.MultiLocatorProfile;

import java.util.List;

/**
 * High-performance Multi-Locator Cascade Engine (inspired by Testsigma).
 * Evaluates alternative locator strategies in sub-15ms against the live page DOM
 * before delegating to more expensive AI/VLM tiers.
 */
public class MultiLocatorCascade {

    /**
     * Resolves the best matching locator candidate from the given profile.
     * Evaluates candidates in priority order. Returns the first unique, visible match.
     *
     * @param page active Playwright page
     * @param profile MultiLocatorProfile containing primary and fallback candidates
     * @return matching locator string, or null if no candidate matched uniquely
     */
    public static String evaluateCascade(Page page, MultiLocatorProfile profile) {
        if (page == null || profile == null) {
            return null;
        }

        List<String> candidates = profile.getOrderedCandidates();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                Locator loc = page.locator(candidate);
                int count = loc.count();
                if (count == 1 && loc.first().isVisible()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Syntactically invalid or unsupported selector, proceed to next fallback
            }
        }
        return null;
    }

    /**
     * Synthesizes a MultiLocatorProfile automatically from an existing selector
     * by generating rule-based fallback candidates via heuristic analysis.
     *
     * @param selector original selector
     * @return MultiLocatorProfile populated with generated fallback alternatives
     */
    public static MultiLocatorProfile autoBuildProfile(String selector) {
        if (selector == null || selector.isBlank()) {
            return new MultiLocatorProfile("");
        }
        String clean = selector.trim();
        List<String> heuristicFallbacks = HeuristicHealer.generateCandidates(clean);
        return new MultiLocatorProfile(clean, heuristicFallbacks);
    }
}

