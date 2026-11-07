package com.studio.db;

import com.studio.model.RunReport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * High-speed query and analytics service for execution reports powered by H2 index.
 */
public class ReportDbService {

    private final DatabaseManager dbManager;

    public ReportDbService() {
        this(DatabaseManager.getInstance());
    }

    public ReportDbService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public static class AnalyticsSummary {
        private final int totalRuns;
        private final int totalCasesExecuted;
        private final int totalPassed;
        private final int totalFailed;
        private final int totalSkipped;
        private final double passRatePercent;
        private final long averageDurationMs;

        public AnalyticsSummary(int totalRuns, int totalCasesExecuted, int totalPassed,
                                int totalFailed, int totalSkipped, double passRatePercent, long averageDurationMs) {
            this.totalRuns = totalRuns;
            this.totalCasesExecuted = totalCasesExecuted;
            this.totalPassed = totalPassed;
            this.totalFailed = totalFailed;
            this.totalSkipped = totalSkipped;
            this.passRatePercent = passRatePercent;
            this.averageDurationMs = averageDurationMs;
        }

        public int getTotalRuns() { return totalRuns; }
        public int getTotalCasesExecuted() { return totalCasesExecuted; }
        public int getTotalPassed() { return totalPassed; }
        public int getTotalFailed() { return totalFailed; }
        public int getTotalSkipped() { return totalSkipped; }
        public double getPassRatePercent() { return passRatePercent; }
        public long getAverageDurationMs() { return averageDurationMs; }
    }

    /**
     * Retrieve recent run reports ordered by start time descending.
     */
    public List<RunReport> getRecentReports(int limit) {
        List<RunReport> list = new ArrayList<>();
        String sql = "SELECT * FROM run_reports ORDER BY start_time DESC LIMIT ?";

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 50);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapReport(rs));
                }
            }
        } catch (SQLException ignored) {}

        return list;
    }

    /**
     * Look up a single report by runId.
     */
    public RunReport getReport(String runId) {
        if (runId == null) return null;
        String sql = "SELECT * FROM run_reports WHERE run_id = ?";

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapReport(rs);
                }
            }
        } catch (SQLException ignored) {}

        return null;
    }

    /**
     * Aggregate executive analytics metrics in a single SQL query.
     */
    public AnalyticsSummary getAnalyticsSummary() {
        String sql = """
            SELECT COUNT(*),
                   COALESCE(SUM(total_cases), 0),
                   COALESCE(SUM(passed_cases), 0),
                   COALESCE(SUM(failed_cases), 0),
                   COALESCE(SUM(skipped_cases), 0),
                   COALESCE(AVG(duration_ms), 0)
            FROM run_reports
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int runs = rs.getInt(1);
                int cases = rs.getInt(2);
                int passed = rs.getInt(3);
                int failed = rs.getInt(4);
                int skipped = rs.getInt(5);
                long avgDuration = (long) rs.getDouble(6);

                double passRate = cases > 0 ? (passed * 100.0) / cases : 0.0;
                return new AnalyticsSummary(runs, cases, passed, failed, skipped, Math.round(passRate * 10.0) / 10.0, avgDuration);
            }
        } catch (SQLException ignored) {}

        return new AnalyticsSummary(0, 0, 0, 0, 0, 0.0, 0);
    }

    private RunReport mapReport(ResultSet rs) throws SQLException {
        RunReport r = new RunReport();
        r.setRunId(rs.getString("run_id"));
        r.setSuiteName(rs.getString("suite_name"));
        r.setRunTarget(rs.getString("run_target"));
        r.setStartTime(rs.getLong("start_time"));
        r.setDurationMs(rs.getLong("duration_ms"));
        r.setTotalCases(rs.getInt("total_cases"));
        r.setPassedCases(rs.getInt("passed_cases"));
        r.setFailedCases(rs.getInt("failed_cases"));
        r.setSkippedCases(rs.getInt("skipped_cases"));
        r.setReportPath(rs.getString("report_path"));
        r.setStatus(rs.getString("status"));
        return r;
    }
}
