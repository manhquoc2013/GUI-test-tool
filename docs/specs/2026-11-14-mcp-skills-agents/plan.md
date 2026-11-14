# Plan: MCP + Skills + Agents

## Vertical slices

1. **Skill runtime** — frontmatter parse, catalog in Planner, inject selected skill into Dual-Loop, implement `load_skill` micro-tool path.
2. **MCP client** — stdio JSON-RPC session (initialize → tools/list → tools/call); wire into ExternalMcpBridge.
3. **Bundled knowledge MCP** — `qa-ingest` (markitdown), harden `qa-compress` as MCP surface over headroom, `qa-persistent-memory` on `.studio/agent_memory.db`.
4. **Midscene vision** — call external Midscene locate when process healthy; else bundled heuristic; CDP in-memory only.
5. **Crew roles** — Analyzer/Optimizer prompts + Settings flags + role tool ACL.
6. **New skills** — a11y, flaky-quarantine, security-negative, traceability; fix 4 missing frontmatters.
7. **Settings defaults** — playwright/midscene default True; figma/db False + UI checkboxes; sync `mcp_servers.json` disabled flags.
8. **Agent docs** — `AGENTS.md` + `.ai-context.md` snapshot.

## Invariants

- Role ACL on every bundled `invoke`.
- External CDP args never written to disk.
- Skill load failure is soft (log + continue), never crash run.
- Analyzer/Optimizer off → identical latency path to today.
- Commit message style: conventional, why-focused, one task per commit.

## Risks

| Risk | Mitigation |
|------|------------|
| Midscene MCP tool names differ by version | Probe tools/list; map known aliases; fallback heuristic |
| headroom API churn | Dual import path + truncate fallback (already started) |
| MCP SDK async vs qasync | Keep client asyncio-native; call from existing async run paths |
| Large markitdown docs | Cap via TOKEN_CONTEXT_BUDGET before returning to LLM |
