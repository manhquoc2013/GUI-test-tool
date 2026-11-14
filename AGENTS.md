# AGENTS.md — AI QA Test Studio

Python desktop Shift-Left QA studio (PySide6). Prefer repo evidence over `docs/knowledge/` reference clones.

## Commands

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
```

## Architecture

- `agent/` — Copilot crew (Planner/Composer/Critic/Analyzer/Optimizer; last two disable via Settings), Dual-Loop, bundled MCP, external MCP JSON-RPC client
- `browser/` — Playwright engine + CDP (single Chromium)
- `core/` — models, YAML project tree, `.studio/runtime.db`, settings
- `ui/` — Airy shell; theme via `ThemeTokens` + `bind_widget_theme` only
- `skills/` — progressive SKILL.md catalog (frontmatter + optional `.usage.json`)
- `mcp_servers.json` — external MCP; CDP ports never persisted

## Invariants

- Hybrid storage: Git YAML under project + gitignored `.studio/`
- Secrets: keyring refs only
- Essential MCP auto-on: bundled servers + external Playwright/Midscene; Figma/DB opt-in
- Midscene: external locate first, bundled heuristic fallback
- Tree mutations only via `project_tree_ops`
- Do not vendor ECC/Ruflo/agency into runtime

## Context

Read `.ai-context.md` at session start. Active SPEC under `docs/specs/` (not `docs/knowledge/`).
