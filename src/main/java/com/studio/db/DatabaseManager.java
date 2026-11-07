package com.studio.db;

import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the embedded H2 database connection and schema lifecycle.
 * Provides high-speed in-process caching, indexing, and analytics.
 */
public class DatabaseManager {

    private static DatabaseManager instance;

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public DatabaseManager() {
        this(resolveDefaultUrl(), "sa", "");
    }

    public DatabaseManager(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user != null ? user : "sa";
        this.password = password != null ? password : "";
        initializeSchema();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public static synchronized void setInstance(DatabaseManager customInstance) {
        instance = customInstance;
    }

    public static String resolveDefaultUrl() {
        Path dataDir = StudioPaths.getDataDir();
        try {
            Files.createDirectories(dataDir);
        } catch (IOException ignored) {}
        String dbPath = dataDir.resolve("studio_cache").toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + dbPath + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;MODE=REGULAR";
    }

    public static DatabaseManager createInMemory(String dbName) {
        return new DatabaseManager("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=REGULAR", "sa", "");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Initialize DDL tables and indexes if they do not exist.
     */
    public void initializeSchema() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS test_groups (
                name VARCHAR(255) PRIMARY KEY,
                description TEXT,
                path TEXT,
                updated_at BIGINT
            );

            CREATE TABLE IF NOT EXISTS test_suites (
                id VARCHAR(255) PRIMARY KEY,
                group_name VARCHAR(255) NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                browser VARCHAR(50),
                headless BOOLEAN,
                path TEXT,
                updated_at BIGINT
            );

            CREATE TABLE IF NOT EXISTS test_cases (
                id VARCHAR(255) PRIMARY KEY,
                suite_id VARCHAR(255),
                group_name VARCHAR(255) NOT NULL,
                suite_name VARCHAR(255) NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                status VARCHAR(50) DEFAULT 'Draft',
                start_url TEXT,
                script_file VARCHAR(255),
                script_content CLOB,
                assertions_count INT DEFAULT 0,
                updated_at BIGINT
            );

            CREATE TABLE IF NOT EXISTS test_assertions (
                id VARCHAR(255) PRIMARY KEY,
                case_id VARCHAR(255) NOT NULL,
                assert_type VARCHAR(100) NOT NULL,
                locator TEXT,
                expected_val TEXT,
                status VARCHAR(50) DEFAULT 'ACTIVE',
                order_index INT DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS run_reports (
                run_id VARCHAR(255) PRIMARY KEY,
                scope VARCHAR(50),
                scope_name VARCHAR(255),
                scope_path TEXT,
                browser VARCHAR(50),
                started_at VARCHAR(100),
                finished_at VARCHAR(100),
                total_cases INT DEFAULT 0,
                passed_cases INT DEFAULT 0,
                failed_cases INT DEFAULT 0,
                skipped_cases INT DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS ai_chat_messages (
                id VARCHAR(255) PRIMARY KEY,
                suite_key VARCHAR(255) NOT NULL,
                role VARCHAR(50) NOT NULL,
                content CLOB,
                estimated_tokens INT DEFAULT 0,
                attached_files TEXT,
                suite_snapshot_json CLOB,
                applied_to_suite BOOLEAN DEFAULT FALSE,
                created_at BIGINT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS system_settings (
                setting_key VARCHAR(100) PRIMARY KEY,
                setting_value CLOB NOT NULL,
                updated_at BIGINT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_tc_suite ON test_cases(group_name, suite_name);
            CREATE INDEX IF NOT EXISTS idx_tc_status ON test_cases(status);
            CREATE INDEX IF NOT EXISTS idx_tc_name ON test_cases(name);
            CREATE INDEX IF NOT EXISTS idx_reports_start ON run_reports(started_at DESC);
            CREATE INDEX IF NOT EXISTS idx_chat_suite ON ai_chat_messages(suite_key, created_at);
            CREATE INDEX IF NOT EXISTS idx_assertions_case ON test_assertions(case_id);
            """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("Không thể khởi tạo cơ sở dữ liệu nhúng H2: " + e.getMessage(), e);
        }
    }
}
