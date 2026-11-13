# ADR 0002 — Agent crew hybrid debate, clarify popup, bundled MCP, model router

**Status**: Accepted  
**Date**: 2026-11-14  
**Spec**: `SPEC.md` (Phase 5.1)  
**Supersedes (partial)**: ADR 0001 §Decision “Handoff = structured only / no free-form agent chat on UI”

## Context

Users need short crew status in Copilot chat, bounded agent↔agent debate, clarification popups on ambiguity, zero-install token MCP tools, multi-API/multi-model Settings, and complexity-aware model routing — without unbounded swarm cost or Stop/Undo breakage.

## Decision

1. **Hybrid UI**: User-visible bubbles are short summaries (≤160 chars). Full artifacts live in bundled crew-memory refs / Apply payloads.
2. **Bounded debate**: After Critic gaps, up to 3 agent↔agent rounds; anti-loop on repeated fingerprints (limit 2) → `loop_halted` + clarify or stop note.
3. **Clarify any role**: Emit `clarify_request`; UI `ClarifyDialog` (suggestions + free text). Skip/timeout → `default_answer` + chat note. No blocking `QMessageBox` inside `@asyncSlot`.
4. **All cognitive steps via LLM gateway** (`stream_chat`), including summarize / debate / clarify_draft. Machinery (anti-loop, timer, compress) is non-LLM.
5. **Bundled in-process MCP** (`qa-compress`, `qa-crew-memory`, `qa-project-context`) auto-registers on app start — zero user install. External Playwright/Midscene remain optional.
6. **System Settings hub**: Full app config + many AI connections + many models per connection + task×complexity routing.
7. **ModelRouter**: `(task, complexity) → tier → model`; same turn may use different models per role. Copilot combo `Auto` or pin.

## Consequences

- ADR 0001 Critic one-pass + Stop/Undo semantics remain.
- Token spend rises with debate/clarify drafts; lean context + compress mitigates.
- Settings schema migrates flat `aiConfig` → `connections[]`.
