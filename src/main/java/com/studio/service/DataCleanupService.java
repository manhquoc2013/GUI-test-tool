package com.studio.service;

import com.studio.db.DatabaseManager;
import com.studio.db.ReportDbService;
import com.studio.model.RunReport;
import com.studio.store.AiChatHistoryStore;
import com.studio.store.ReportStore;
import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to execute system data maintenance, clean temporary files, execution reports,
 * and AI chat sessions while strictly preserving system settings.
 */
public class DataCleanupService {

    private final DatabaseManager dbManager;
    private final ReportStore reportStore;

    public DataCleanupService() {
        this(DatabaseManager.getInstance(), new ReportStore());
    }

    public DataCleanupService(DatabaseManager dbManager, ReportStore reportStore) {
        this.dbManager = dbManager;
        this.reportStore = reportStore;
    }

    public static class CleanupOptions {
        private boolean cleanReports = true;
        private int keepRecentReports = 0; // 0 = delete all, 5, 10
        private boolean cleanAiChat = true;
        private boolean cleanWorkDir = true;

        public CleanupOptions() {}

        public CleanupOptions(boolean cleanReports, int keepRecentReports, boolean cleanAiChat, boolean cleanWorkDir) {
            this.cleanReports = cleanReports;
            this.keepRecentReports = keepRecentReports;
            this.cleanAiChat = cleanAiChat;
            this.cleanWorkDir = cleanWorkDir;
        }

        public boolean isCleanReports() { return cleanReports; }
        public void setCleanReports(boolean cleanReports) { this.cleanReports = cleanReports; }

        public int getKeepRecentReports() { return keepRecentReports; }
        public void setKeepRecentReports(int keepRecentReports) { this.keepRecentReports = keepRecentReports; }

        public boolean isCleanAiChat() { return cleanAiChat; }
        public void setCleanAiChat(boolean cleanAiChat) { this.cleanAiChat = cleanAiChat; }

        public boolean isCleanWorkDir() { return cleanWorkDir; }
        public void setCleanWorkDir(boolean cleanWorkDir) { this.cleanWorkDir = cleanWorkDir; }
    }

    public static class CleanupResult {
        private int deletedReportsCount;
        private int deletedChatCount;
        private int deletedWorkFilesCount;
        private long freedBytes;
        private boolean settingsPreserved = true;

        public int getDeletedReportsCount() { return deletedReportsCount; }
        public int getDeletedChatCount() { return deletedChatCount; }
        public int getDeletedWorkFilesCount() { return deletedWorkFilesCount; }
        public long getFreedBytes() { return freedBytes; }
        public boolean isSettingsPreserved() { return settingsPreserved; }

        public String formatFreedSpace() {
            if (freedBytes < 1024) return freedBytes + " B";
            if (freedBytes < 1024 * 1024) return String.format("%.1f KB", freedBytes / 1024.0);
            return String.format("%.2f MB", freedBytes / (1024.0 * 1024.0));
        }
    }

    public static class DiskUsageStats {
        public int reportsCount;
        public long reportsBytes;
        public int chatFilesCount;
        public long chatBytes;
        public int workFilesCount;
        public long workBytes;

        public long getTotalBytes() {
            return reportsBytes + chatBytes + workBytes;
        }

