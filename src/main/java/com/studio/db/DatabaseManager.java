package com.studio.db;

import com.studio.util.StudioPaths;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the embedded H2 database connection and schema lifecycle.
 * Provides high-speed in-process caching, indexing, and analytics.
 */
public class DatabaseManager {

    private static DatabaseManager instance;

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final BlockingQueue<Connection> pool = new LinkedBlockingQueue<>(10);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

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
        if (isShutdown.get()) {
            throw new SQLException("DatabaseManager has been shut down");
        }
        Connection physical = pool.poll();
        try {
            if (physical != null && (physical.isClosed() || !physical.isValid(1))) {
                try { physical.close(); } catch (Exception ignored) {}
                physical = null;
            }
        } catch (Exception e) {
            physical = null;
        }

        if (physical == null) {
            physical = DriverManager.getConnection(jdbcUrl, user, password);
        }

        final Connection target = physical;
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        if (!isShutdown.get() && !target.isClosed()) {
                            try {
                                if (!target.getAutoCommit()) {
                                    target.rollback();
                                    target.setAutoCommit(true);
                                }
                            } catch (SQLException ignored) {}
                            if (pool.offer(target)) {
                                return null;
                            }
                        }
                        target.close();
                        return null;
                    }
                    if ("isClosed".equals(method.getName())) {
                        return target.isClosed();
                    }
                    if ("unwrap".equals(method.getName()) && args != null && args.length == 1 && ((Class<?>) args[0]).isInstance(target)) {
                        return target;
                    }
                    if ("isWrapperFor".equals(method.getName()) && args != null && args.length == 1 && ((Class<?>) args[0]).isInstance(target)) {
                        return true;
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ite) {
                        Throwable cause = ite.getCause();
                        throw cause != null ? cause : ite;
                    }
                }
        );
    }

    public int getIdlePoolSize() {
        return pool.size();
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
                priority VARCHAR(10) DEFAULT 'P1',
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

            CREATE TABLE IF NOT EXISTS ai_chat_sessions (
                id VARCHAR(255) PRIMARY KEY,
                suite_key VARCHAR(255) NOT NULL,
                title VARCHAR(255) NOT NULL,
                pinned BOOLEAN DEFAULT FALSE,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS ai_chat_messages (
                id VARCHAR(255) PRIMARY KEY,
                suite_key VARCHAR(255) NOT NULL,
                session_id VARCHAR(255),
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
            CREATE INDEX IF NOT EXISTS idx_chat_sessions ON ai_chat_sessions(suite_key, updated_at DESC);
            CREATE INDEX IF NOT EXISTS idx_assertions_case ON test_assertions(case_id);
            """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            try {
                stmt.execute("ALTER TABLE test_cases ADD COLUMN IF NOT EXISTS priority VARCHAR(10) DEFAULT 'P1'");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tc_priority ON test_cases(priority)");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE ai_chat_messages ADD COLUMN IF NOT EXISTS session_id VARCHAR(255)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_session_id ON ai_chat_messages(session_id, created_at)");
            } catch (Exception ignored) {}
        } catch (SQLException e) {
            throw new RuntimeException("Không thể khởi tạo cơ sở dữ liệu nhúng H2: " + e.getMessage(), e);
        }
    }

    /**
     * Compacts H2 memory buffers and flushes unwritten blocks to disk to release RAM.
     */
    public synchronized void compactAndReleaseMemory() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CHECKPOINT DEFRAG");
        } catch (Exception ignored) {}
    }

    /**
     * Performs a graceful database shutdown, defragmenting and releasing all file locks and OS buffers.
     */
    public synchronized void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            return;
        }
        Connection pooledConn;
        while ((pooledConn = pool.poll()) != null) {
            try { pooledConn.close(); } catch (Exception ignored) {}
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN COMPACT");
        } catch (Exception ignored) {}
    }
}
