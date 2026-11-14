# Spec: Review Remediation — Cancel, Heal, Secrets, Shell Split

**Status**: ACTIVE  
**Product**: AI QA Test Studio v4.1  
**Date**: 2026-11-14  
**Source**: s-review findings vs `docs/knowledge/` (Hermes cancel, Orca secrets, Skyvern/Midscene heal, ADR 0001–0003)  
**Prior product SPEC**: `docs/specs/archive/2026-11-14-phase-5-crew-airy/SPEC.md`

## Objective

Close every Must / Should / Nice finding from the system review so Run/Stop, Copilot clarify, heal/bind, secrets, Airy push layout, and MainWindow modularity meet ADR contracts and knowledge-aligned patterns.

**Success**: `uv run ruff check src tests` · `uv run mypy src` · `uv run pytest` green; mutation on heal/bind no longer stays green; no plaintext API keys in `settings.json`.

## Decisions locked (interrogation 1C / 2C / 3B / 4C / 5A)

| Area | Decision |
|---|---|
| Scope | Must + Should + Nice |
| Secrets | OS keychain (`keyring`) — JSON stores refs only |
| Drawer | **Push** when `width ≥ 960`; **overlay** when `< 960` (restore ADR 0003) |
| MainWindow | Full split: Layout + Run + Copilot + Export controllers |
| Git | One atomic commit per completed task |

## Architecture changes

```text
ui/
  main_window.py          # thin shell: construct + wire controllers
  controllers/
    layout_controller.py  # breakpoints, push/overlay drawer, sidebar
    run_controller.py     # launch/sync/run/stop + session cancel
    copilot_controller.py # orchestrator, clarify, Esc/stop, history
    export_controller.py  # HTML/Excel/pytest export actions
agent/
  dual_loop_agent.py      # cooperative cancel between steps
  healer.py               # heal even when locator still matches
  auto_binder.py          # never default-bind first DOM node
  orchestrator.py         # clarify×stop race; critic clarify→user_text
  llm_gateway.py          # Gemini stream close on cancel
  bundled_mcp.py          # enforce role ACL on invoke
core/
  secret_store.py         # keyring-backed API key persistence
  env_manager.py          # write apiKeyRef slots, never raw keys
```

## Pipeline / cancel contracts

1. **Browser Run**: Stop sets `cancel_requested`; DualLoop checks between steps; Stop awaits run settle then `engine.stop()`.
2. **closeEvent**: always `await session.stop()` when started.
3. **Copilot**: Stop races clarify Future; Esc stops Copilot when chat busy; no Copilot DOM read while Run `_busy`.
4. **Clarify**: Critic resolves append `[Clarify]` into `user_text` like Planner/Composer.
5. **History**: sliding window includes composer full text and/or crew-memory refs.
6. **Heal**: post-failure path attempts DOM/vision even if semantic match count==1.
7. **Bind**: heuristic miss → `None` / UNBOUND.
8. **Verify**: expected_final_result uses normalized containment (strip/collapse whitespace), not raw substring only when empty body.
9. **Secrets**: `keyring.set_password(service, slot, key)`; settings JSON holds `keyring:<slot>` refs.
10. **Layout**: `≥960` push (shrink splitter for drawer width); `<960` geometry overlay.

## Testing / Gates

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

New/extended tests: dual-loop cancel, healer post-match fail, heuristic no default bind, clarify×stop, critic clarify merge, history refs, keychain round-trip (fake keyring), push vs overlay breakpoints, controller smoke.

## Boundaries

**In**: All review findings Must+Should+Nice; docs ADR/SPEC/ARCHITECTURE sync for push layout; `keyring` dependency.  
**Out**: Live multi-provider E2E; PyInstaller packaging; light-theme polish; unlimited swarm.

## Commit rule

One atomic git commit per task in `tasks/todo.md` after that task’s tests pass.