        public String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Measure disk usage across maintenance targets.
     */
    public DiskUsageStats calculateDiskUsage() {
        DiskUsageStats stats = new DiskUsageStats();

        // 1. Reports directory
        Path reportsDir = StudioPaths.getReportsDir();
        if (Files.exists(reportsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsDir)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        stats.reportsCount++;
                        stats.reportsBytes += computeDirectorySize(p);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Chat history files
        Path projDir = StudioPaths.getProjectDir();
        if (Files.exists(projDir)) {
            try (var walk = Files.walk(projDir)) {
                walk.filter(p -> p.getFileName().toString().equals(AiChatHistoryStore.CHAT_HISTORY_FILE))
                        .forEach(p -> {
                            stats.chatFilesCount++;
                            try { stats.chatBytes += Files.size(p); } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}
        }
        Path defaultChat = StudioPaths.getDataDir().resolve("ai_chat_default.json");
        if (Files.exists(defaultChat)) {
            stats.chatFilesCount++;
            try { stats.chatBytes += Files.size(defaultChat); } catch (Exception ignored) {}
        }

        // 3. Work directory & converted docs directory
        Path workDir = StudioPaths.getWorkDir();
        if (Files.exists(workDir)) {
            try (var walk = Files.walk(workDir)) {
                walk.filter(p -> !p.getFileName().toString().equals(".gitkeep") && !Files.isDirectory(p))
                        .forEach(p -> {
                            stats.workFilesCount++;
                            try { stats.workBytes += Files.size(p); } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}
        }
        Path convertedDocs = StudioPaths.getDataDir().resolve("converted_docs");
        if (Files.exists(convertedDocs)) {
            try (var walk = Files.walk(convertedDocs)) {
                walk.filter(p -> !Files.isDirectory(p))
                        .forEach(p -> {
                            stats.workFilesCount++;
                            try { stats.workBytes += Files.size(p); } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}
        }

        return stats;
    }

    /**
     * Execute data cleanup according to selected options.
     * System settings are strictly guarded and never deleted.
     * Aborts with IllegalStateException if an in-process test run is currently active.
     */
    public CleanupResult executeCleanup(CleanupOptions options) {
        if (RunCancelService.isRunActive()) {
            throw new IllegalStateException("Không thể dọn dẹp dữ liệu khi kiểm thử đang chạy.");
        }
        CleanupResult result = new CleanupResult();
        if (options == null) return result;

        // 1. Clean Reports
        if (options.isCleanReports()) {
            cleanReports(options.getKeepRecentReports(), result);
        }

        // 2. Clean AI Chat
        if (options.isCleanAiChat()) {
            cleanAiChat(result);
        }

        // 3. Clean Work Directory
        if (options.isCleanWorkDir()) {
            cleanWorkDirectory(result);
        }

        return result;
    }

    private void cleanReports(int keepCount, CleanupResult result) {
        try {
            List<RunReport> all = reportStore.listReports();
            if (all.isEmpty()) return;

            // Sort newest first
            all.sort((a, b) -> {
                String sa = a.getStartedAt() != null ? a.getStartedAt() : "";
                String sb = b.getStartedAt() != null ? b.getStartedAt() : "";
                return sb.compareTo(sa);
            });

            Set<String> keepRunIds = new HashSet<>();
            if (keepCount > 0) {
                for (int i = 0; i < Math.min(keepCount, all.size()); i++) {
                    keepRunIds.add(all.get(i).getRunId());
                }
            }

            for (RunReport r : all) {
                String runId = r.getRunId();
                if (keepRunIds.contains(runId)) {
                    continue; // Retain
                }

                Path reportDir = StudioPaths.getReportsDir().resolve(runId);
                long dirSize = computeDirectorySize(reportDir);

                try {
                    reportStore.deleteReport(runId);
                    result.deletedReportsCount++;
                    result.freedBytes += dirSize;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void cleanAiChat(CleanupResult result) {
        // 1. Scan and delete all .ai_chat.json files
        Path projDir = StudioPaths.getProjectDir();
        if (Files.exists(projDir)) {
            try (var walk = Files.walk(projDir)) {
                walk.filter(p -> p.getFileName().toString().equals(AiChatHistoryStore.CHAT_HISTORY_FILE))
                        .forEach(p -> {
                            try {
                                long sz = Files.size(p);
                                Files.deleteIfExists(p);
                                result.deletedChatCount++;
                                result.freedBytes += sz;
                            } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}
        }

        Path defaultChat = StudioPaths.getDataDir().resolve("ai_chat_default.json");
        if (Files.exists(defaultChat)) {
            try {
                long sz = Files.size(defaultChat);
                Files.deleteIfExists(defaultChat);
                result.deletedChatCount++;
                result.freedBytes += sz;
            } catch (Exception ignored) {}
        }

        // 2. Clear H2 ai_chat_messages table
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ai_chat_messages")) {
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private void cleanWorkDirectory(CleanupResult result) {
        Path workDir = StudioPaths.getWorkDir();
        cleanDirectoryFiles(workDir, result, true);

        Path convertedDocs = StudioPaths.getDataDir().resolve("converted_docs");
        cleanDirectoryFiles(convertedDocs, result, false);
    }

    private void cleanDirectoryFiles(Path dir, CleanupResult result, boolean preserveGitKeep) {
        if (dir == null || !Files.exists(dir)) return;

        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir) && (!preserveGitKeep || !p.getFileName().toString().equals(".gitkeep")))
                    .forEach(p -> {
                        try {
                            if (!Files.isDirectory(p)) {
                                long sz = Files.size(p);
                                result.freedBytes += sz;
                                result.deletedWorkFilesCount++;
                            }
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }

    private long computeDirectorySize(Path dir) {
        if (dir == null || !Files.exists(dir)) return 0;
        AtomicLong size = new AtomicLong(0);
        try (var walk = Files.walk(dir)) {
            walk.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                try { size.addAndGet(Files.size(p)); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        return size.get();
    }
}

