# Implementation Plan: AI Assistant Copy, Revert/Undo, Chat History & Token Optimization

## Phase 1: Models & Core Storage (Snapshot & Rollback Engine)
- Create `AiChatMessage.java` and `AiChatSession.java` representing messages, token counts, and snapshot states.
- Create `AiChatHistoryStore.java` with persistent JSON serialization (`.ai_chat.json`), suite filesystem snapshot capture, and atomic rollback capability.
- Unit tests for `AiChatHistoryStore` (snapshot creation, file restoration, deletion of newly created test files).

## Phase 2: Token Optimizer Service
- Create `AiTokenOptimizer.java` with token estimation heuristic, attachment compression, sliding window context pruner, and automatic summary compaction.
- Unit tests for `AiTokenOptimizerTest` covering token calculations, sliding window logic, and compression rules.
- Integrate optimizer with `AiTestGeneratorService.java` to format optimized prompts sent to `OpenAiClient`.

## Phase 3: UI Icons & Visual Enhancements
- Add `UNDO` icon to `UiIcons.java`.
- Update `studio.css` and `studio-light.css` with chat action button styles (hover state, copy & undo pill buttons, token badge).

## Phase 4: AI Assistant Page Integration
- Update `AiAssistantPageController.java` to:
  - Load/save chat history automatically per selected Suite.
  - Render copy & undo buttons on each chat message.
  - Handle copy to system clipboard with notification.
  - Handle undo: remove chat message, revert preview list, and rollback Suite files if applied.
  - Live token estimation indicator (`tokenUsageBadge`) updated on prompt input and message load.
- Update `ai_assistant_page.fxml` with token badge and polished UI components.

## Phase 5: Verification & End-to-End Tests
- Compile with `mvn test-compile -o`.
- Execute full test suite (`mvn test -o`).
- Perform multi-specialist verification (correctness, safety, performance).
