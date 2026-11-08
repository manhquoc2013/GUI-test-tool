# Specification: AI Assistant Chat Session Management & Large Context Optimization

## 1. Objective & Scope
Enhance the AI Assistant in **Test Studio** with complete session lifecycle management and large context optimization:
1. **Multi-Session Chat Management**:
   - Create new chat conversations ("+ Cuộc trò chuyện mới" / New Chat).
   - Switch between historical conversations smoothly.
   - Search, rename, pin, and delete sessions.
   - Auto-generate conversation title from the first user prompt.
2. **Collapsible History Sidebar**:
   - Integrated left sidebar inside the AI Assistant view with collapsible toggle (`☰ Lịch sử`).
   - Clean list showing session title, relative timestamp, message count, and actions.
   - Seamless switching between Target Suites and sessions.
3. **Advanced Large-Context Token Optimization**:
   - **Hybrid Rolling Summary**: Progressive conversation memory consolidating user goals, generated test cases, and technical decisions from pruned turns.
   - **Adaptive Sliding Window**: Full context retention for the most recent $N$ conversational turns.
   - **Bulky Code & DOM Truncation**: Automatic compression of bulky code blocks and DOM snapshots in historical turns to conserve up to 75% tokens.
   - **Budget Limit Enforcement**: Dynamic pruning ensuring context never breaches token limits.
4. **Storage & Backward Compatibility**:
   - Per-suite storage inside `.ai_chats/<sessionId>.json`.
   - Local H2 Database indexing in `ai_chat_sessions` and `ai_chat_messages` (with `session_id`).
   - Seamless zero-data-loss auto-migration from legacy `.ai_chat.json`.

---

## 2. Architecture & Data Flow

```
[UI: Collapsible Sidebar]
     │ (select / new / rename / delete)
     ▼
[AiAssistantPageController]
     │
     ├──► [AiChatHistoryStore]
     │        ├── Reads/Writes `.ai_chats/<sessionId>.json`
     │        └── Syncs with [AiChatDbService] / [DatabaseManager] (H2)
     │
     └──► When sending prompt:
              │
              ▼
         [AiTokenOptimizer]
              ├── Adaptive Sliding Window (last N turns)
              ├── Historical Bulky Code & DOM Truncation
              ├── Progressive Rolling Summary
              └── Token Budget Enforcement
              │
              ▼
         [OpenAiClient / Ollama API]
```

---

## 3. Database Schema Updates
```sql
CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id VARCHAR(255) PRIMARY KEY,
    suite_key VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    pinned BOOLEAN DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

ALTER TABLE ai_chat_messages ADD COLUMN IF NOT EXISTS session_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_chat_sessions ON ai_chat_sessions(suite_key, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_session_id ON ai_chat_messages(session_id, created_at);
```

---

## 4. Key Verification & Test Strategy
- `AiTokenOptimizerTest`:
  - Test sliding window with rolling summary generation.
  - Test bulky code block truncation in older messages.
  - Test strict budget enforcement under heavy chat loads.
- `AiChatHistoryStoreTest`:
  - Test multi-session creation, retrieval, switching, renaming, and deletion.
  - Test legacy `.ai_chat.json` automatic migration.
- `AiChatDbServiceTest` & `AiAssistantChatFlowTest`:
  - Test session listing and message querying by `session_id`.
- UI smoke testing & Maven build validation (`mvn clean test`).
