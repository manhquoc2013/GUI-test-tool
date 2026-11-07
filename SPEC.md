# Technical Specification: Local Embedded Database (H2) Acceleration & Read-Through Index

## 1. Objective
Integrate an embedded local database (H2 2.2.224) as a high-performance Dual-Layer Read-Through Cache and Index for Playwright Automation Studio.
Key benefits:
1. **Sub-millisecond data retrieval**: Replace recursive directory scans and repetitive JSON disk parsing with indexed SQL queries.
2. **Instant Search & Filter across Explorer**: Full-text keyword search and instant status filtering across test cases and script contents.
3. **High-speed Reports Analytics**: Instant aggregation (pass/fail rate, total runs, average execution duration) without reading hundreds of `summary.json` files.
4. **Fast AI Chat History**: Instant query of chat history and token usage by suite key.
5. **Dual-Layer Consistency**: Git-friendly JSON and `.java` files remain intact on disk. The local database auto-indexes on startup and synchronizes write-through on changes.

## 2. Architecture & Components

### 2.1 Database Engine (`com.studio.db.DatabaseManager`)
- Embedded H2 JDBC driver (`jdbc:h2:file:./data/studio_cache;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;MODE=REGULAR`).
- Support for in-memory mode (`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`) for lightning-fast, isolated unit testing.
- Automatic DDL schema migration on startup.
- Tables:
  - `test_groups` (id, name, description, parent_id, path, updated_at)
  - `test_suites` (id, group_name, name, description, browser, headless, path, updated_at)
  - `test_cases` (id, suite_id, group_name, suite_name, name, description, status, start_url, script_file, script_content, assertions_count, updated_at)
  - `test_assertions` (id, case_id, type, locator, value, status, order_index)
  - `run_reports` (run_id, suite_name, run_target, start_time, duration_ms, total_cases, passed_cases, failed_cases, skipped_cases, report_path, status)
  - `ai_chat_messages` (id, suite_key, role, content, estimated_tokens, attached_files, has_snapshot, applied_to_suite, created_at)
- Indexes on:
  - `(group_name, suite_name)`
  - `(status)`
  - `(name, description)`
  - `(start_time DESC)`
  - `(suite_key, created_at)`

### 2.2 Synchronization & Indexing Engine (`com.studio.db.LocalDataIndexService`)
- `syncAllFromDisk()`: Scans project directories and reports directory, populates or updates H2 index in batches.
- `indexGroup(TestGroup group, Path path)`
- `indexSuite(TestSuite suite, Path path, String groupName)`
- `indexCase(TestCase tc, String groupName, String suiteName, String scriptContent)`
- `indexReport(RunReport report)`
- `indexChatMessage(AiChatMessage msg, String suiteKey)`
- `deleteCase(String caseId)`
- `deleteSuite(String suiteName, String groupName)`

### 2.3 Domain Services
- `ProjectDbService`: Fast queries for Explorer, case counts, script search, assertion lookups.
- `ReportDbService`: Fast reports list, pagination/sorting, overall analytics metrics (pass rate, runs count).
- `AiChatDbService`: Fast chat session retrieval and persistent message indexing.

### 2.4 Integration Points
- `ProjectStore`: Synchronizes changes to `LocalDataIndexService` whenever groups, suites, or cases are created/updated/deleted.
- `ReportStore`: Synchronizes newly saved reports to `LocalDataIndexService`.
- `AiChatHistoryStore`: Synchronizes AI chat messages into `LocalDataIndexService`.
- `ExplorerPageController`: Uses `ProjectDbService` for instant filter / search.
- `ReportsPageController`: Uses `ReportDbService` for instant statistics cards.

## 3. Testing Strategy
- Unit tests for `DatabaseManager`: schema creation, connection lifecycle, in-memory isolation.
- Unit tests for `LocalDataIndexService`: disk-to-DB sync, entity indexing, deletions, search.
- Unit tests for `ReportDbService`: summary statistics calculation, sorting, and retrieval.
- Unit tests for `ProjectDbService`: fast case filtering and assertion counting.
- Full regression test run with `mvn test -o`.
