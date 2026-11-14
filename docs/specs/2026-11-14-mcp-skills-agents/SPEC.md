# Spec: MCP + Skills + Agents Runtime (P0→P2)

**Status**: ACTIVE  
**Product**: AI QA Test Studio  
**Date**: 2026-11-14  
**Map**: `CAPABILITY_MAP.md`

## Objective

Close the gap between knowledge-derived agent patterns and the live Studio runtime: skills must load into Copilot/Dual-Loop; external MCP must speak JSON-RPC; Midscene vision uses CDP with heuristic fallback; headroom/markitdown/memory ship as **bundled in-process MCP** (not lib-only wrappers); Analyzer/Optimizer on by default (Settings can disable); essential external MCP (Playwright, Midscene) auto-on; Figma/DB remain opt-in.

**Success**: ruff / mypy / pytest green; each module task has atomic commit; skill content reaches LLM context; `tools/call` works against external Midscene/Playwright; new skills discoverable; persistent notes survive sessions under `.studio/`.

## Locked decisions

1. Scope = full P0→P2.
2. Midscene = external MCP + bundled heuristic fallback.
3. Crew = Planner/Composer/Critic + Analyzer + Optimizer (on by default; Settings can disable).
4. Atomic git commit per completed task (user override for this initiative).
5. Memory = local-first under project `.studio/` via bundled MCP.
6. Knowledge MCP capabilities (headroom, markitdown, memory) = **bundled MCP servers inside the app**, not pip-only helpers.
7. Essential MCP auto-on: all bundled + external playwright + midscene. Figma/DB disabled until Settings enable.
8. One Chromium via app CDP; never persist ephemeral CDP ports to `mcp_servers.json`.
9. Do not vendor ECC/Ruflo/agency wholesale.

## ASSUMPTIONS

1. `mcp` Python SDK (already in pyproject) is the preferred JSON-RPC client for stdio servers.
2. Headroom API may be `crush` or `compress` — probe both; fallback to `TokenBudget.truncate_to_cap`.
3. Markitdown conversion is sync and CPU-bound — expose via bundled tool, call off UI thread when wired from UI later (v1: agent/tool path only).
4. Persistent memory stores plain text notes keyed by domain/project path; no vector DB in v1.
5. Settings model already has `mcp.external.playwright|midscene` — extend for figma/db + analyzer/optimizer flags.

## Commands

```
uv run ruff check src tests
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
```

## Project structure (touch points)

```
src/ai_qa_test_studio/agent/
  skill_loader.py, dual_loop_agent.py, orchestrator.py
  mcp_manager.py, external_mcp_bridge.py, mcp_client.py (new)
  bundled_mcp.py, mcp_servers/qa_*.py
skills/*/SKILL.md
mcp_servers.json
src/ai_qa_test_studio/core/models.py  # settings flags
ui/widgets/system_settings_dialog.py
AGENTS.md (new)
```

## Testing strategy

- Unit: skill frontmatter + inject context; MCP client initialize/list/call (mocked stdio); Midscene fallback path; ingest/compress/memory tools; role ACL for Analyzer/Optimizer; settings defaults.
- Regression: existing `test_qa_mcp_tools`, `test_orchestrator`, `test_skill_loader`, `test_external_mcp_bridge`.
- Gates: ruff + mypy + full pytest before initiative complete.

## Boundaries

- No Ruflo/ECC bulk install into runtime.
- No second Chromium for Midscene/Playwright MCP.
- No auto-enable Figma/DB (secrets/credentials).
- No vector/HNSW memory in v1.
- No UI redesign beyond Settings toggles + skill combo behavior.
- Docs under `docs/knowledge/` remain reference only (not shipped as runtime).
