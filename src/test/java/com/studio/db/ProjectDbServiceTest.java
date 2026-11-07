package com.studio.db;

import com.studio.model.Assertion;
import com.studio.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectDbServiceTest {

    private DatabaseManager dbManager;
    private ProjectDbService dbService;

    @BeforeEach
    public void setup() throws SQLException {
        dbManager = DatabaseManager.createInMemory("project_db_test_" + System.currentTimeMillis());
        dbService = new ProjectDbService(dbManager);

        try (Connection conn = dbManager.getConnection()) {
            // Insert test cases
            String tcSql = "INSERT INTO test_cases (id, suite_id, group_name, suite_name, name, description, status, start_url, script_file, script_content, assertions_count, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(tcSql)) {
                // Case 1
                ps.setString(1, "tc-1");
                ps.setString(2, "suite-login");
                ps.setString(3, "AuthGroup");
                ps.setString(4, "LoginSuite");
                ps.setString(5, "Valid Login");
                ps.setString(6, "Test login with valid email and password");
                ps.setString(7, "Ready");
                ps.setString(8, "https://example.com/login");
                ps.setString(9, "ValidLogin.java");
                ps.setString(10, "page.fill('#email', 'user@test.com');");
                ps.setInt(11, 2);
                ps.setLong(12, 1000L);
                ps.executeUpdate();

                // Case 2
                ps.setString(1, "tc-2");
                ps.setString(2, "suite-login");
                ps.setString(3, "AuthGroup");
                ps.setString(4, "LoginSuite");
                ps.setString(5, "Invalid Password");
                ps.setString(6, "Test login with wrong password");
                ps.setString(7, "Draft");
                ps.setString(8, "https://example.com/login");
                ps.setString(9, "InvalidPassword.java");
                ps.setString(10, "page.fill('#password', 'wrong');");
                ps.setInt(11, 1);
                ps.setLong(12, 2000L);
                ps.executeUpdate();
            }

            // Insert assertions
            String assertSql = "INSERT INTO test_assertions (id, case_id, assert_type, locator, expected_val, status, order_index) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(assertSql)) {
                // Assertions for Case 1
                ps.setString(1, "as-1");
                ps.setString(2, "tc-1");
                ps.setString(3, "ELEMENT_VISIBLE");
                ps.setString(4, "#dashboard");
                ps.setString(5, "");
                ps.setString(6, "ACTIVE");
                ps.setInt(7, 1);
                ps.executeUpdate();

                ps.setString(1, "as-2");
                ps.setString(2, "tc-1");
                ps.setString(3, "PAGE_HAS_TEXT");
                ps.setString(4, "");
                ps.setString(5, "Chào mừng");
                ps.setString(6, "ACTIVE");
                ps.setInt(7, 2);
                ps.executeUpdate();

                // Assertion for Case 2
                ps.setString(1, "as-3");
                ps.setString(2, "tc-2");
                ps.setString(3, "PAGE_HAS_TEXT");
                ps.setString(4, "");
                ps.setString(5, "Sai mật khẩu");
                ps.setString(6, "ACTIVE");
                ps.setInt(7, 1);
                ps.executeUpdate();
            }
        }
    }

    @Test
    public void testSearchCasesWithBatchAssertions() {
        List<TestCase> results = dbService.searchCases("Login", "Tất cả");
        assertEquals(2, results.size());

        TestCase tc1 = results.stream().filter(c -> c.getId().equals("tc-1")).findFirst().orElse(null);
        assertNotNull(tc1);
        assertEquals("Valid Login", tc1.getName());
        assertEquals(2, tc1.getAssertions().size());
        assertEquals("ELEMENT_VISIBLE", tc1.getAssertions().get(0).getType());
        assertEquals("PAGE_HAS_TEXT", tc1.getAssertions().get(1).getType());

        TestCase tc2 = results.stream().filter(c -> c.getId().equals("tc-2")).findFirst().orElse(null);
        assertNotNull(tc2);
        assertEquals(1, tc2.getAssertions().size());
        assertEquals("Sai mật khẩu", tc2.getAssertions().get(0).getValue());
    }

    @Test
    public void testGetCasesForSuiteWithBatchAssertions() {
        List<TestCase> results = dbService.getCasesForSuite("AuthGroup", "LoginSuite");
        assertEquals(2, results.size());

        for (TestCase tc : results) {
            assertNotNull(tc.getAssertions());
            assertFalse(tc.getAssertions().isEmpty());
        }
    }

    @Test
    public void testCountsAndStatusAggregation() {
        assertEquals(2, dbService.getTotalCasesCount());
        assertEquals(3, dbService.getTotalAssertionsCount());

        Map<String, Integer> counts = dbService.getCasesCountByStatus();
        assertEquals(1, counts.get("Ready"));
        assertEquals(1, counts.get("Draft"));
    }
}

