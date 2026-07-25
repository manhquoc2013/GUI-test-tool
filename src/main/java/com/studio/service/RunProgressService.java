package com.studio.service;

import com.studio.model.CaseResult;
import com.studio.model.RunReport;
import com.studio.util.StudioPaths;
import javafx.application.Platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live run progress overlays for Workspace / Explorer.
 * Runner thread writes; UI listeners refresh on FX thread.
 */
public final class RunProgressService {

    public static final long CLEAR_DELAY_MS = 4000L;

    public record ProgressEntry(
            String caseKey,
            String caseName,
            String groupPath,
            String suiteName,
            String suiteDir,
            String status
    ) {}

    private static final Map<String, ProgressEntry> BY_CASE = new ConcurrentHashMap<>();
    private static final AtomicBoolean RUN_ACTIVE = new AtomicBoolean(false);
    private static final AtomicReference<String> ACTIVE_CASE_KEY = new AtomicReference<>();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Thread> CLEAR_THREAD = new AtomicReference<>();

    private RunProgressService() {}

    public static String caseKey(CaseResult cr) {
        if (cr == null) return "";
        if (cr.getTestCaseId() != null && !cr.getTestCaseId().isBlank()) {
            return cr.getTestCaseId().trim();
        }
        return safe(cr.getGroupPath()) + "/" + safe(cr.getSuiteName()) + "/" + safe(cr.getTestCaseName());
    }

    public static void beginRun(String scope, Path scopePath) {
        cancelClearTimer();
        BY_CASE.clear();
        ACTIVE_CASE_KEY.set(null);
        RUN_ACTIVE.set(true);
        notifyListeners();
    }

    public static void caseStarted(CaseResult cr) {
        if (cr == null) return;
        put(cr, "RUNNING");
        ACTIVE_CASE_KEY.set(caseKey(cr));
        notifyListeners();
    }

    public static void caseFinished(CaseResult cr) {
        if (cr == null) return;
        String status = cr.getStatus() != null ? cr.getStatus().trim().toUpperCase() : "ERROR";
        if ("PENDING".equals(status) || status.isEmpty()) {
            status = "ERROR";
        }
        put(cr, status);
        String key = caseKey(cr);
        if (key.equals(ACTIVE_CASE_KEY.get())) {
            ACTIVE_CASE_KEY.set(null);
        }
        notifyListeners();
    }

    public static void endRun(RunReport report) {
        RUN_ACTIVE.set(false);
        ACTIVE_CASE_KEY.set(null);
        notifyListeners();
        scheduleClear(CLEAR_DELAY_MS);
    }

    public static void clearNow() {
        cancelClearTimer();
        BY_CASE.clear();
        ACTIVE_CASE_KEY.set(null);
        RUN_ACTIVE.set(false);
        notifyListeners();
    }

    public static boolean isRunActive() {
        return RUN_ACTIVE.get();
    }

    public static boolean hasOverlay() {
        return !BY_CASE.isEmpty();
    }

    public static String statusForCaseId(String caseId) {
        if (caseId == null || caseId.isBlank()) return null;
        ProgressEntry e = BY_CASE.get(caseId.trim());
        return e != null ? e.status() : null;
    }

    /** Workspace: match by case id, else suite name + case name. */
    public static String overlayForCase(String caseId, String suiteName, String caseName) {
        String byId = statusForCaseId(caseId);
        if (byId != null) return byId;
        if (caseName == null || caseName.isBlank()) return null;
        for (ProgressEntry e : BY_CASE.values()) {
            if (!safe(caseName).equals(e.caseName())) continue;
            if (suiteName != null && !suiteName.isBlank() && !safe(suiteName).equals(e.suiteName())) {
                continue;
            }
            return e.status();
        }
        return null;
    }

    public static String overlayForSuiteDir(String suiteDirAbsolute) {
        if (suiteDirAbsolute == null || suiteDirAbsolute.isBlank()) return null;
        Path suiteDir = Path.of(suiteDirAbsolute).toAbsolutePath().normalize();
        String normalized = suiteDir.toString();
        String expectedGroup = relativeGroupPath(suiteDir);
        String suiteName = suiteDir.getFileName() != null ? suiteDir.getFileName().toString() : "";
        List<String> statuses = new ArrayList<>();
        for (ProgressEntry e : BY_CASE.values()) {
            if (e.suiteDir() != null
                    && Path.of(e.suiteDir()).toAbsolutePath().normalize().toString().equals(normalized)) {
                statuses.add(e.status());
            } else if (safe(expectedGroup).equals(e.groupPath()) && safe(suiteName).equals(e.suiteName())) {
                statuses.add(e.status());
            }
        }
        return aggregate(statuses);
    }

