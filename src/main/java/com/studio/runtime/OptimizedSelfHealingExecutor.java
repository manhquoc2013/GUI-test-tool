package com.studio.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ScreenshotType;
import com.studio.model.AiConfig;
import com.studio.model.Assertion;
import com.studio.model.HealedStepMeta;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.service.OpenAiClient;
import com.studio.store.ProjectStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Multi-Tier Autonomous Self-Healing Engine.
 * Coordinates Tier 0 (Resilience), Tier 1 (LocateCache), Tier 2 (Heuristics),
 * Tier 3 (Multimodal VLM), and Tier 4 (Sandbox Verification + Persistence).
 */
public class OptimizedSelfHealingExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> LOCATE_CACHE = new ConcurrentHashMap<>();

    private final Page page;
    private final OpenAiClient openAiClient;
    private final AiConfig aiConfig;
    private final ProjectStore projectStore;
    private final Path suiteDir;
    private final TestSuite currentSuite;
    private final TestCase currentCase;
    private final List<HealedStepMeta> auditLogs = new ArrayList<>();

    public OptimizedSelfHealingExecutor(Page page, OpenAiClient openAiClient, AiConfig aiConfig,
                                        ProjectStore projectStore, Path suiteDir,
                                        TestSuite currentSuite, TestCase currentCase) {
        this.page = page;
        this.openAiClient = openAiClient;
        this.aiConfig = aiConfig;
        this.projectStore = projectStore;
        this.suiteDir = suiteDir;
        this.currentSuite = currentSuite;
        this.currentCase = currentCase;
    }

    public List<HealedStepMeta> getAuditLogs() {
        return Collections.unmodifiableList(auditLogs);
    }

    public static void clearCache() {
        LOCATE_CACHE.clear();
    }

    public static Map<String, String> getLocateCache() {
        return Collections.unmodifiableMap(LOCATE_CACHE);
    }

    /**
     * Resolve a selector with multi-tier self-healing support.
     * If the selector is healthy, returns immediately.
     * If not found or hidden, attempts Cache -> Heuristic -> VLM.
     */
    public Locator resolveLocator(String selector, String actionDescription) {
        if (selector == null || selector.isBlank()) {
            return page.locator(selector);
        }

        // Tier 1: Check locate cache
        String activeSelector = LOCATE_CACHE.getOrDefault(selector, selector);

        try {
            Locator loc = page.locator(activeSelector);
            if (loc.count() > 0 && loc.first().isVisible()) {
                return loc;
            }
        } catch (Exception ignored) {}

        // If not immediately valid, run healing loop
        String repaired = repairSelector(selector, actionDescription, "Element not found or not visible");
        if (repaired != null) {
            return page.locator(repaired);
        }

        // Fallback to original locator
        return page.locator(selector);
    }

    /**
     * Executes an action runnable with automated retry and self-healing.
     */
    public void executeWithHealing(
            String currentSelector,
            String actionDescription,
            Consumer<String> actionRunnable,
            Consumer<String> onSelectorRepairedCallback) {

        String activeSelector = LOCATE_CACHE.getOrDefault(currentSelector, currentSelector);

        try {
            actionRunnable.accept(activeSelector);
        } catch (PlaywrightException ex) {
            System.err.printf("[Self-Healing] Step failed with selector '%s': %s%n", activeSelector, ex.getMessage());

            // Tier 0: Smart wait buffer for UI animation / hydration
            try {
                page.waitForTimeout(350);
            } catch (Exception ignored) {}

            String repairedSelector = repairSelector(currentSelector, actionDescription, ex.getMessage());

            if (repairedSelector != null) {
                // Apply healed selector
                actionRunnable.accept(repairedSelector);

                if (onSelectorRepairedCallback != null) {
                    try {
                        onSelectorRepairedCallback.accept(repairedSelector);
                    } catch (Exception cbEx) {
                        System.err.println("[Self-Healing] Callback error: " + cbEx.getMessage());
                    }
                }
            } else {
                throw ex; // Re-throw original exception if unhealable
            }
        }
    }

    /**
     * Core repair orchestrator: Tier 1 (Cache) -> Tier 2 (Heuristic) -> Tier 3 (VLM).
     */
    public String repairSelector(String failedSelector, String actionDescription, String errorMessage) {
        if (page == null || failedSelector == null) return null;

        // Tier 1: Locate Cache
        if (LOCATE_CACHE.containsKey(failedSelector)) {
            String cached = LOCATE_CACHE.get(failedSelector);
            if (validateCandidate(cached)) {
                recordHealing(failedSelector, cached, "LOCATE_CACHE", 1.0, actionDescription);
                return cached;
            }
        }

        // Tier 2: Heuristic Rule-Based Healing (<30ms, Zero Cost)
        String heuristicMatch = HeuristicHealer.attemptHeuristicRepair(page, failedSelector, actionDescription);
        if (heuristicMatch != null) {
            recordHealing(failedSelector, heuristicMatch, "HEURISTIC", 0.95, actionDescription);
            persistMutation(failedSelector, heuristicMatch);
            return heuristicMatch;
        }

        // Tier 3: Multimodal VLM Healing
        if (openAiClient != null && aiConfig != null && !aiConfig.getEffectiveApiKey().isBlank()) {
            String vlmMatch = attemptVlmHealing(failedSelector, actionDescription, errorMessage);
            if (vlmMatch != null) {
                recordHealing(failedSelector, vlmMatch, "VLM", 0.90, actionDescription);
                persistMutation(failedSelector, vlmMatch);
                return vlmMatch;
            }
        }

        return null;
    }

    private String attemptVlmHealing(String failedSelector, String actionDescription, String errorMessage) {
        try {
            byte[] ssBytes = page.screenshot(new Page.ScreenshotOptions()
                    .setType(ScreenshotType.JPEG)
                    .setQuality(65));
            String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(ssBytes);
            String domJson = EnhancedDomExtractor.extractReducedDom(page);

            String prompt = String.format("""
                You are an expert Playwright Test Automation Healer.
                A test step failed because the selector could not locate or interact with the intended element.
                
                - Failed Selector: %s
                - Action Attempted: %s
                - Playwright Error: %s
                - Interactive DOM Candidates: %s
                
                Examine the screenshot and DOM candidates. Determine the single best, robust, unique Playwright selector.
                Prefer: data-testid, aria-label, role with text, or stable ID.
                
                Respond ONLY with a JSON object in this format:
                {
                   "candidates": ["selector_1", "selector_2", "selector_3"],
                   "confidence": 0.95,
                   "reasoning": "brief explanation"
                }
                """, failedSelector, actionDescription, errorMessage, domJson);

            OpenAiClient.ChatMessage msg = new OpenAiClient.ChatMessage("user", prompt, List.of(base64Image));
            String response = openAiClient.chatCompletion(aiConfig, List.of(msg));
            if (response == null || response.isBlank()) return null;

            String jsonText = extractJsonBlock(response);
            JsonNode root = MAPPER.readTree(jsonText);
            JsonNode cands = root.get("candidates");
            if (cands != null && cands.isArray()) {
                for (JsonNode candNode : cands) {
                    String candidate = candNode.asText();
                    if (validateCandidate(candidate)) {
                        return candidate;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Self-Healing VLM] Recovery failed: " + e.getMessage());
        }
        return null;
    }

    private boolean validateCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        try {
            Locator loc = page.locator(candidate);
            return loc.count() == 1 && loc.first().isVisible() && loc.first().isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private void recordHealing(String oldSelector, String newSelector, String method, double confidence, String actionDesc) {
        LOCATE_CACHE.put(oldSelector, newSelector);
        String caseId = currentCase != null ? currentCase.getId() : "runtime";
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        HealedStepMeta meta = new HealedStepMeta(caseId, actionDesc, oldSelector, newSelector, method, confidence, now);
        auditLogs.add(meta);
        System.out.printf("[Self-Healing SUCCESS via %s] '%s' ➔ '%s'%n", method, oldSelector, newSelector);
    }

    private void persistMutation(String oldSelector, String newSelector) {
        if (suiteDir == null || currentCase == null) {
            return;
        }
        try {
            // 1. Persist mutation into script file
            String scriptFileName = currentCase.getScriptFile() != null && !currentCase.getScriptFile().isBlank()
                    ? currentCase.getScriptFile()
                    : currentCase.getName() + ".java";
            Path scriptPath = suiteDir.resolve(scriptFileName);
            if (Files.exists(scriptPath)) {
                String content = Files.readString(scriptPath);
                if (content.contains(oldSelector)) {
                    Files.writeString(scriptPath, content.replace(oldSelector, newSelector));
                    System.out.printf("[Self-Healing PERSISTED] Updated %s with new selector '%s'%n",
                            scriptFileName, newSelector);
                }
            }

            // 2. Persist mutation in suite.json assertions if applicable
            if (projectStore != null && currentSuite != null && currentCase.getAssertions() != null) {
                boolean suiteModified = false;
                for (Assertion a : currentCase.getAssertions()) {
                    if (oldSelector.equals(a.getLocator())) {
                        a.setLocator(newSelector);
                        suiteModified = true;
                    }
                    if (oldSelector.equals(a.getValue())) {
                        a.setValue(newSelector);
                        suiteModified = true;
                    }
                }
                if (suiteModified) {
                    projectStore.saveSuite(currentSuite, suiteDir);
                    System.out.println("[Self-Healing PERSISTED] Updated suite.json assertions.");
                }
            }
        } catch (Exception e) {
            System.err.println("[Self-Healing PERSIST ERROR] " + e.getMessage());
        }
    }

    private static String extractJsonBlock(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
