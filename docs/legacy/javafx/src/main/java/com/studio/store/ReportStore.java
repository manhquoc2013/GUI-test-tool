package com.studio.store;

import com.studio.db.DatabaseManager;
import com.studio.db.LocalDataIndexService;
import com.studio.db.ReportDbService;
import com.studio.model.RunReport;
import com.studio.service.AiLoggingService;
import com.studio.util.StudioPaths;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportStore {

    private static final ObjectMapper mapper = ProjectStore.DEFAULT_MAPPER;
    private static final String REPORT_JSON = "report.json";
    /** Upper bound for the DB side of the merge; the disk scan is always complete. */
    private static final int DB_RECENT_REPORTS_LIMIT = 100;

    private final LocalDataIndexService indexService = new LocalDataIndexService();
    private final ReportDbService reportDbService = new ReportDbService();

    public ReportStore() {
    }

    public void saveReport(RunReport report) throws IOException {
        Path reportDir = StudioPaths.getReportsDir().resolve(report.getRunId());
        Files.createDirectories(reportDir);
        ProjectStore.writeJsonAtomic(reportDir.resolve(REPORT_JSON), report);
        indexService.indexReport(report);
    }

    public RunReport loadReport(String runId) throws IOException {
        Path reportFile = StudioPaths.getReportsDir().resolve(runId).resolve(REPORT_JSON);
        if (!Files.exists(reportFile)) {
            throw new IOException("Report not found: " + runId);
        }
        return mapper.readValue(reportFile.toFile(), RunReport.class);
    }

    /**
     * Lists every report that exists on disk, merged with the DB mirror.
     *
     * <p>The disk is authoritative: a report the DB never indexed (failed insert, restored backup,
     * file copied in) is still listed and re-indexed, and a DB row whose folder disappeared is not
     * shown. A single unreadable {@code report.json} is skipped with a warning instead of failing
     * the whole history.</p>
     */
    public List<RunReport> listReports() throws IOException {
        Path reportsDir = StudioPaths.getReportsDir();
        Map<String, RunReport> byId = new LinkedHashMap<>();

        for (RunReport cached : reportDbService.getRecentReports(DB_RECENT_REPORTS_LIMIT)) {
            if (cached == null || cached.getRunId() == null || cached.getRunId().isBlank()) {
                continue;
            }
            if (!Files.exists(reportsDir.resolve(cached.getRunId()).resolve(REPORT_JSON))) {
                continue; // stale DB row: the report folder is gone
            }
            byId.put(cached.getRunId(), cached);
        }

        if (Files.exists(reportsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsDir)) {
                for (Path dir : stream) {
                    if (!Files.isDirectory(dir)) {
                        continue;
                    }
                    Path reportFile = dir.resolve(REPORT_JSON);
                    if (!Files.exists(reportFile)) {
                        continue;
                    }
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (byId.containsKey(dirName)) {
                        continue; // DB already has the full record
                    }
                    try {
                        RunReport report = mapper.readValue(reportFile.toFile(), RunReport.class);
                        if (report.getRunId() == null || report.getRunId().isBlank()) {
                            report.setRunId(dirName);
                        }
                        report.setCases(new ArrayList<>());
                        byId.put(report.getRunId(), report);
                        indexService.indexReport(report); // repair the DB mirror
                    } catch (Exception corrupt) {
                        AiLoggingService.warn("REPORTS", "Bỏ qua report hỏng " + reportFile + ": "
                                + corrupt.getMessage());
                    }
                }
            } catch (IOException scanFailure) {
                AiLoggingService.error("REPORTS",
                        "Không quét được thư mục reports: " + scanFailure.getMessage(), scanFailure);
            }
        }

        List<RunReport> reports = new ArrayList<>(byId.values());
        reports.sort(Comparator.comparing(RunReport::getStartedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return reports;
    }

    public void deleteReport(String runId) throws IOException {
        String safeRunId = requireRunId(runId);
        Path reportDir = StudioPaths.getReportsDir().resolve(safeRunId);
        deleteDirectoryRecursive(reportDir);
        Path harnessDir = StudioPaths.getHarnessDir().resolve(safeRunId);
        deleteDirectoryRecursive(harnessDir);

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM run_reports WHERE run_id = ?")) {
            ps.setString(1, safeRunId);
            ps.executeUpdate();
        } catch (Exception dbFailure) {
            // The folder is already gone, so listReports no longer shows it; only the index row is
            // stale. Report it instead of hiding the failure.
            AiLoggingService.warn("REPORTS", "Đã xoá report " + safeRunId
                    + " nhưng không xoá được bản ghi DB: " + dbFailure.getMessage());
        }
    }

    /** Rejects a run id that could escape the reports directory. */
    private static String requireRunId(String runId) throws IOException {
        if (runId == null || runId.isBlank()) {
            throw new IOException("Missing runId");
        }
        String trimmed = runId.trim();
        if (!trimmed.equals(Path.of(trimmed).getFileName().toString())
                || ".".equals(trimmed) || "..".equals(trimmed)) {
            throw new IOException("runId không hợp lệ: " + runId);
        }
        return trimmed;
    }

    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
        if (Files.exists(dir)) {
            throw new IOException("Không xóa hết được: " + dir);
        }
    }

    public Path getScreenshotDir(String runId) {
        return StudioPaths.getReportsDir().resolve(runId).resolve("screenshots");
    }
}
