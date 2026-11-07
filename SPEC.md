# Technical Specification: System Data Cleanup & Persistent Settings Protection

## 1. Objective
Provide a robust Data Maintenance & System Cleanup feature in Playwright Automation Studio:
1. **Multi-Target Cleanup**:
   - Clean Execution Reports & Screenshots (`data/reports/`, H2 `run_reports`), with option to preserve the latest N (e.g. 5 or 10) reports.
   - Clean AI Chat History (purges `.ai_chat.json` files across all suites and H2 `ai_chat_messages`).
   - Clean Temporary Work Directory & Test Harnesses (`work/`).
2. **Absolute System Settings Protection & Dual-Persistence**:
   - Protect `SettingsStore.Settings` (theme, browser defaults, timeouts, OpenAI API keys, endpoints, parameters) from ever being touched during cleanups.
   - **Dual-Persistence**: Automatically persist settings to both `data/config/settings.json` and H2 table `system_settings` to guard against file deletion or disk corruption.
   - **Backup & Restore**: Provide 1-click **Export Settings** to JSON file and **Import Settings** from JSON file.
3. **Ergonomic Modal UI (`DataCleanupDialog.java`)**:
   - Accessible via "Dọn dẹp" on the main application header.
   - Granular checkboxes, report retention picker (Delete all vs. Keep 5 vs. Keep 10), live disk usage estimation, and confirmation prompt.

## 2. Architecture & Components

### 2.1 Database Schema Addition (`DatabaseManager.java`)
- New table:
  ```sql
  CREATE TABLE IF NOT EXISTS system_settings (
      key VARCHAR(100) PRIMARY KEY,
      value CLOB NOT NULL,
      updated_at BIGINT NOT NULL
  );
  ```

### 2.2 Dual-Persistence & Backup in `SettingsStore.java`
- `save(Settings s)`: Writes to disk `data/config/settings.json` AND merges into H2 table `system_settings`.
- `load()`: Reads from `settings.json`; if missing or unreadable, falls back to H2 `system_settings`; if both missing, seeds defaults and saves to both.
- `exportSettings(Path targetFile)`: Exports current settings to chosen file.
- `importSettings(Path sourceFile)`: Validates and imports settings from file, saving to both disk and H2.

### 2.3 Cleanup Service (`com.studio.service.DataCleanupService`)
- Options class: `CleanupOptions`:
  - `cleanReports`: boolean
  - `keepRecentReports`: int (0 for all, 5, 10, etc.)
  - `cleanAiChat`: boolean
  - `cleanWorkDir`: boolean
  - `preserveSettings`: boolean (always true)
- Result class: `CleanupResult`:
  - `deletedReportsCount`: int
  - `deletedChatCount`: int
  - `deletedWorkFilesCount`: int
  - `freedBytes`: long
  - `settingsPreserved`: boolean
- Methods:
  - `calculateDiskUsage()`: Measures size and counts before cleanup.
  - `executeCleanup(CleanupOptions options)`: Executes cleanup, purges database rows, recalculates stats.

### 2.4 Cleanup Modal Dialog (`com.studio.controller.DataCleanupDialog`)
- Shows system settings safety guarantee with Export/Import buttons.
- Checkboxes for Reports, AI Chat, Work dir.
- ComboBox for report retention: "Xóa tất cả báo cáo", "Giữ lại 5 báo cáo mới nhất", "Giữ lại 10 báo cáo mới nhất".
- "Dọn dẹp ngay" action button with confirmation and summary toast.

## 3. Testing Strategy
- Unit tests for `SettingsStore` dual-persistence (fallback from H2 if file deleted, export/import).
- Unit tests for `DataCleanupService` (retention of N recent reports, work dir cleanup, chat deletion, settings immutability).
- Full regression verification (`mvn test -o`).
