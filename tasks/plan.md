# Implementation Plan: AI Assistant Chat Session Management & Context Optimization

## Overview
Decompose the implementation of multi-session chat management, collapsible history sidebar, and large context token optimization into 5 vertical slices following RED -> GREEN -> Refactor and atomic commits.

---

## Slices

### Slice 1: Domain Models & Database Schema
- Update `AiChatSession` with `id`, `title`, `createdAt`, `pinned`.
- Create `AiChatSessionMeta` for lightweight session listings.
- Update `DatabaseManager` DDL to add table `ai_chat_sessions` and column `session_id` in `ai_chat_messages`.
- Update `AiChatDbService` to support listing sessions, upserting session metadata, and querying messages by `session_id`.

### Slice 2: Enhanced Token Optimizer (Large Context Engine)
- Update `AiTokenOptimizer`:
  - Implement historical bulky code and DOM truncation (`truncateHistoricalContent`).
  - Implement progressive rolling summary generation with categorized sections (`[Mục tiêu chính]`, `[Ca kiểm thử đã sinh]`, `[Locators/Quy tắc]`).
  - Refine `buildOptimizedMessages` with adaptive sliding window and hard token budget limit enforcement.
- Add unit tests in `AiTokenOptimizerTest` covering large conversational history, truncation, and rolling summary.

### Slice 3: Multi-Session Store & Migration Engine
- Update `AiChatHistoryStore`:
  - Directory-based storage `.ai_chats/` per suite/workspace.
  - Implement `listSessions`, `loadSession`, `createNewSession`, `saveSession`, `renameSession`, `deleteSession`.
  - Implement seamless migration from legacy `.ai_chat.json`.
- Add comprehensive tests in `AiChatHistoryStoreTest`.

### Slice 4: JavaFX UI - Collapsible Sidebar & Session Controls
- Update `ai_assistant_page.fxml`:
  - Add collapsible Left Sidebar `sessionsSidebar` with "+ Cuộc trò chuyện mới", search filter, and session list.
  - Add toggle sidebar button in the top toolbar (`☰ Lịch sử`).
  - Update styles in `ai-assistant.css` / theme.
- Update `AiAssistantPageController`:
  - Wire sidebar controls, toggle action, session switching, new chat action, search filter.
  - Auto-name session from first user message.
  - Context menu for session item: rename, pin, delete.
  - Connect active session ID to message saving and loading.

### Slice 5: End-to-End Verification & Documentation
- Run full Maven test suite (`mvn clean test`).
- Update documentation and `tasks/learnings.md`.
