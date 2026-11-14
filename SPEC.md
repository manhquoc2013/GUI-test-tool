# Spec: Bugbot P0 + Bundled Midscene/Playwright MCP

**Status**: ACTIVE  
**Date**: 2026-11-14  
**Prior**: `docs/specs/archive/2026-11-14-review-remediation/`  
**Decisions**: 1C Full Midscene bridge · 2A cancel poll around steps · 3B missing role → `system` · 4A atomic commits · build missing MCPs in-app

## Objective

Close all Bugbot findings and ship zero-install vision heal via bundled MCP, with optional external `@midscene/mcp` / `@playwright/mcp` upgrade path.

**Success**: `ruff` / `mypy` / `pytest` green; DualLoop calls vision locate when midscene enabled; Esc/Stop clears `_busy` and schedules async stop; `lean_tail_max_tokens` bounds history; Gemini client reused.

## Decisions locked

| Area | Decision |
|---|---|
| Midscene | Bundled in-process `qa-midscene` (locate via DOM map + screenshot metadata); when `mcp.external.midscene` and BinaryResolver finds `npx`, optionally start external `@midscene/mcp` with CDP — never second Chromium |
| Playwright MCP | Bundled `qa-playwright` tools (`screenshot`, `cdp_info`) for heal/run; external `@playwright/mcp` remains in `mcp_servers.json` |
| Cancel | Poll `should_cancel` before each step, after execute, before/after heal (2A) |
| MCP ACL | `role=None` → `ROLE_SYSTEM` with compress/get/locate only (3B) |
| Memory | `llm_window` accepts token budget from `lean_tail_max_tokens` |
| Stop/Esc | `request_cancel()` sync + `create_task(stop_browser)`; `stop_browser` finally clears `_busy` |

## New modules

```text
agent/mcp_servers/qa_midscene.py      # locate(intent, element_map|dom) → css
agent/mcp_servers/qa_playwright.py    # screenshot / cdp_info bound to page factory
agent/vision_bridge.py                # DualLoop vision_locate → bundled (+ optional external)
```

## Testing / Gates

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

## Boundaries

**In**: Bugbot 8 findings; bundled Midscene + Playwright MCP; ACL system role; lean tail; Gemini cache; settings draft seal.  
**Out**: Figma/DB MCP enablement; second browser; live Midscene cloud E2E without local npx.
