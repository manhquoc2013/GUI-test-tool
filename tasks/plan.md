# Implementation Plan: Local Embedded Database (H2) Acceleration

## Phase 1: Database Infrastructure & Schema Setup
- Add `com.h2database:h2:2.2.224` to `pom.xml`.
- Create `DatabaseManager.java` managing connection lifecycle, schema initialization, and test memory mode.
- Unit tests for `DatabaseManagerTest`.

## Phase 2: Indexing Engine & Synchronization
- Create `LocalDataIndexService.java` with batch synchronization from disk (`syncAllFromDisk`), entity upserts, and deletions.
- Create domain services: `ProjectDbService.java`, `ReportDbService.java`, `AiChatDbService.java`.
- Unit tests for `LocalDataIndexServiceTest`, `ReportDbServiceTest`, and `ProjectDbServiceTest`.

## Phase 3: Integration with Stores & Controllers
- Hook `LocalDataIndexService` into `ProjectStore.java` on save/create/delete operations.
- Hook `LocalDataIndexService` into `ReportStore.java` on save report operations.
- Hook `LocalDataIndexService` into `AiChatHistoryStore.java` on save/delete session operations.
- Initialize `LocalDataIndexService.syncAllFromDisk()` on startup or lazy-first-load.

## Phase 4: Full Verification & Performance Audit
- Execute all unit tests (`mvn test -o`).
- Verify query speed and memory safety.
- Record institutional learnings in `tasks/learnings.md`.
