# Plan: QA Copilot + 3-Role Orchestrator (Phase 5)

Binding: `SPEC.md`, ADR `docs/adr/0001-qa-copilot-three-role-orchestrator.md`, `CAPABILITY_MAP.md` module `qa-copilot`.

## Dependency graph

```text
C0 SPEC/ADR/tasks
 └── C1 chat_pane + Segmented Copilot|Feed + history/stop/copy/undo (mock)
      └── C2 llm_gateway + EnvManager + live stream + Stop cancel
           └── C3 orchestrator Planner→Composer→Critic (default path)
                ├── C4 Compose mode + Apply drafts; deprecate Wizard entry
                └── C5 Case tools + @DOM/@Last Error chips + token guard
```

## Vertical slices

1. **C1 Shell UX** — Copilot pane, session list, Stop/Copy/Undo→fill, starters; mock async chunks.
2. **C2 Gateway** — OpenAI-compatible stream primary; optional Gemini; qasync `@asyncSlot` + abort.
3. **C3 Orchestrator** — Forced 3-role pipeline; Critic once; timeline in thread; cancel any stage.
4. **C4 Compose** — SRS/URL flow inside Copilot (reuse generator); Apply UNBOUND; Top Bar → Copilot.
5. **C5 Case mutate** — chips + Apply Test Data/steps; DOM via distiller+Headroom.

## Risks

- Token blow-up if Critic re-loops — hard cap one Critic pass (SPEC).
- Stop vs Playwright: never QMessageBox in asyncSlot; abort + InfoBar only.
- Undo must drop all role artifacts after the user turn, not only the last assistant bubble.
- Wizard dual-entry confusion — remove primary Wizard button once C4 ships.
