# Learnings — Review Remediation (2026-11-14)

## Decisions
- Cooperative cancel beats hard `engine.stop()` mid-run (Hermes/Orca settle pattern).
- Heal must not treat “locator resolves” as healthy after action failure.
- Heuristic bind must fail closed (UNBOUND), never first-node default.
- API keys belong in OS keyring; JSON refs only (`keyring:<slot>`).
- ADR 0003 restored: push ≥960, overlay <960 (not always-overlay).
- MainWindow logic lives in controllers; window keeps test-facing public attrs.

## Patterns established
- `SessionController._active_run` Future + `should_cancel` callback into DualLoop.
- Clarify×Stop via `asyncio.wait` + `ClarifyDialog.abort_stopped()`.
- `LayoutController` push = workspace `setContentsMargins(0,0,drawer_w,0)`.
- Gateway caches `AsyncOpenAI` per instance; resolve keys at ModelRouter boundary.

## Gotchas
- `uv.lock` is gitignored — still run `uv add` so local envs pick up `keyring`.
- Async generators cannot `return value` — use out-lists for role results.
- Keep `_require_case` / `DEFAULT_TARGET_URL` strings discoverable for inspect-based UI tests.
- CI without keyring backend: tests monkeypatch `keyring.get/set_password`.