    public static String overlayForGroupDir(String groupDirAbsolute) {
        if (groupDirAbsolute == null || groupDirAbsolute.isBlank()) return null;
        Path groupDir = Path.of(groupDirAbsolute).toAbsolutePath().normalize();
        Path project = StudioPaths.getProjectDir().toAbsolutePath().normalize();
        if (groupDir.equals(project)) {
            return aggregate(BY_CASE.values().stream().map(ProgressEntry::status).toList());
        }
        String gp;
        if (!groupDir.startsWith(project)) {
            gp = groupDir.getFileName() != null ? groupDir.getFileName().toString() : "";
        } else {
            gp = project.relativize(groupDir).toString().replace('\\', '/');
        }
        String prefix = gp.isEmpty() ? "" : gp + "/";
        List<String> statuses = new ArrayList<>();
        for (ProgressEntry e : BY_CASE.values()) {
            String eg = safe(e.groupPath());
            if (eg.equals(gp) || (!gp.isEmpty() && eg.startsWith(prefix))) {
                statuses.add(e.status());
            }
        }
        return aggregate(statuses);
    }

    public static Collection<ProgressEntry> entries() {
        return List.copyOf(BY_CASE.values());
    }

    public static void addListener(Runnable listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    static void resetForTests() {
        clearNow();
        LISTENERS.clear();
    }

    /** Visible for tests — delay 0 clears immediately. */
    static void scheduleClear(long delayMs) {
        cancelClearTimer();
        if (delayMs <= 0) {
            BY_CASE.clear();
            ACTIVE_CASE_KEY.set(null);
            RUN_ACTIVE.set(false);
            notifyListeners();
            return;
        }
        Thread clearer = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (CLEAR_THREAD.get() != Thread.currentThread()) {
                return;
            }
            try {
                if (Platform.isFxApplicationThread()) {
                    clearNow();
                } else {
                    Platform.runLater(RunProgressService::clearNow);
                }
            } catch (IllegalStateException noToolkit) {
                clearNow();
            }
        }, "run-progress-clear");
        clearer.setDaemon(true);
        CLEAR_THREAD.set(clearer);
        clearer.start();
    }

    private static void put(CaseResult cr, String status) {
        String key = caseKey(cr);
        BY_CASE.put(key, new ProgressEntry(
                key,
                safe(cr.getTestCaseName()),
                safe(cr.getGroupPath()),
                safe(cr.getSuiteName()),
                resolveSuiteDirString(cr),
                status));
    }

    private static String aggregate(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) return null;
        boolean running = false, failed = false, error = false, passed = false, skipped = false;
        for (String s : statuses) {
            String k = s != null ? s.toUpperCase() : "";
            switch (k) {
                case "RUNNING" -> running = true;
                case "FAILED", "FAIL" -> failed = true;
                case "ERROR" -> error = true;
                case "PASSED", "PASS", "OK" -> passed = true;
                case "SKIPPED", "SKIP" -> skipped = true;
                default -> {
                }
            }
        }
        if (running) return "RUNNING";
        if (failed) return "FAILED";
        if (error) return "ERROR";
        if (passed) return "PASSED";
        if (skipped) return "SKIPPED";
        return null;
    }

    private static String resolveSuiteDirString(CaseResult cr) {
        if (cr == null) return null;
        try {
            if (cr.getSuiteDir() != null && !cr.getSuiteDir().isBlank()) {
                return Path.of(cr.getSuiteDir()).toAbsolutePath().normalize().toString();
            }
            Path project = StudioPaths.getProjectDir().toAbsolutePath().normalize();
            String gp = safe(cr.getGroupPath());
            String sn = safe(cr.getSuiteName());
            if (sn.isEmpty()) return null;
            Path suite = gp.isEmpty() ? project.resolve(sn) : project.resolve(gp).resolve(sn);
            return suite.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String relativeGroupPath(Path suiteDir) {
        if (suiteDir == null) return "";
        Path projectDir = StudioPaths.getProjectDir().toAbsolutePath().normalize();
        Path parent = suiteDir.toAbsolutePath().normalize().getParent();
        if (parent == null || parent.equals(projectDir)) return "";
        if (!parent.startsWith(projectDir)) {
            return parent.getFileName() != null ? parent.getFileName().toString() : "";
        }
        return projectDir.relativize(parent).toString().replace('\\', '/');
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static void cancelClearTimer() {
        Thread t = CLEAR_THREAD.getAndSet(null);
        if (t != null) {
            t.interrupt();
        }
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                if (Platform.isFxApplicationThread()) {
                    listener.run();
                } else {
                    Platform.runLater(listener);
                }
            } catch (IllegalStateException noToolkit) {
                listener.run();
            } catch (Exception ignored) {
            }
        }
    }
}
