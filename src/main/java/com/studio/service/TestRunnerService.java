package com.studio.service;

import com.studio.model.*;
import com.studio.runtime.OptimizedSelfHealingExecutor;
import com.studio.runtime.StudioRuntime;
import com.studio.store.ProjectStore;
import com.studio.store.ReportStore;
import com.studio.store.SettingsStore;
import com.studio.util.StudioPaths;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

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
    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public TestRunnerService(ProjectStore projectStore, ReportStore reportStore) {
        this(projectStore, reportStore, new SettingsStore(), new OpenAiClient());
    }

    public TestRunnerService(ProjectStore projectStore, ReportStore reportStore,
                             SettingsStore settingsStore, OpenAiClient openAiClient) {
        this.projectStore = projectStore;
        this.reportStore = reportStore;
        this.settingsStore = settingsStore != null ? settingsStore : new SettingsStore();
        this.openAiClient = openAiClient != null ? openAiClient : new OpenAiClient();
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

                // Prepare screenshot paths
                Path ssPassed = screenshotDir.resolve(cr.getTestCaseName() + "_PASSED.png");
                Path ssFailed = screenshotDir.resolve(cr.getTestCaseName() + "_FAILED.png");
                String harnessScreenshotBase = screenshotDir.resolve(cr.getTestCaseName()).toString();

                // Transform script for in-process execution
                String harnessClass = ScriptHarnessTransformer.sanitizeClassName(
                        cr.getTestCaseId() + "_Run");
                String transformed = ScriptHarnessTransformer.transform(
                        scriptCode, useShared, harnessScreenshotBase, harnessClass);

                // Inject soft-assert block into transformed script (inside try, before finally)
                String assertBlock = AssertCodeGenerator.generateAssertBlock(activeAsserts);
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
                boolean compiled = compileHarness(harnessFile, harnessDir, terminalOutput);
                if (!compiled) {
                    cr.setStatus("ERROR");
                    cr.setFailureReason("Compilation failed");
                    cr.setLogExcerpt("See terminal for compilation errors");
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

                    if (failures > 0) {
                        cr.setStatus("FAILED");
                        cr.setFailureReason(failures + " assertion(s) failed");
                        cr.setScreenshotPath(finalizeScreenshot(rawScreenshot, ssFailed));
                        report.setFailed(report.getFailed() + 1);
                    } else {
                        cr.setStatus("PASSED");
                        cr.setScreenshotPath(finalizeScreenshot(rawScreenshot, ssPassed));
                        report.setPassed(report.getPassed() + 1);
                    }
                    cr.setDurationMs(duration);
                    cr.setExitCode(0);

                    // Load assert results for detail view
                    cr.setAssertResults(AssertResultsScorer.loadResults(assertResultsFile));

                    terminal(String.format("  %s: %s (%dms)", cr.getTestCaseName(), cr.getStatus(), duration), terminalOutput);
                    RunProgressService.caseFinished(cr);

                } catch (Exception e) {
                    if (RunCancelService.isCancelRequested()) {
                        markCaseCancelled(cr, report, start, ssFailed.toString(), terminalOutput);
                        applyCancelToPending(allCases, terminalOutput);
                        break;
                    }
                    cr.setStatus("FAILED");
                    cr.setFailureReason(e.getMessage() != null ? e.getMessage() : "Unknown error");
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
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                    });
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
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                terminal("ERROR: Không tìm thấy Java compiler (cần JDK, không phải JRE)", terminalOutput);
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
                    terminal("  Compile: " + d.getKind() + " line " + d.getLineNumber()
                            + ": " + d.getMessage(null), terminalOutput);
                }
            }

            fm.close();
            return success;
        } catch (Exception e) {
            terminal("Compile exception: " + e.getMessage(), terminalOutput);
            return false;
        }
    }

    /**
     * Load the compiled harness class via URLClassLoader and invoke its main method.
     * Supports both default-package and packaged classes (e.g. package com.test;).
     */
    private void runHarnessClass(Path classDir, String className) throws Exception {
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
        } finally {
            cl.close();
        }
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
                    Files.createDirectories(preferred.getParent());
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

    private void terminal(String msg, Consumer<String> output) {
        if (output != null) output.accept(msg);
        System.out.println("[Runner] " + msg);
    }
}
