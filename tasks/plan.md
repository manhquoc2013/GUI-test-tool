# Implementation Plan: System Data Cleanup & Settings Protection

## Phase 1: Settings Dual-Persistence & Backup
- Add `system_settings` table to `DatabaseManager.java`.
- Enhance `SettingsStore.java` with H2 mirror persistence, fallback loading, and Export/Import methods.
- Write unit tests in `SettingsStoreTest.java`.

## Phase 2: Data Cleanup Engine
- Create `DataCleanupService.java` handling multi-target deletions, report retention limits, work directory purging, and byte counting.
- Write unit tests in `DataCleanupServiceTest.java`.

## Phase 3: Cleanup Modal Dialog & Shell Integration
- Create `DataCleanupDialog.java` with safety banner, backup/restore buttons, target checkboxes, retention picker, and confirmation workflow.
- Update `main_shell.fxml` with "Dọn dẹp" button in header bar.
- Update `MainShellController.java` to launch `DataCleanupDialog`.

## Phase 4: Full Verification & Learnings
- Run all tests with `mvn test -o`.
- Document learnings in `tasks/learnings.md`.
