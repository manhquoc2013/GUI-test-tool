package com.studio.db;

import com.studio.model.Assertion;
import com.studio.model.TestCase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-speed query service for Project, Suite, and TestCase domain models powered by H2 index.
 */
public class ProjectDbService {

    private final DatabaseManager dbManager;

    public ProjectDbService() {
        this(DatabaseManager.getInstance());
    }

    public ProjectDbService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Search test cases with instant full-text filtering on name, description, and script content.
     */
    public List<TestCase> searchCases(String keyword, String statusFilter) {
        List<TestCase> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM test_cases WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (statusFilter != null && !statusFilter.isBlank() && !"Tất cả".equalsIgnoreCase(statusFilter)) {
            sql.append("AND status = ? ");
            params.add(statusFilter);
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ? OR LOWER(script_content) LIKE ?) ");
            String term = "%" + keyword.toLowerCase().trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY name ASC");

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TestCase tc = mapTestCase(rs);
                    tc.setAssertions(getAssertionsForCase(conn, tc.getId()));
                    results.add(tc);
                }
            }
        } catch (SQLException ignored) {}

        return results;
    }

    /**
     * Get all test cases for a specific suite.
     */
    public List<TestCase> getCasesForSuite(String groupName, String suiteName) {
        List<TestCase> results = new ArrayList<>();
        String sql = "SELECT * FROM test_cases WHERE group_name = ? AND suite_name = ? ORDER BY name ASC";

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName != null ? groupName : "");
            ps.setString(2, suiteName != null ? suiteName : "");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TestCase tc = mapTestCase(rs);
                    tc.setAssertions(getAssertionsForCase(conn, tc.getId()));
                    results.add(tc);
                }
            }
        } catch (SQLException ignored) {}

        return results;
    }

    /**
     * Total number of test cases indexed.
     */
    public int getTotalCasesCount() {
        String sql = "SELECT COUNT(*) FROM test_cases";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    /**
     * Total number of assertions indexed.
     */
    public int getTotalAssertionsCount() {
        String sql = "SELECT COUNT(*) FROM test_assertions";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return 0;
    }

    /**
     * Aggregation of cases count grouped by status (e.g. Ready, Draft, Deprecated).
     */
    public Map<String, Integer> getCasesCountByStatus() {
        Map<String, Integer> counts = new HashMap<>();
        String sql = "SELECT status, COUNT(*) FROM test_cases GROUP BY status";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException ignored) {}
        return counts;
    }

    /**
     * Get assertions for a specific test case.
     */
    public List<Assertion> getAssertionsForCase(String caseId) {
        try (Connection conn = dbManager.getConnection()) {
            return getAssertionsForCase(conn, caseId);
        } catch (SQLException ignored) {
            return new ArrayList<>();
        }
    }

    private List<Assertion> getAssertionsForCase(Connection conn, String caseId) {
        List<Assertion> list = new ArrayList<>();
        String sql = "SELECT * FROM test_assertions WHERE case_id = ? ORDER BY order_index ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Assertion a = new Assertion();
                    a.setType(rs.getString("assert_type"));
                    a.setLocator(rs.getString("locator"));
                    a.setValue(rs.getString("expected_val"));
                    a.setStatus(rs.getString("status"));
                    list.add(a);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private TestCase mapTestCase(ResultSet rs) throws SQLException {
        TestCase tc = new TestCase();
        tc.setId(rs.getString("id"));
        tc.setName(rs.getString("name"));
        tc.setDescription(rs.getString("description"));
        tc.setStatus(rs.getString("status"));
        tc.setStartUrl(rs.getString("start_url"));
        tc.setScriptFile(rs.getString("script_file"));
        return tc;
    }
}

