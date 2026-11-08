# Implementation Plan: System Review Issue Resolution & Performance Optimization

## Overview
Address findings from the five-axis review of Test Studio 2.3.1 across Correctness, Architecture, Performance, and Code Cleanliness.

---

## Slices

### Slice 1: Correctness - Fix Duplicated AI Error Handling
- Remove dead duplicate lines 1056–1061 in `AiAssistantPageController.java`.
- Guard against null `currentSession` if active session was deleted during generation.
- Ensure only one error message bubble is rendered and single toast is shown.

### Slice 2: Reliability - Atomic JSON File Writes in `AiChatHistoryStore`
- Implement atomic write pattern (`writeJsonAtomic`) in `AiChatHistoryStore.java` for `saveSession` and `migrateLegacyIfPresent`.
- Uses temporary file in the destination directory and `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` to prevent corrupted JSON files on sudden termination.

### Slice 3: Performance - Batch Indexing for AI Chat Messages in H2
- Add `indexChatMessages(List<AiChatMessage> messages, String suiteKey)` in `LocalDataIndexService.java` using a single JDBC `Connection` and `PreparedStatement.addBatch() / executeBatch()`.
- Update `AiChatHistoryStore.saveSession()` to invoke this batch indexing method rather than looping individual single-connection inserts.

### Slice 4: Cleanliness - Remove Redundant Session Refreshes & Unify Deletion SQL
- In `AiAssistantPageController.handleDeleteSession`, remove redundant `refreshSessionsList(null)` call right before `loadChatHistoryForSuite()`.
- In `LocalDataIndexService.clearChatForSession`, delegate directly to `AiChatDbService.deleteSession`.

### Slice 5: Verification & Architectural Learnings
- Run full test suite (`mvn clean test`).
- Record learnings in `tasks/learnings.md`.
