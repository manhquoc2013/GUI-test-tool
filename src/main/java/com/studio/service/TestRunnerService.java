package com.studio.service;

import com.studio.model.*;
import com.studio.runtime.OptimizedSelfHealingExecutor;
import com.studio.runtime.StudioRuntime;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.store.StepGroupStore;
import com.studio.util.StudioPaths;
import com.studio.util.VariableResolver;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Central test runner. Handles all 4 scopes (ALL / GROUP / SUITE / CASE),
 * shared browser context, stop-on-first-failure, soft-assert injection,
 * in-process compile + execution, and screenshot capture.
 */
public class TestRunnerService {

    private final ProjectStore projectStore;
    private final ReportStore reportStore;
    private final SettingsStore settingsStore;
    private final OpenAiClient openAiClient;
    private final StepGroupStore stepGroupStore;
    private final com.studio.store.TestDataProfileStore testDataProfileStore;
    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public TestRunnerService(ProjectStore projectStore, ReportStore reportStore) {
        this(projectStore, reportStore, new SettingsStore(), new OpenAiClient());
    }

    public TestRunnerService(ProjectStore projectStore, ReportStore reportStore,
                             SettingsStore settingsStore, OpenAiClient openAiClient) {
        this(projectStore, reportStore, settingsStore, openAiClient, new StepGroupStore());
    }

    public TestRunnerService(ProjectStore projectStore, ReportStore reportStore,
                             SettingsStore settingsStore, OpenAiClient openAiClient,
                             StepGroupStore stepGroupStore) {
        this(projectStore, reportStore, settingsStore, openAiClient, stepGroupStore, new com.studio.store.TestDataProfileStore());
    }

    public TestRunnerService(ProjectStore projectStore, ReportStore reportStore,
                             SettingsStore settingsStore, OpenAiClient openAiClient,
                             StepGroupStore stepGroupStore,
                             com.studio.store.TestDataProfileStore testDataProfileStore) {
        this.projectStore = projectStore;
        this.reportStore = reportStore;
        this.settingsStore = settingsStore != null ? settingsStore : new SettingsStore();
        this.openAiClient = openAiClient != null ? openAiClient : new OpenAiClient();
        this.stepGroupStore = stepGroupStore != null ? stepGroupStore : new StepGroupStore();
        this.testDataProfileStore = testDataProfileStore != null ? testDataProfileStore : new com.studio.store.TestDataProfileStore();
    }

    /**
     * Main entry point: run tests with the given configuration.
     *
     * @param scope              ONE of: "ALL", "GROUP", "SUITE", "CASE"
     * @param scopePath          path to scope target (group dir, suite dir, case java file)
     * @param headless           whether to run browsers in headless mode
     * @param browserId          browser identifier (CHROMIUM, FIREFOX, WEBKIT, CHROME, EDGE)
     * @param stopOnFirstFailure true to stop after the first FAILED case
     * @param shareContext       true to share one BrowserContext across multi-case runs
     * @param terminalOutput     callback for progress messages
     * @return the completed RunReport
     */
    public RunReport run(String scope, Path scopePath, boolean headless, String browserId,
                         boolean stopOnFirstFailure, boolean shareContext,
                         Consumer<String> terminalOutput) {
        return run(scope, scopePath, headless, browserId, stopOnFirstFailure, shareContext, terminalOutput, null);
    }

