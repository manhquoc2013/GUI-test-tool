package com.studio.runtime;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast Rule-Based Selector Repair Engine (Tier 2).
 * Operates in <30ms locally in the browser with zero AI/token cost.
 */
public class HeuristicHealer {

    private static final Pattern ID_PATTERN = Pattern.compile("#([a-zA-Z0-9_-]+)");
    private static final Pattern ID_ATTR_PATTERN = Pattern.compile("id=['\"]?([^'\"]+)['\"]?");
    private static final Pattern TEXT_PATTERN = Pattern.compile("(?i)(?:text=[\"']?|:has-text\\([\"']?)([^\"'\\)]+)[\"']?\\)?");
    private static final Pattern NAME_ATTR_PATTERN = Pattern.compile("name=['\"]?([^'\"]+)['\"]?");
    private static final Pattern PLACEHOLDER_ATTR_PATTERN = Pattern.compile("placeholder=['\"]?([^'\"]+)['\"]?");

    /**
     * Attempt rule-based heuristic repair against the active page DOM.
     * Returns first candidate where locator.count() == 1, isVisible(), and isEnabled().
     */
    public static String attemptHeuristicRepair(Page page, String failedSelector, String actionDescription) {
        if (page == null || failedSelector == null || failedSelector.isBlank()) {
            return null;
        }

        List<String> candidates = generateCandidates(failedSelector);

        for (String candidate : candidates) {
            try {
                Locator loc = page.locator(candidate);
                if (loc.count() == 1 && loc.first().isVisible() && loc.first().isEnabled()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Invalid syntax for specific locator, skip to next candidate
            }
        }

        return null;
    }

    /**
     * Generate list of heuristic candidate selectors based on common pattern mutations.
     */
    public static List<String> generateCandidates(String selector) {
        Set<String> candidates = new LinkedHashSet<>();
        if (selector == null) return List.of();
        String sel = selector.trim();

        // 1. Dynamic ID pattern (e.g. #btn-submit-12849 or id='user_input_882')
        String idValue = extractId(sel);
        if (idValue != null) {
            String strippedId = idValue.replaceAll("[_-]?\\d{3,}$", "");
            if (!strippedId.isEmpty() && !strippedId.equals(idValue)) {
                candidates.add(String.format("[id^='%s']", strippedId));
                candidates.add(String.format("[id*='%s']", strippedId));
            }
        }

        // 2. Text / Button conversion
        Matcher textMatcher = TEXT_PATTERN.matcher(sel);
        if (textMatcher.find()) {
            String text = textMatcher.group(1).trim();
            if (!text.isEmpty()) {
                candidates.add(String.format("button:has-text('%s')", text));
                candidates.add(String.format("a:has-text('%s')", text));
                candidates.add(String.format("[role='button']:has-text('%s')", text));
                candidates.add(String.format("text='%s'", text));
                candidates.add(String.format("text=%s", text));
            }
        }

        // 3. Name attribute to testid / label / placeholder
        Matcher nameMatcher = NAME_ATTR_PATTERN.matcher(sel);
        if (nameMatcher.find()) {
            String nameVal = nameMatcher.group(1).trim();
            if (!nameVal.isEmpty()) {
                candidates.add(String.format("[data-testid='%s']", nameVal));
                candidates.add(String.format("input[name='%s']", nameVal));
                candidates.add(String.format("[aria-label='%s']", nameVal));
                candidates.add(String.format("[placeholder='%s']", nameVal));
            }
        }

        // 4. Placeholder attribute
        Matcher phMatcher = PLACEHOLDER_ATTR_PATTERN.matcher(sel);
        if (phMatcher.find()) {
            String phVal = phMatcher.group(1).trim();
            if (!phVal.isEmpty()) {
                candidates.add(String.format("input[placeholder='%s']", phVal));
                candidates.add(String.format("[aria-label='%s']", phVal));
            }
        }

        // 5. CSS Class hash cleanup (e.g., .btn_submit__2x8b -> .btn_submit)
        if (sel.startsWith(".")) {
            String cleanClass = sel.replaceAll("__[a-zA-Z0-9_-]+$", "");
            if (!cleanClass.equals(sel)) {
                candidates.add(cleanClass);
            }
        }

        return new ArrayList<>(candidates);
    }

    private static String extractId(String selector) {
        Matcher m1 = ID_PATTERN.matcher(selector);
        if (m1.find()) return m1.group(1);
        Matcher m2 = ID_ATTR_PATTERN.matcher(selector);
        if (m2.find()) return m2.group(1);
        return null;
    }
}
