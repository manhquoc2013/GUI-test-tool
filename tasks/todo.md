# Todo — QA Copilot + 3-Role Orchestrator (Phase 5)

## Spec bootstrap
- [x] C0.1 Archive Phase 4C → `docs/specs/archive/2026-11-13-phase-4c-fluent-ui/`
- [x] C0.2 Write Phase 5 `SPEC.md` + ADR 0001 (Planner/Composer/Critic **default**)
- [x] C0.3 Update `CAPABILITY_MAP.md` / `tasks/plan.md` / `.ai-context.md`
- [x] C0.4 Lock offline mock + persist path defaults

## Copilot implementation
- [x] C1 `CopilotChatPane` + Segmented in `BrowserAgentPane` (history / Stop / Copy / Undo→fill)
- [x] C2 `UnifiedLLMGateway` streaming + EnvManager provider + cancel
- [x] C3 `Orchestrator` default Planner → Composer → Critic (1 Critic pass)
- [x] C4 Compose mode + Apply UNBOUND drafts; Top Bar/Palette → Copilot
- [x] C5 Case chips `@DOM`/`@Error` + Apply Test Data; token/DOM distill

## Gates
- [x] `uv run ruff check src tests && uv run mypy src && uv run pytest` (32 passed)
