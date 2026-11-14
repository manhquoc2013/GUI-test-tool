# Capability Map: Copilot History + Parallel Runs

| Module id | Responsibility | Depends on |
|---|---|---|
| chat-run-registry | Per-session in-flight turns, cancel flags, unread when finish off-view | — |
| chat-history-rail | Collapsible session list, select/load/delete/new, restore transcript, running/unread badges | chat-run-registry, `ChatHistoryStore` |
| parallel-session-switch | Switch conversations without cancelling live agents; route events to live session; per-view busy/stop | chat-run-registry, chat-history-rail |

Build order: `chat-run-registry` → `chat-history-rail` → `parallel-session-switch`
