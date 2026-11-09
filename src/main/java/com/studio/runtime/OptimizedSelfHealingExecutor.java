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
import com.studio.model.MultiLocatorProfile;
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

    private static final ObjectMapper MAPPER = ProjectStore.DEFAULT_MAPPER;
    private static final Map<String, String> LOCATE_CACHE = new ConcurrentHashMap<>();
    public static final int MAX_MUTATIONS_PER_CASE = 3;
    private static final Map<String, Integer> MUTATION_COUNTS = new ConcurrentHashMap<>();

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
        MUTATION_COUNTS.clear();
    }

    public static void resetMutationCounts() {
        MUTATION_COUNTS.clear();
    }

    public static int getMutationCount(String caseKey) {
        if (caseKey == null) return 0;
        return MUTATION_COUNTS.getOrDefault(caseKey, 0);
    }

    public static void resetMutationCount(String caseKey) {
        if (caseKey != null) {
            MUTATION_COUNTS.remove(caseKey);
        }
    }

    public static Map<String, String> getLocateCache() {
        return Collections.unmodifiableMap(LOCATE_CACHE);
    }

    /**
     * Resolve a selector with multi-tier self-healing support.
     * Uses a Dynamic Proxy to preserve native Playwright Auto-Waiting.
     * Only intercepts and triggers healing if an action/assert throws PlaywrightException.
     * Supports locator method chaining (e.g. .first(), .last(), .nth(i), .filter(...))
     * while retaining self-healing capabilities on the resulting child locators.
     */
    public Locator resolveLocator(String selector, String actionDescription) {
        if (selector == null || selector.isBlank() || page == null) {
            return page != null ? page.locator(selector) : null;
        }

        String initialSelector = LOCATE_CACHE.getOrDefault(selector, selector);
        Locator initialLocator = page.locator(initialSelector);

        return createProxy(initialSelector, initialLocator, actionDescription, null);
    }

    /**
     * Resolve a locator using a prioritized MultiLocatorProfile (Testsigma-inspired).
     * Evaluates fallback candidates pre-emptively before binding the proxy locator.
     */
    public Locator resolveLocator(MultiLocatorProfile profile, String actionDescription) {
        if (profile == null || page == null) return null;
        String candidate = MultiLocatorCascade.evaluateCascade(page, profile);
        String initialSelector = candidate != null ? candidate : profile.getPrimary();
        if (candidate != null && !candidate.equals(profile.getPrimary())) {
            String oldPrimary = profile.getPrimary();
            profile.promote(candidate);
            recordHealing(oldPrimary, candidate, "MULTI_LOCATOR_CASCADE", 0.99, actionDescription);
            persistMutation(oldPrimary, candidate);
        }
        return resolveLocator(initialSelector, actionDescription);
    }

    /**
     * Executes an interaction and verifies state delta (Skyvern-inspired), recovering from phantom clicks.
     */
    public ActionDeltaVerifier.DeltaResult executeWithDeltaVerification(
            String selector,
            String actionDescription,
            Consumer<Locator> action) {
        Locator loc = resolveLocator(selector, actionDescription);
        if (loc == null) {
            return new ActionDeltaVerifier.DeltaResult(false, false, false, false, "NULL_LOCATOR");
        }
        return ActionDeltaVerifier.executeAndVerify(page, loc, action);
    }

    private Locator createProxy(String activeSelector, Locator targetLocator, String actionDescription, ChainedStep parentStep) {
        return (Locator) java.lang.reflect.Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class, HealedLocatorWrapper.class},
                new java.lang.reflect.InvocationHandler() {
                    private String currentActive = activeSelector;
                    private Locator currentLoc = targetLocator;

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        if ("unwrap".equals(method.getName()) && (args == null || args.length == 0)) {
                            return currentLoc;
                        }
                        if ("getActiveSelector".equals(method.getName()) && (args == null || args.length == 0)) {
                            return currentActive;
                        }
                        if ("toString".equals(method.getName())) {
                            return "HealedLocator[" + currentActive + "]";
                        }
                        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return currentActive.hashCode();
                        }
                        if ("first".equals(method.getName()) && (args == null || args.length == 0)) {
                            Locator visibleLoc = StudioRuntime.findFirstVisible(currentLoc);
                            ChainedStep nextStep = new ChainedStep(parentStep, method, args);
                            return createProxy(currentActive, visibleLoc != null ? visibleLoc : currentLoc.first(), actionDescription, nextStep);
                        }

                        try {
                            Object result = method.invoke(currentLoc, args);
                            if (result instanceof Locator childLoc && !"page".equals(method.getName())) {
                                ChainedStep nextStep = new ChainedStep(parentStep, method, args);
                                return createProxy(currentActive, childLoc, actionDescription, nextStep);
                            }
                            return result;
                        } catch (java.lang.reflect.InvocationTargetException ite) {
                            Throwable cause = ite.getCause();
                            if (cause instanceof PlaywrightException) {
                                String msg = cause.getMessage();
                                if (msg != null && (msg.contains("strict mode violation") || msg.contains("not visible") || msg.contains("Timeout"))) {
                                    try {
                                        Locator visibleLoc = StudioRuntime.findFirstVisible(currentLoc);
                                        if (visibleLoc != null) {
                                            Object result = method.invoke(visibleLoc, args);
                                            System.out.printf("[Self-Healing Disambiguation] Resolved to visible matching element for '%s'%n", currentActive);
                                            if (result instanceof Locator childLoc && !"page".equals(method.getName())) {
                                                ChainedStep nextStep = new ChainedStep(parentStep, method, args);
                                                return createProxy(currentActive, childLoc, actionDescription, nextStep);
                                            }
                                            return result;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                String repaired = repairSelector(
                                        currentActive,
                                        actionDescription != null ? actionDescription : method.getName(),
                                        cause.getMessage());
                                if (repaired != null) {
                                    currentActive = repaired;
                                    Locator base = page.locator(repaired);
                                    if (parentStep != null) {
                                        currentLoc = parentStep.replay(base);
                                    } else {
                                        currentLoc = base;
                                    }
                                    Object result = method.invoke(currentLoc, args);
                                    if (result instanceof Locator childLoc && !"page".equals(method.getName())) {
                                        ChainedStep nextStep = new ChainedStep(parentStep, method, args);
                                        return createProxy(currentActive, childLoc, actionDescription, nextStep);
                                    }
                                    return result;
                                }
                            }
                            throw cause != null ? cause : ite;
                        }
                    }
                }
        );
    }

    private static class ChainedStep {
        final ChainedStep parent;
        final java.lang.reflect.Method method;
        final Object[] args;

        ChainedStep(ChainedStep parent, java.lang.reflect.Method method, Object[] args) {
            this.parent = parent;
            this.method = method;
            this.args = args;
        }

        Locator replay(Locator base) throws Exception {
            Locator target = (parent != null) ? parent.replay(base) : base;
            return (Locator) method.invoke(target, args);
        }
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

        // Tier 1.5: Multi-Locator Fallback Cascade (<15ms, Testsigma-inspired)
        MultiLocatorProfile profile = MultiLocatorCascade.autoBuildProfile(failedSelector);
        String cascadeMatch = MultiLocatorCascade.evaluateCascade(page, profile);
        if (cascadeMatch != null && !cascadeMatch.equals(failedSelector)) {
            recordHealing(failedSelector, cascadeMatch, "MULTI_LOCATOR_CASCADE", 0.98, actionDescription);
            persistMutation(failedSelector, cascadeMatch);
            return cascadeMatch;
        }

        // Tier 2: Heuristic Rule-Based Healing (<30ms, Zero Cost)
        String heuristicMatch = HeuristicHealer.attemptHeuristicRepair(page, failedSelector, actionDescription);
        if (heuristicMatch != null) {
            recordHealing(failedSelector, heuristicMatch, "HEURISTIC", 0.95, actionDescription);
            persistMutation(failedSelector, heuristicMatch);
            return heuristicMatch;
        }

        // Tier 3: Multimodal VLM Healing (only if configured model supports vision)
        if (openAiClient != null && aiConfig != null && !aiConfig.getEffectiveApiKey().isBlank()
                && isVisionSupported(aiConfig.getEffectiveModel())) {
            String vlmMatch = attemptVlmHealing(failedSelector, actionDescription, errorMessage);
            if (vlmMatch != null) {
                recordHealing(failedSelector, vlmMatch, "VLM", 0.90, actionDescription);
                persistMutation(failedSelector, vlmMatch);
                return vlmMatch;
            }
        }

        return null;
    }

    public static boolean isVisionSupported(String modelName) {
        if (modelName == null || modelName.isBlank()) return true;
        String m = modelName.toLowerCase();
        if (m.contains("gpt-3.5") || m.contains("deepseek") || m.contains("embedding")
                || (m.contains("instruct") && !m.contains("vision") && !m.contains("vl"))) {
            return false;
        }
        return true;
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

        // Anti-Thrashing Guard: halt speculative mutations if a case is patched more than 3 times
        String caseKey = currentCase.getId() != null ? currentCase.getId() : currentCase.getName();
        if (caseKey != null) {
            int currentCount = MUTATION_COUNTS.getOrDefault(caseKey, 0);
            if (currentCount >= MAX_MUTATIONS_PER_CASE) {
                System.err.printf("[Anti-Thrashing GUARD] Case '%s' has reached the maximum patch limit (%d). Halting speculative mutations to prevent file thrashing.%n",
                        caseKey, MAX_MUTATIONS_PER_CASE);
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                auditLogs.add(new HealedStepMeta(caseKey, "ANTI_THRASHING_HALT", oldSelector, newSelector,
                        "ANTI_THRASHING_GUARD", 0.0, now));
                return;
            }
            MUTATION_COUNTS.put(caseKey, currentCount + 1);
        }

        try {
            // 1. Persist mutation into script file (strictly targeting quoted string literals to protect Java code)
            String scriptFileName = currentCase.getScriptFile() != null && !currentCase.getScriptFile().isBlank()
                    ? currentCase.getScriptFile()
                    : currentCase.getName() + ".java";
            Path scriptPath = suiteDir.resolve(scriptFileName);
            if (Files.exists(scriptPath)) {
                String content = Files.readString(scriptPath);
                String oldEsc = com.studio.util.StringEscapeUtil.escapeJava(oldSelector);
                String newEsc = com.studio.util.StringEscapeUtil.escapeJava(newSelector);
                String quotedOldEsc = "\"" + oldEsc + "\"";
                String quotedOldRaw = "\"" + oldSelector + "\"";
                String quotedNew = "\"" + newEsc + "\"";

                if (content.contains(quotedOldEsc)) {
                    ProjectStore.writeTextAtomic(scriptPath, content.replace(quotedOldEsc, quotedNew));
                    System.out.printf("[Self-Healing PERSISTED] Updated %s with new selector '%s'%n",
                            scriptFileName, newSelector);
                } else if (content.contains(quotedOldRaw)) {
                    ProjectStore.writeTextAtomic(scriptPath, content.replace(quotedOldRaw, quotedNew));
                    System.out.printf("[Self-Healing PERSISTED] Updated %s with new selector '%s'%n",
                            scriptFileName, newSelector);
                }
            }

            // 2. Persist mutation in suite.json assertions & test steps if applicable
            if (projectStore != null && currentSuite != null) {
                boolean suiteModified = false;
                if (currentCase.getAssertions() != null) {
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
                }
                if (currentCase.getTestSteps() != null) {
                    for (com.studio.model.TestStep step : currentCase.getTestSteps()) {
                        if (oldSelector.equals(step.getTarget())) {
                            step.setTarget(newSelector);
                            suiteModified = true;
                        }
                    }
                }
                if (suiteModified) {
                    projectStore.saveSuite(currentSuite, suiteDir);
                    System.out.println("[Self-Healing PERSISTED] Updated suite.json assertions and test steps.");
                }
            }
        } catch (Exception e) {
            System.err.println("[Self-Healing PERSIST ERROR] " + e.getMessage());
        }
    }

    /**
     * Evaluates a high-level semantic AI assertion (Midscene-style).
     * Validates live page screenshot and DOM against the specified requirement.
     */
    public boolean evaluateSemanticAssertion(String requirement) {
        if (requirement == null || requirement.isBlank()) return true;
        if (page == null) throw new AssertionError("Active page is null for semantic assertion");

        if (openAiClient != null && aiConfig != null && !aiConfig.getEffectiveApiKey().isBlank()) {
            try {
                byte[] ssBytes = page.screenshot(new Page.ScreenshotOptions()
                        .setType(ScreenshotType.JPEG)
                        .setQuality(65));
                String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(ssBytes);
                String domJson = EnhancedDomExtractor.extractReducedDom(page);
                String pageText = "";
                try {
                    pageText = (String) page.evaluate("() => document.body ? document.body.innerText.substring(0, 3000) : ''");
                } catch (Exception ignored) {}

                String prompt = String.format("""
                    [IDENTITY & GOAL]
                    You are a strict, objective Quality Assurance verification engine (Midscene & Fabric-inspired).
                    Your task is to determine whether the given assertion/requirement is TRUE on the active webpage.

                    [REQUIREMENT TO VERIFY]
                    "%s"

                    [PAGE CONTEXT]
                    - URL: %s
                    - Title: %s
                    - Page Text Snippet: %s
                    - Interactive DOM: %s

                    [OUTPUT INSTRUCTIONS]
                    Respond with ONLY a raw JSON object (no markdown ticks):
                    {"pass": true|false, "reason": "brief explanation based strictly on DOM/visual evidence"}
                    """, requirement, page.url(), page.title(), pageText, domJson);

                List<String> images = isVisionSupported(aiConfig.getEffectiveModel()) ? List.of(base64Image) : List.of();
                OpenAiClient.ChatMessage userMsg = new OpenAiClient.ChatMessage("user", prompt, images);
                String resp = openAiClient.chatCompletion(aiConfig, List.of(userMsg));
                JsonNode json = MAPPER.readTree(resp);
                boolean pass = json.path("pass").asBoolean(false);
                String reason = json.path("reason").asText("");
                if (!pass) {
                    throw new AssertionError("[AI Semantic Assert Failed] " + requirement + (reason.isBlank() ? "" : " | " + reason));
                }
                return true;
            } catch (AssertionError ae) {
                throw ae;
            } catch (Exception ex) {
                System.err.println("[Semantic-Assert] VLM call error, falling back to text verification: " + ex.getMessage());
            }
        }

        // Fallback: in-page text verification heuristic
        try {
            String pageText = (String) page.evaluate("() => document.body ? document.body.innerText : ''");
            if (pageText != null && pageText.contains(requirement)) {
                return true;
            }
        } catch (Exception ignored) {}

        throw new AssertionError("[Semantic Assert Failed] Requirement not satisfied on page: " + requirement);
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
