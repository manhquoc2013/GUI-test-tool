package com.studio.store;

import com.studio.db.DatabaseManager;
import com.studio.db.LocalDataIndexService;
import com.studio.db.ReportDbService;
import com.studio.model.RunReport;
import com.studio.util.StudioPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ReportStore {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String REPORT_JSON = "report.json";

    private final LocalDataIndexService indexService = new LocalDataIndexService();
    private final ReportDbService reportDbService = new ReportDbService();

    public ReportStore() {
    }

    public void saveReport(RunReport report) throws IOException {
        Path reportDir = StudioPaths.getReportsDir().resolve(report.getRunId());
        Files.createDirectories(reportDir);
        mapper.writeValue(reportDir.resolve(REPORT_JSON).toFile(), report);
        indexService.indexReport(report);
    }

    public RunReport loadReport(String runId) throws IOException {
        Path reportFile = StudioPaths.getReportsDir().resolve(runId).resolve(REPORT_JSON);
        if (!Files.exists(reportFile)) {
            throw new IOException("Report not found: " + runId);
        }
        return mapper.readValue(reportFile.toFile(), RunReport.class);
    }

    public List<RunReport> listReports() throws IOException {
        List<RunReport> cached = reportDbService.getRecentReports(100);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<RunReport> reports = new ArrayList<>();
        Path reportsDir = StudioPaths.getReportsDir();

        if (Files.exists(reportsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsDir)) {
                for (Path dir : stream) {
                    if (Files.isDirectory(dir) && Files.exists(dir.resolve(REPORT_JSON))) {
                        RunReport report = mapper.readValue(dir.resolve(REPORT_JSON).toFile(), RunReport.class);
                        report.setCases(new ArrayList<>());
                        reports.add(report);
                        indexService.indexReport(report);
                    }
                }
            }
        }
        return reports;
    }

    public void deleteReport(String runId) throws IOException {
        if (runId == null || runId.isBlank()) {
            throw new IOException("Missing runId");
        }
        Path reportDir = StudioPaths.getReportsDir().resolve(runId);
        deleteDirectoryRecursive(reportDir);
        Path harnessDir = StudioPaths.getHarnessDir().resolve(runId);
        deleteDirectoryRecursive(harnessDir);

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM run_reports WHERE run_id = ?")) {
            ps.setString(1, runId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
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