    /**
     * Overload supporting Priority-Driven execution (e.g. running only P0 for Smoke Test).
     */
    public RunReport run(String scope, Path scopePath, boolean headless, String browserId,
                         boolean stopOnFirstFailure, boolean shareContext,
                         Consumer<String> terminalOutput, Set<String> targetPriorities) {

        String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        // Collect cases (collectCasesForScope applies skip logic per the plan)
        List<CaseResult> allCases;
        try {
            allCases = projectStore.collectCasesForScope(scope, scopePath);
        } catch (Exception e) {
            terminal("FATAL: Failed to collect cases: " + e.getMessage(), terminalOutput);
            return emptyUnsavedReport(runId, scope, scopePath, browserId, headless,
                    shareContext, stopOnFirstFailure);
        }

        if (allCases == null || allCases.isEmpty()) {
            terminal("ℹ️ Không có test case nào để chạy — bỏ qua lưu báo cáo.", terminalOutput);
            return emptyUnsavedReport(runId, scope, scopePath, browserId, headless,
                    shareContext, stopOnFirstFailure);
        }

        // Apply Priority Filtering if specified
        if (targetPriorities != null && !targetPriorities.isEmpty()) {
            for (CaseResult cr : allCases) {
                if (!"SKIPPED".equals(cr.getStatus()) && !targetPriorities.contains(cr.getPriority())) {
                    cr.setStatus("SKIPPED");
                    cr.setSkipReason("Excluded by priority filter (" + cr.getPriority() + ")");
                }
            }
        }

        Path screenshotDir = reportStore.getScreenshotDir(runId);
        try {
            Files.createDirectories(screenshotDir);
        } catch (Exception e) {
            terminal("WARN: Cannot create screenshot dir: " + e.getMessage(), terminalOutput);
        }

        RunReport report = new RunReport();
        report.setRunId(runId);
        report.setScope(scope);
        report.setScopePath(scopePath != null ? scopePath.toString() : "");
        report.setScopeName(scopePath != null ? scopePath.getFileName().toString() : "ALL");
        report.setBrowser(browserId);
        report.setHeadless(headless);
        report.setShareContext(shareContext);
        report.setStopOnFirstFailure(stopOnFirstFailure);
        report.setStartedAt(LocalDateTime.now().toString());

        // Separate running cases from already-skipped
        List<CaseResult> toRun = new ArrayList<>();
        for (CaseResult cr : allCases) {
            if (!"SKIPPED".equals(cr.getStatus())) {
                toRun.add(cr);
            }
        }

        // Build ordered list: skipped cases first, then running cases
        List<CaseResult> ordered = new ArrayList<>();
        for (CaseResult cr : allCases) {
            if ("SKIPPED".equals(cr.getStatus())) {
                ordered.add(cr);
            }
        }
        ordered.addAll(toRun);
        allCases = ordered;

        // Shared browser session — only for multi-case with share enabled
        SharedBrowserSession session = null;
        boolean useShared = shareContext && toRun.size() > 1;

        RunCancelService.beginRun();
        RunProgressService.beginRun(scope, scopePath);
        for (CaseResult cr : allCases) {
            if ("SKIPPED".equals(cr.getStatus())) {
                RunProgressService.caseFinished(cr);
            }
        }
        try {
            if (useShared) {
                terminal("Starting shared browser session (" + browserId + ", headless=" + headless + ")", terminalOutput);
                session = new SharedBrowserSession();
                session.start(headless, browserId);
                RunCancelService.attachSession(session);
            }

            for (CaseResult cr : allCases) {
                // Already skipped by collect logic
                if ("SKIPPED".equals(cr.getStatus())) {
                    continue;
                }

                if (RunCancelService.isCancelRequested()) {
                    applyCancelToPending(allCases, terminalOutput);
                    break;
                }

                // Stop-on-first-failure
                if (stopOnFirstFailure && report.getFailed() > 0) {
                    cr.setStatus("SKIPPED");
                    cr.setSkipReason("Stopped after first failure");
                    report.setSkipped(report.getSkipped() + 1);
                    terminal("SKIP: " + cr.getTestCaseName() + " — " + cr.getSkipReason(), terminalOutput);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                RunProgressService.caseStarted(cr);
                terminal("Running: " + cr.getTestCaseName(), terminalOutput);
                long start = System.currentTimeMillis();

                // Resolve suite directory from group path + suite name
                Path suiteDir = resolveSuiteDir(cr);
                if (suiteDir == null || !Files.isDirectory(suiteDir)) {
                    String lookedUp = formatSuiteLookup(cr);
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Không tìm thấy thư mục suite: " + lookedUp);
                    cr.setSkipReason(null);
                    report.setFailed(report.getFailed() + 1);
                    terminal("ERROR: Không tìm thấy thư mục suite cho \""
                            + cr.getTestCaseName() + "\" (đường dẫn: " + lookedUp + ")", terminalOutput);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                // Load suite.json for assertions
                TestSuite suite = loadSuite(suiteDir);
                if (suite == null) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Cannot load suite.json");
                    report.setFailed(report.getFailed() + 1);
                    terminal("ERROR: Cannot load suite.json for " + cr.getTestCaseName(), terminalOutput);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                // Collect ACTIVE assertions from the running case
                List<Assertion> activeAsserts = new ArrayList<>();
                TestCase runningCase = suite.getCases().stream()
                        .filter(c -> c.getId().equals(cr.getTestCaseId()))
                        .findFirst()
                        .orElse(null);
                if (runningCase != null) {
                    for (Assertion a : runningCase.getAssertions()) {
                        if ("ACTIVE".equals(a.getStatus())) {
                            activeAsserts.add(a);
                        }
                    }
                }

                // Read the script file
                String scriptCode;
                try {
                    scriptCode = projectStore.readCaseScript(suiteDir, cr.getTestCaseId());
                } catch (Exception e) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Script file not found or unreadable: " + e.getMessage());
                    report.setFailed(report.getFailed() + 1);
                    terminal("ERROR: Script not found for " + cr.getTestCaseName(), terminalOutput);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                if (scriptCode == null || scriptCode.isEmpty()) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Script file empty or not found");
                    report.setFailed(report.getFailed() + 1);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                if (RunCancelService.isCancelRequested()) {
                    markCaseCancelled(cr, report, start, null, terminalOutput);
                    applyCancelToPending(allCases, terminalOutput);
                    break;
                }

                // Resolve variables (Automa-inspired) and expand reusable step groups (Testsigma-inspired)
                Map<String, String> activeVariables = new HashMap<>();
                String profileId = runningCase != null && runningCase.getTestDataProfileId() != null && !runningCase.getTestDataProfileId().isBlank()
                        ? runningCase.getTestDataProfileId()
                        : (suite != null ? suite.getTestDataProfileId() : null);
                if (profileId != null && testDataProfileStore != null) {
                    testDataProfileStore.findById(profileId).ifPresent(p -> activeVariables.putAll(p.getVariables()));
                }
                if (runningCase != null && runningCase.getTestDataSets() != null && !runningCase.getTestDataSets().isEmpty()) {
                    TestDataItem firstItem = runningCase.getTestDataSets().get(0);
                    if (firstItem.getParameters() != null) {
                        activeVariables.putAll(firstItem.getParameters());
                    }
                }
                String expandedScript = expandStepGroups(scriptCode, stepGroupStore);
                String resolvedScript = VariableResolver.resolve(expandedScript, activeVariables);
                List<Assertion> resolvedAsserts = VariableResolver.resolveAssertions(activeAsserts, activeVariables);

                // Prepare screenshot paths with filesystem-safe names (guards against Windows forbidden chars and subfolder slashes)
                String safeCaseName = com.studio.util.StringEscapeUtil.safeFileName(
                        cr.getTestCaseName(),
                        cr.getTestCaseId() != null ? cr.getTestCaseId() : "case");
                Path ssPassed = screenshotDir.resolve(safeCaseName + "_PASSED.png");
                Path ssFailed = screenshotDir.resolve(safeCaseName + "_FAILED.png");
                String harnessScreenshotBase = screenshotDir.resolve(safeCaseName).toString();

                // Transform script for in-process execution
                String harnessClass = ScriptHarnessTransformer.sanitizeClassName(
                        cr.getTestCaseId() + "_Run");
                String transformed = ScriptHarnessTransformer.transform(
                        resolvedScript, useShared, harnessScreenshotBase, harnessClass);

                // Inject soft-assert block into transformed script (inside try, before finally)
                String assertBlock = AssertCodeGenerator.generateAssertBlock(resolvedAsserts);
                transformed = injectAssertBlock(transformed, assertBlock);

                // Write harness to work/harness/<runId>/ directory
                Path harnessDir = StudioPaths.getHarnessDir().resolve(runId);
                try {
                    Files.createDirectories(harnessDir);
                } catch (Exception e) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Failed to create harness dir: " + e.getMessage());
                    report.setFailed(report.getFailed() + 1);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                Path harnessFile = harnessDir.resolve(harnessClass + ".java");
                try {
                    Files.writeString(harnessFile, transformed);
                } catch (Exception e) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Failed to write harness: " + e.getMessage());
                    report.setFailed(report.getFailed() + 1);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                // Compile the harness
                Path assertResultsFile = harnessDir.resolve(cr.getTestCaseId() + "_asserts.json");
                List<String> compileErrors = new ArrayList<>();
                boolean compiled = compileHarness(harnessFile, harnessDir, terminalOutput, compileErrors);
                if (!compiled) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Lỗi biên dịch mã kiểm thử (Compilation failed)");
                    StringBuilder sb = new StringBuilder("❌ LỖI BIÊN DỊCH MÃ NGUỒN (Compilation Error):\n");
                    if (!compileErrors.isEmpty()) {
                        for (String err : compileErrors) {
                            sb.append("   • ").append(err).append("\n");
                        }
                    } else {
                        sb.append("   • Trình biên dịch javac không thể biên dịch file kiểm thử.\n");
                    }
                    sb.append("\n🔍 CÁCH TÁI HIỆN:\n");
                    sb.append("   1. Kiểm tra mã nguồn file: ").append(cr.getScriptFile() != null ? cr.getScriptFile() : harnessFile.getFileName().toString()).append("\n");
                    sb.append("   2. Sửa lại cú pháp Java hoặc các thư viện phụ thuộc.");
                    cr.setLogExcerpt(sb.toString());
                    report.setFailed(report.getFailed() + 1);
                    terminal("ERROR: Compilation failed for " + cr.getTestCaseName()
                            + " — xem chi tiết Compile ở trên", terminalOutput);
                    RunProgressService.caseFinished(cr);
                    continue;
                }

                if (RunCancelService.isCancelRequested()) {
                    markCaseCancelled(cr, report, start, ssFailed.toString(), terminalOutput);
                    applyCancelToPending(allCases, terminalOutput);
                    break;
                }

                // Execute harness in-process
                try {
                    System.setProperty("studio.assertResults", assertResultsFile.toString());

                    AiConfig aiConfig = null;
                    try {
                        aiConfig = settingsStore.load().getAiConfig();
                    } catch (Exception ignored) {}

                    if (!useShared) {
                        // Per-case: start own browser session
                        SharedBrowserSession perCaseSession = new SharedBrowserSession();
                        perCaseSession.start(headless, browserId);
                        RunCancelService.attachSession(perCaseSession);
                        StudioRuntime.set(perCaseSession.page(), perCaseSession.context());
                        OptimizedSelfHealingExecutor executor = new OptimizedSelfHealingExecutor(
                                perCaseSession.page(), openAiClient, aiConfig, projectStore,
                                suiteDir, suite, runningCase);
                        StudioRuntime.setExecutor(executor);
                        try {
                            runHarnessClass(harnessDir, harnessClass);
                        } finally {
                            List<HealedStepMeta> healed = StudioRuntime.getHealedSteps();
                            if (healed != null && !healed.isEmpty()) {
                                cr.setHealedSteps(new ArrayList<>(healed));
                                for (HealedStepMeta h : healed) {
                                    terminal("  ✨ [Auto-Healed via " + h.getMethod() + "] '" + h.getOriginalSelector() + "' ➔ '" + h.getRepairedSelector() + "'", terminalOutput);
                                }
                            }
                            StudioRuntime.clear();
                            RunCancelService.clearSession(perCaseSession);
                            perCaseSession.close();
                        }
                    } else {
                        // Shared: use the existing session's page
                        RunCancelService.attachSession(session);
                        StudioRuntime.set(session.page(), session.context());
                        OptimizedSelfHealingExecutor executor = new OptimizedSelfHealingExecutor(
                                session.page(), openAiClient, aiConfig, projectStore,
                                suiteDir, suite, runningCase);
                        StudioRuntime.setExecutor(executor);
                        try {
                            runHarnessClass(harnessDir, harnessClass);
                        } finally {
                            List<HealedStepMeta> healed = StudioRuntime.getHealedSteps();
                            if (healed != null && !healed.isEmpty()) {
                                cr.setHealedSteps(new ArrayList<>(healed));
                                for (HealedStepMeta h : healed) {
                                    terminal("  ✨ [Auto-Healed via " + h.getMethod() + "] '" + h.getOriginalSelector() + "' ➔ '" + h.getRepairedSelector() + "'", terminalOutput);
                                }
                            }
                            // Don't clear page/context for shared, but clear executor
                            StudioRuntime.setExecutor(null);
                        }
                    }

                    if (RunCancelService.isCancelRequested()) {
                        markCaseCancelled(cr, report, start, ssFailed.toString(), terminalOutput);
                        applyCancelToPending(allCases, terminalOutput);
                        break;
                    }

                    // Determine pass/fail by reading assert-results file
                    int failures = AssertResultsScorer.countFailures(assertResultsFile);
                    long duration = System.currentTimeMillis() - start;
                    Path rawScreenshot = Path.of(harnessScreenshotBase + ".png");

                    // Load assert results for detail view
                    cr.setAssertResults(AssertResultsScorer.loadResults(assertResultsFile));

                    if (failures > 0) {
                        cr.setStatus("FAILED");
                        cr.setFailureReason(failures + " assertion(s) failed");
                        cr.setScreenshotPath(finalizeScreenshot(rawScreenshot, ssFailed));
                        report.setFailed(report.getFailed() + 1);

                        StringBuilder assertLog = new StringBuilder();
                        assertLog.append(String.format("❌ KIỂM TRA THẤT BẠI: Phát hiện %d assertion không thỏa mãn.\n\n", failures));
                        assertLog.append("🔍 CÁCH TÁI HIỆN (Steps to Reproduce):\n");
                        if (runningCase != null && runningCase.getStartUrl() != null && !runningCase.getStartUrl().isBlank()) {
                            assertLog.append("   1. Mở trang: ").append(runningCase.getStartUrl()).append("\n");
                        }
                        assertLog.append("   2. Chạy kịch bản: '").append(cr.getTestCaseName()).append("'\n");
                        assertLog.append("   3. Đối chiếu các mục Assert bị thất bại trong danh sách kiểm tra bên trên:\n");
                        for (AssertResult ar : cr.getAssertResults()) {
                            if ("FAILED".equalsIgnoreCase(ar.getStatus())) {
                                assertLog.append(String.format("      • [%s] %s: %s\n",
                                        ar.getType() != null ? ar.getType() : "ASSERT",
                                        ar.getDetail() != null ? ar.getDetail() : "",
                                        ar.getMessage() != null ? ar.getMessage() : "Giá trị không khớp"));
                            }
                        }
                        cr.setLogExcerpt(assertLog.toString());
                    } else {
                        cr.setStatus("PASSED");
                        cr.setScreenshotPath(finalizeScreenshot(rawScreenshot, ssPassed));
                        report.setPassed(report.getPassed() + 1);
                    }
                    cr.setDurationMs(duration);
                    cr.setExitCode(0);

                    terminal(String.format("  %s: %s (%dms)", cr.getTestCaseName(), cr.getStatus(), duration), terminalOutput);
                    RunProgressService.caseFinished(cr);

                } catch (Throwable e) {
                    if (RunCancelService.isCancelRequested()) {
                        markCaseCancelled(cr, report, start, ssFailed.toString(), terminalOutput);
                        applyCancelToPending(allCases, terminalOutput);
                        break;
                    }
                    String currentUrl = null;
                    try {
                        if (StudioRuntime.page() != null) {
                            currentUrl = StudioRuntime.page().url();
                        }
                    } catch (Throwable ignored) {}

                    String reason = extractFailureReason(e);
                    String startUrl = (currentUrl != null && !currentUrl.isBlank())
                            ? currentUrl
                            : (runningCase != null ? runningCase.getStartUrl() : null);
                    String reproLog = buildReproductionLog(e, cr.getTestCaseName(), cr.getScriptFile(), startUrl, runningCase);

                    cr.setStatus("FAILED");
                    cr.setFailureReason(reason);
                    cr.setLogExcerpt(reproLog);
                    cr.setDurationMs(System.currentTimeMillis() - start);
                    cr.setScreenshotPath(finalizeScreenshot(
                            Path.of(harnessScreenshotBase + ".png"), ssFailed));
                    report.setFailed(report.getFailed() + 1);
                    terminal("FAILED: " + cr.getTestCaseName() + " — " + cr.getFailureReason(), terminalOutput);
                    RunProgressService.caseFinished(cr);
                } finally {
                    System.clearProperty("studio.assertResults");
                    if (!useShared) {
                        StudioRuntime.clear();
                    }
                }
            }

        } catch (Exception e) {
            terminal("FATAL: Run failed: " + e.getMessage(), terminalOutput);
        } finally {
            if (session != null) {
                StudioRuntime.clear();
                RunCancelService.clearSession(session);
                session.close();
            }
            RunCancelService.endRun();
            // Clean up temporary compiled harness directory to release disk and file handles
            deleteQuietly(StudioPaths.getHarnessDir().resolve(runId));
            // Reclaim resources and return unused memory to OS
            ResourceReleaseService.releaseAfterTestRun();
        }

        // Final counts
        report.setTotal(allCases.size());
        int skipCount = 0;
        for (CaseResult cr : allCases) {
            if ("SKIPPED".equals(cr.getStatus())) skipCount++;
        }
        report.setSkipped(skipCount);

        report.setFinishedAt(LocalDateTime.now().toString());
        report.setCases(allCases);
        RunProgressService.endRun(report);

        // Only persist when there was at least one case in the run
        if (report.getTotal() > 0) {
            try {
                reportStore.saveReport(report);
                terminal(String.format("Report saved: %s/%s",
                        reportStore.getScreenshotDir(runId).getParent().getFileName(),
                        report.getRunId()), terminalOutput);
            } catch (Exception e) {
                terminal("WARN: Failed to save report: " + e.getMessage(), terminalOutput);
            }
        } else {
            terminal("ℹ️ Không có test — bỏ qua lưu báo cáo.", terminalOutput);
            deleteQuietly(reportStore.getScreenshotDir(runId).getParent());
        }

        return report;
    }

    private static RunReport emptyUnsavedReport(String runId, String scope, Path scopePath,
                                                String browserId, boolean headless,
                                                boolean shareContext, boolean stopOnFirstFailure) {
        RunReport empty = new RunReport();
        empty.setRunId(runId);
        empty.setScope(scope);
        empty.setScopePath(scopePath != null ? scopePath.toString() : "");
        empty.setScopeName(scopePath != null ? scopePath.getFileName().toString() : "ALL");
        empty.setBrowser(browserId);
        empty.setHeadless(headless);
        empty.setShareContext(shareContext);
        empty.setStopOnFirstFailure(stopOnFirstFailure);
        empty.setStartedAt(LocalDateTime.now().toString());
        empty.setFinishedAt(LocalDateTime.now().toString());
        empty.setTotal(0);
        empty.setPassed(0);
        empty.setFailed(0);
        empty.setSkipped(0);
        empty.setCases(new ArrayList<>());
        return empty;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                        });
            }
        } catch (Exception ignored) { }
    }

    // ──────────────────────────── Helpers ────────────────────────────────

    /** Resolve the suite directory: prefer absolute suiteDir, else groupPath + suiteName. */
    private Path resolveSuiteDir(CaseResult cr) {
        if (cr.getSuiteDir() != null && !cr.getSuiteDir().isBlank()) {
            Path absolute = Path.of(cr.getSuiteDir());
            if (Files.isDirectory(absolute)) {
                return absolute;
            }
        }
        Path base = StudioPaths.getProjectDir();
        if (cr.getGroupPath() != null && !cr.getGroupPath().isEmpty()) {
            // groupPath can contain slashes for nested groups: e.g. "Regression/Payments"
            String[] parts = cr.getGroupPath().split("/");
            for (String part : parts) {
                if (part == null || part.isBlank()) continue;
                base = base.resolve(part);
            }
        }
        return base.resolve(cr.getSuiteName());
    }

    private static String formatSuiteLookup(CaseResult cr) {
        String group = cr.getGroupPath() != null ? cr.getGroupPath().trim() : "";
        String suite = cr.getSuiteName() != null ? cr.getSuiteName() : "";
        if (group.isEmpty()) return suite;
        return group + "/" + suite;
    }

    /** Load suite.json from the given suite directory. */
    private TestSuite loadSuite(Path suiteDir) {
        try {
            String json = Files.readString(suiteDir.resolve("suite.json"));
            return mapper.readValue(json, TestSuite.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Inject soft-assert block inside the screenshot try, before its finally. */
    private String injectAssertBlock(String code, String assertBlock) {
        if (assertBlock == null || assertBlock.isBlank()) {
            return code;
        }
        String marker = "\n        } finally {";
        int idx = code.lastIndexOf(marker);
        if (idx >= 0) {
            return code.substring(0, idx) + assertBlock + code.substring(idx);
        }
        // Fallback: before closing brace of main()
        int mainStart = code.indexOf("public static void main");
        if (mainStart < 0) {
            int lastBrace = code.lastIndexOf("}");
            if (lastBrace >= 0) {
                return code.substring(0, lastBrace) + assertBlock + "\n    }";
            }
            return code + "\n" + assertBlock;
        }
        int braceStart = code.indexOf("{", mainStart);
        if (braceStart < 0) return code + "\n" + assertBlock;

        int depth = 1;
        int pos = braceStart + 1;
        while (pos < code.length() && depth > 0) {
            char ch = code.charAt(pos);
            if (ch == '{') depth++;
            else if (ch == '}') depth--;
            pos++;
        }
        int closingBrace = pos - 1;
        return code.substring(0, closingBrace) + assertBlock + "\n    }" + code.substring(closingBrace + 1);
    }

    /**
     * Compile a single .java source file using the system Java compiler.
     * Returns true if compilation succeeded with no errors.
     */
    private boolean compileHarness(Path sourceFile, Path outputDir, Consumer<String> terminalOutput) {
        return compileHarness(sourceFile, outputDir, terminalOutput, null);
    }

    private boolean compileHarness(Path sourceFile, Path outputDir, Consumer<String> terminalOutput, List<String> errorCollector) {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                String msg = "Không tìm thấy Java compiler (cần JDK, không phải JRE)";
                terminal("ERROR: " + msg, terminalOutput);
                if (errorCollector != null) errorCollector.add(msg);
                return false;
            }

            List<String> options = new ArrayList<>(Arrays.asList(
                "-d", outputDir.toString(),
                "-encoding", "UTF-8"
            ));

            // Build classpath: include all runtime dependencies (Playwright, Jackson, etc.)
            String classpath = System.getProperty("java.class.path");
            if (classpath != null && !classpath.isEmpty()) {
                options.add("-cp");
                options.add(classpath);
            }

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);

            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(sourceFile.toFile());
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fm, diagnostics, options, null, units
            );

            boolean success = Boolean.TRUE.equals(task.call());

            if (!success) {
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    String line = String.format("Compile %s [dòng %d]: %s",
                            d.getKind(), d.getLineNumber(), d.getMessage(null));
                    terminal("  " + line, terminalOutput);
                    if (errorCollector != null) {
                        errorCollector.add(line);
                    }
                }
            }

            fm.close();
            return success;
        } catch (Exception e) {
            terminal("Compile exception: " + e.getMessage(), terminalOutput);
            if (errorCollector != null) {
                errorCollector.add("Compile exception: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Load the compiled harness class via URLClassLoader and invoke its main method.
     * Supports both default-package and packaged classes (e.g. package com.test;).
     */
    private void runHarnessClass(Path classDir, String className) throws Throwable {
        URLClassLoader cl = new URLClassLoader(
            new URL[]{classDir.toUri().toURL()},
            getClass().getClassLoader()
        );

        try {
            Class<?> cls;
            try {
                cls = cl.loadClass(className);
            } catch (ClassNotFoundException e) {
                String fqcn = resolveFullyQualifiedClassName(classDir, className);
                if (fqcn != null) {
                    cls = cl.loadClass(fqcn);
                } else {
                    throw e;
                }
            }
            java.lang.reflect.Method main = cls.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException ite) {
            Throwable target = ite.getTargetException();
            throw (target != null ? target : ite);
        } finally {
            cl.close();
        }
    }

    /**
     * Recursively unwrap reflection, execution, or generic runtime wrapper exceptions.
     */
    public static Throwable unwrapCause(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            Throwable next = null;
            if (curr instanceof InvocationTargetException ite && ite.getTargetException() != null) {
                next = ite.getTargetException();
            } else if (curr instanceof ExecutionException ee && ee.getCause() != null) {
                next = ee.getCause();
            } else if (curr.getCause() != null && curr.getCause() != curr) {
                if (curr.getMessage() == null || curr instanceof RuntimeException) {
                    next = curr.getCause();
                }
            }
            if (next != null && next != curr) {
                curr = next;
            } else {
                break;
            }
        }
        return curr != null ? curr : t;
    }

    /**
     * Extract a concise, meaningful failure message (e.g. Playwright timeout with selector).
     */
    public static String extractFailureReason(Throwable t) {
        Throwable unwrapped = unwrapCause(t);
        String msg = unwrapped.getMessage();
        if (msg == null || msg.isBlank()) {
            Throwable c = unwrapped.getCause();
            while (c != null && c != unwrapped) {
                if (c.getMessage() != null && !c.getMessage().isBlank()) {
                    msg = c.getMessage();
                    break;
                }
                c = c.getCause();
            }
        }
        if (msg == null || msg.isBlank()) {
            return unwrapped.getClass().getSimpleName();
        }

        String cleanMsg = msg.trim();
        if (cleanMsg.contains("waiting for locator(")) {
            int start = cleanMsg.indexOf("waiting for locator(");
            int end = cleanMsg.indexOf(")", start);
            if (end > start) {
                String locatorPart = cleanMsg.substring(start, end + 1);
                String firstLine = cleanMsg.split("\\r?\\n")[0].trim();
                return firstLine + " [" + locatorPart + "]";
            }
        } else if (cleanMsg.contains("waiting for selector")) {
            int start = cleanMsg.indexOf("waiting for selector");
            int end = cleanMsg.indexOf("\n", start);
            String selectorPart = end > start ? cleanMsg.substring(start, end).trim() : cleanMsg.substring(start).trim();
            String firstLine = cleanMsg.split("\\r?\\n")[0].trim();
            return firstLine + " [" + selectorPart + "]";
        }

        String firstLine = cleanMsg.split("\\r?\\n")[0].trim();
        return firstLine.isEmpty() ? unwrapped.getClass().getSimpleName() : firstLine;
    }

    /**
     * Builds structured diagnosis log with steps to reproduce and troubleshooting guidance.
     * Backward-compatible overload without TestCase parameter.
     */
    public static String buildReproductionLog(Throwable t, String caseName, String scriptFile, String startUrl) {
        return buildReproductionLog(t, caseName, scriptFile, startUrl, null);
    }

    /**
     * Builds structured diagnosis log with clear error classification, clean error message,
     * actionable steps to reproduce, and contextual troubleshooting hints.
     * Raw application/framework stack traces are omitted to maintain clear, high-signal diagnostics.
     */
    public static String buildReproductionLog(Throwable t, String caseName, String scriptFile, String startUrl, TestCase testCase) {
        Throwable root = unwrapCause(t);
        StringBuilder sb = new StringBuilder();

        String errorCategory = categorizeError(root);
        String cleanMessage = sanitizeErrorMessage(root.getMessage());

        sb.append("❌ CHI TIẾT LỖI (Execution Error):\n");
        sb.append("   • Phân loại lỗi: ").append(errorCategory).append("\n");
        sb.append("   • Mã ngoại lệ: ").append(root.getClass().getSimpleName()).append("\n");
        if (cleanMessage != null && !cleanMessage.isBlank()) {
            sb.append("   • Thông báo lỗi: ").append(cleanMessage).append("\n");
        } else {
            sb.append("   • Thông báo lỗi: (Không có thông báo lỗi từ runtime)\n");
        }

        // Extract key locator or target if detected
        String extractedTarget = extractTargetLocator(root);
        if (extractedTarget != null && !extractedTarget.isBlank()) {
            sb.append("   • Phần tử gặp sự cố: ").append(extractedTarget).append("\n");
        }

        // Find relevant stack frame in test script or harness (only test script line, omitting runner plumbing)
        StackTraceElement scriptFrame = null;
        for (StackTraceElement elem : root.getStackTrace()) {
            String cn = elem.getClassName();
            String fn = elem.getFileName();
            if (fn != null && fn.endsWith(".java")
                    && !cn.startsWith("com.studio.service.")
                    && !cn.startsWith("com.studio.runtime.")
                    && !cn.startsWith("java.lang.reflect.")
                    && !cn.startsWith("jdk.internal.")
                    && !cn.startsWith("org.junit.")) {
                scriptFrame = elem;
                break;
            }
        }

        sb.append("\n🔍 CÁCH TÁI HIỆN (Steps to Reproduce):\n");
        int step = 1;
        if (startUrl != null && !startUrl.isBlank()) {
            sb.append(String.format("   %d. Mở trình duyệt và truy cập URL: %s\n", step++, startUrl));
        }
        if (scriptFile != null && !scriptFile.isBlank()) {
            sb.append(String.format("   %d. Mở kịch bản kiểm thử: %s%s\n",
                    step++, scriptFile,
                    (caseName != null && !caseName.isBlank()) ? " (Test case: " + caseName + ")" : ""));
        } else if (caseName != null && !caseName.isBlank()) {
            sb.append(String.format("   %d. Test case: %s\n", step++, caseName));
        }

        if (testCase != null && testCase.getTestSteps() != null && !testCase.getTestSteps().isEmpty()) {
            sb.append("   • Các bước thực hiện tuần tự:\n");
            for (TestStep ts : testCase.getTestSteps()) {
                String action = ts.getAction();
                String target = ts.getTarget();
                String val = ts.getTestData();
                String desc = ts.getDescription();
                StringBuilder stepDesc = new StringBuilder();
                if (desc != null && !desc.isBlank()) {
                    stepDesc.append(desc);
                } else {
                    stepDesc.append("[").append(action).append("]");
                    if (target != null && !target.isBlank()) {
                        stepDesc.append(" trên '").append(target).append("'");
                    }
                    if (val != null && !val.isBlank()) {
                        stepDesc.append(" (Dữ liệu: \"").append(val).append("\")");
                    }
                }
                int num = ts.getStepNumber() > 0 ? ts.getStepNumber() : step++;
                sb.append(String.format("      %d. %s\n", num, stepDesc));
            }
        }

        if (scriptFrame != null) {
            sb.append(String.format("   • Điểm dừng lỗi: %s.%s() tại dòng %d (file %s)\n",
                    scriptFrame.getClassName(), scriptFrame.getMethodName(),
                    scriptFrame.getLineNumber(), scriptFrame.getFileName()));
        }

        // Contextual advice
        String lowerMsg = (cleanMessage != null ? cleanMessage : "").toLowerCase();
        String rootClass = root.getClass().getSimpleName();
        if (lowerMsg.contains("timeout") || lowerMsg.contains("waiting for locator")
                || rootClass.contains("Timeout")) {
            sb.append("\n💡 GỢI Ý XỬ LÝ (Troubleshooting Hint):\n");
            sb.append("   • Playwright chờ phần tử UI xuất hiện nhưng hết thời gian chờ (Timeout 30s).\n");
            sb.append("   • Kiểm tra ảnh chụp màn hình kiểm chứng đính kèm: Nếu trang đang dừng ở màn hình Đăng nhập (hoặc popup/dialog), kịch bản cần thực hiện bước đăng nhập hoặc xử lý popup trước khi tương tác các phần tử bên trong.\n");
            sb.append("   • Kiểm tra lại bộ chọn (CSS / XPath locator) xem có chính xác với DOM hiện tại không.\n");
        } else if (lowerMsg.contains("target closed") || lowerMsg.contains("page closed") || rootClass.contains("Closed")) {
            sb.append("\n💡 GỢI Ý XỬ LÝ (Troubleshooting Hint):\n");
            sb.append("   • Trình duyệt hoặc tab trang web đã bị đóng bất ngờ trước khi hoàn thành các bước.\n");
        } else if (lowerMsg.contains("strict mode violation")) {
            sb.append("\n💡 GỢI Ý XỬ LÝ (Troubleshooting Hint):\n");
            sb.append("   • Bộ chọn trả về nhiều hơn một phần tử (Strict mode violation). Hãy thêm .first() hoặc dùng bộ chọn đặc thù hơn.\n");
        } else if (root instanceof AssertionError || rootClass.contains("Assertion")) {
            sb.append("\n💡 GỢI Ý XỬ LÝ (Troubleshooting Hint):\n");
            sb.append("   • Dữ liệu hoặc trạng thái thực tế trên giao diện không thỏa mãn điều kiện mong đợi (Assertion Failed).\n");
        } else if (lowerMsg.contains("intercepted") || lowerMsg.contains("pointer-events")) {
            sb.append("\n💡 GỢI Ý XỬ LÝ (Troubleshooting Hint):\n");
            sb.append("   • Phần tử đang bị che khuất bởi lớp phủ (overlay), thanh header cố định hoặc dialog khác.\n");
        }

        return sb.toString();
    }

    /**
     * Categorizes exceptions into user-friendly diagnostic error types.
     */
    public static String categorizeError(Throwable root) {
        if (root == null) return "Lỗi không xác định (Unknown Error)";
        String className = root.getClass().getSimpleName();
        String msg = (root.getMessage() != null ? root.getMessage() : "").toLowerCase();

        if (className.contains("Timeout") || msg.contains("timeout 30000ms") || msg.contains("timeout exceeded")) {
            return "TimeoutError (Quá thời gian chờ phần tử giao diện)";
        }
        if (root instanceof AssertionError || className.contains("Assertion")) {
            return "AssertionError (Kết quả kiểm chứng không thỏa mãn)";
        }
        if (msg.contains("strict mode violation")) {
            return "StrictModeViolation (Bộ chọn trùng lặp nhiều phần tử)";
        }
        if (msg.contains("target closed") || msg.contains("page closed") || msg.contains("browser has been closed")) {
            return "BrowserClosed (Trình duyệt hoặc tab bị đóng đột ngột)";
        }
        if (msg.contains("net::err") || msg.contains("ns_error") || msg.contains("navigation failed")) {
            return "NavigationError (Lỗi tải trang web hoặc mạng)";
        }
        if (msg.contains("intercepted") || msg.contains("pointer-events") || msg.contains("another element would receive the click")) {
            return "ElementClickIntercepted (Phần tử bị che khuất hoặc modal chèn lên)";
        }
        if (msg.contains("element is not visible") || msg.contains("hidden")) {
            return "ElementNotVisible (Phần tử bị ẩn hoặc chưa hiển thị)";
        }
        if (msg.contains("not found") || msg.contains("waiting for locator") || msg.contains("no element matches")) {
            return "ElementNotFound (Không tìm thấy phần tử giao diện)";
        }
        return className + " (Lỗi thực thi kịch bản kiểm thử)";
    }

    /**
     * Extracts targeted locator or selector string from error message.
     */
    public static String extractTargetLocator(Throwable root) {
        if (root == null || root.getMessage() == null) return null;
        String msg = root.getMessage();
        if (msg.contains("waiting for locator(\"")) {
            int start = msg.indexOf("waiting for locator(\"") + "waiting for locator(\"".length();
            int end = msg.indexOf("\")", start);
            if (end > start) {
                return msg.substring(start, end);
            }
        } else if (msg.contains("waiting for locator(")) {
            int start = msg.indexOf("waiting for locator(") + "waiting for locator(".length();
            int end = msg.indexOf(")", start);
            if (end > start) {
                return msg.substring(start, end);
            }
        } else if (msg.contains("waiting for selector \"")) {
            int start = msg.indexOf("waiting for selector \"") + "waiting for selector \"".length();
            int end = msg.indexOf("\"", start);
            if (end > start) {
                return msg.substring(start, end);
            }
        } else if (msg.contains("waiting for selector")) {
            int start = msg.indexOf("waiting for selector");
            int end = msg.indexOf("\n", start);
            return (end > start) ? msg.substring(start, end).trim() : msg.substring(start).trim();
        }
        return null;
    }

    /**
     * Sanitizes raw error message by eliminating internal stack traces and truncating noisy call logs.
     */
    public static String sanitizeErrorMessage(String msg) {
        if (msg == null || msg.isBlank()) return "";
        String trimmed = msg.trim();

        // Strip embedded java stack traces
        int atIdx = trimmed.indexOf("\n\tat ");
        if (atIdx >= 0) {
            trimmed = trimmed.substring(0, atIdx).trim();
        }
        int atIdx2 = trimmed.indexOf("\tat ");
        if (atIdx2 >= 0) {
            trimmed = trimmed.substring(0, atIdx2).trim();
        }

        // Shorten long Playwright call logs to the summary + first 3 lines
        int callLogIdx = trimmed.indexOf("Call log:");
        if (callLogIdx > 0) {
            String header = trimmed.substring(0, callLogIdx).trim();
            String callLogPart = trimmed.substring(callLogIdx);
            String[] lines = callLogPart.split("\\r?\\n");
            StringBuilder limited = new StringBuilder(header);
            if (!header.isEmpty()) limited.append("\n");
            int maxLines = Math.min(lines.length, 4);
            for (int i = 0; i < maxLines; i++) {
                limited.append("   ").append(lines[i].trim()).append("\n");
            }
            if (lines.length > 4) {
                limited.append("   ... (rút gọn log gọi Playwright)");
            }
            trimmed = limited.toString().trim();
        }
        return trimmed;
    }

    private String resolveFullyQualifiedClassName(Path classDir, String simpleClassName) {
        try (var stream = Files.walk(classDir)) {
            return stream
                .filter(p -> p.getFileName().toString().equals(simpleClassName + ".class"))
                .findFirst()
                .map(p -> {
                    Path rel = classDir.relativize(p);
                    String pathStr = rel.toString();
                    if (pathStr.endsWith(".class")) {
                        pathStr = pathStr.substring(0, pathStr.length() - 6);
                    }
                    return pathStr.replace('/', '.').replace('\\', '.');
                })
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Harness always writes {@code <case>.png}. Rename to the status-suffixed path
     * expected by reports ({@code <case>_PASSED.png} / {@code _FAILED.png}).
     */
    private static String finalizeScreenshot(Path rawFromHarness, Path preferred) {
        try {
            if (rawFromHarness != null && Files.isRegularFile(rawFromHarness)) {
                if (preferred != null && !rawFromHarness.equals(preferred)) {
                    if (preferred.getParent() != null) {
                        Files.createDirectories(preferred.getParent());
                    }
                    Files.move(rawFromHarness, preferred,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return preferred.toString();
                }
                return rawFromHarness.toString();
            }
            if (preferred != null && Files.isRegularFile(preferred)) {
                return preferred.toString();
            }
            if (rawFromHarness != null && Files.isRegularFile(rawFromHarness)) {
                return rawFromHarness.toString();
            }
        } catch (Exception ignored) {
            if (rawFromHarness != null && Files.isRegularFile(rawFromHarness)) {
                return rawFromHarness.toString();
            }
        }
        return preferred != null ? preferred.toString() : null;
    }

    /** Forward a message to the terminal output callback (if any) and stdout. */
    private void markCaseCancelled(CaseResult cr, RunReport report, long startMs,
                                   String screenshotPath, Consumer<String> terminalOutput) {
        if ("PASSED".equals(cr.getStatus()) || "FAILED".equals(cr.getStatus())
                || "SKIPPED".equals(cr.getStatus()) || "ERROR".equals(cr.getStatus())) {
            RunProgressService.caseFinished(cr);
            return;
        }
        cr.setStatus("ERROR");
        cr.setFailureReason(RunCancelService.CANCEL_REASON);
        cr.setDurationMs(System.currentTimeMillis() - startMs);
        if (screenshotPath != null) {
            Path preferred = Path.of(screenshotPath);
            Path raw = preferred.getFileName() != null
                    ? preferred.resolveSibling(
                    preferred.getFileName().toString()
                            .replace("_FAILED.png", ".png")
                            .replace("_PASSED.png", ".png"))
                    : preferred;
            cr.setScreenshotPath(finalizeScreenshot(raw, preferred));
        }
        report.setFailed(report.getFailed() + 1);
        terminal("⏹ Dừng: " + cr.getTestCaseName() + " — " + RunCancelService.CANCEL_REASON, terminalOutput);
        RunProgressService.caseFinished(cr);
    }

    private void applyCancelToPending(List<CaseResult> allCases, Consumer<String> terminalOutput) {
        terminal("⏹ Đã dừng chạy — bỏ qua các case còn lại", terminalOutput);
        for (CaseResult cr : allCases) {
            if ("PENDING".equals(cr.getStatus())) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason(RunCancelService.CANCEL_REASON);
                terminal("SKIP: " + cr.getTestCaseName() + " — " + RunCancelService.CANCEL_REASON, terminalOutput);
                RunProgressService.caseFinished(cr);
            }
        }
    }

    public static String expandStepGroups(String scriptCode, StepGroupStore store) {
        if (scriptCode == null || !scriptCode.contains("@include(") || store == null) {
            return scriptCode;
        }
        Pattern p = Pattern.compile("(?:/{2}|/\\*)\\s*@include\\([\"']([^\"']+)[\"']\\)\\s*(?:\\*/)?");
        Matcher m = p.matcher(scriptCode);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String groupId = m.group(1);
            Optional<StepGroup> group = store.findById(groupId);
            String expansion = "// [StepGroup: " + groupId + " not found]";
            if (group.isPresent()) {
                StringBuilder grpCode = new StringBuilder("// [StepGroup: " + group.get().getName() + "]\n");
                for (TestStep step : group.get().getSteps()) {
                    grpCode.append("        // Step: ").append(step.getDescription()).append("\n");
                }
                expansion = grpCode.toString();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(expansion));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void terminal(String msg, Consumer<String> output) {
        String sanitized = com.studio.util.SecuritySanitizer.sanitize(msg);
        if (output != null) output.accept(sanitized);
        System.out.println("[Runner] " + sanitized);
    }
}
