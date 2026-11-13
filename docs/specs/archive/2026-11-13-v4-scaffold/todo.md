# Todo — AI QA Test Studio v4.0

## Phase 0 — Spec bootstrap
- [x] T0.1 Archive JavaFX `SPEC.md` / `tasks/plan.md` / `tasks/todo.md` → `docs/specs/archive/2026-11-13-javafx-ui-redesign/`
- [x] T0.2 Write `CAPABILITY_MAP.md` (reporting ↛ ui)
- [x] T0.3 Write `SPEC.md` (invariants + residual mitigations)
- [x] T0.4 Write `tasks/plan.md` + this `tasks/todo.md`
- [x] T0.5 Refresh `.ai-context.md` for Python/MCP stack

## Phase 1 — Core models & data migration
- [x] T1.1 Move Java app to `legacy/javafx/`
- [x] T1.2 Create `pyproject.toml` + package `ai_qa_test_studio` via `uv` (`markitdown[docx,pdf,xlsx]`, `PySide6-Essentials`)
- [x] T1.3 Implement `core/models.py`
- [x] T1.4 Implement `storage.py` + `env_manager.py`
- [x] T1.5 Implement Java importer + `legacy_locator_normalizer.py`
- [x] T1.6 Gate: ruff / mypy / pytest importer

## Phase 2 — MCP host, skills, token governance
- [x] T2.1 `token_budget.py` in-process headroom + markitdown + tiktoken
- [x] T2.2 `mcp_servers.json` templates
- [x] T2.3 `mcp_manager.py` + `BinaryResolver` + stub MCP
- [x] T2.4 `skill_loader.py` + 4 seed skills
- [x] T2.5 Entry Proactor + qasync boot
- [x] T2.6 Gate: qasync + stub MCP stdio smoke

## Phase 3 — Dual-Loop & Shift-Left binder
- [x] T3.1–T3.4 Browser engine + ephemeral CDP in-memory injection
- [x] T3.5–T3.7 Dom distiller, binder, dual-loop, healer
- [x] T3.8 Gate: UNBOUND→BOUND→HEALED local HTML

## Phase 4A — UI core workspace
- [x] T4A.1–T4A.4 Shell + editor sync + Run/Stop + pytest-qt smoke

## Phase 4B — Wizard, inspector, polish
- [x] T4B.1–T4B.4 Wizard, CDP screencast viewport, HiDPI math, palette, QSS ≥44px

## Phase 5 — Reporting & packaging
- [x] T5.1–T5.3 HTML / Excel / code exporters
- [x] T5.4 Reporting CLI + subprocess no-PySide6 assert
- [x] T5.5 PyInstaller script + python-ci workflow
- [x] T5.6 Gate: headless CLI + packaging script present

## Final
- [x] F1 Gates green: ruff / mypy / pytest
- [x] F2 Update `.ai-context.md`
