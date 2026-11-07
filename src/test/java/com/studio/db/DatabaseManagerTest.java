package com.studio.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {

    @Test
    public void testInMemoryDatabaseInitialization() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory("db_init_test");
        assertNotNull(db);
        assertTrue(db.getJdbcUrl().contains("mem:db_init_test"));

        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            // Verify tables exist in H2 schema
            ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='PUBLIC'");
            StringBuilder tables = new StringBuilder();
            while (rs.next()) {
                tables.append(rs.getString(1).toLowerCase()).append(",");
            }

            String tableList = tables.toString();
            assertTrue(tableList.contains("test_groups"));
            assertTrue(tableList.contains("test_suites"));
            assertTrue(tableList.contains("test_cases"));
            assertTrue(tableList.contains("test_assertions"));
            assertTrue(tableList.contains("run_reports"));
            assertTrue(tableList.contains("ai_chat_messages"));
        }
    }

    @Test
    public void testSimpleInsertAndSelect() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory("db_crud_test");

        try (Connection conn = db.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO test_groups (id, name, description, updated_at) VALUES ('g1', 'E2E', 'End to End tests', 1000)");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name, description FROM test_groups WHERE id='g1'")) {
                assertTrue(rs.next());
                assertEquals("E2E", rs.getString("name"));
                assertEquals("End to End tests", rs.getString("description"));
            }
        }
    }
}
