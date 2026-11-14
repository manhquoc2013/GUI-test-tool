# Capability Map: MCP + Skills + Agents (P0→P2)

| Module id | Responsibility | Depends on |
|-----------|----------------|------------|
| skill-runtime | Progressive skills wired into Copilot + Dual-Loop (`load_skill`, catalog, frontmatter) | — |
| mcp-client | JSON-RPC stdio MCP client for external servers (list/call tools) | — |
| midscene-vision | External Midscene via CDP + heuristic bundled fallback | mcp-client |
| bundled-knowledge-mcp | In-app MCP: compress (headroom), ingest (markitdown), persistent memory | — |
| crew-roles | Analyzer + Optimizer on by default (Settings can disable); role-gated tools | skill-runtime, bundled-knowledge-mcp |
| new-skills | a11y / flaky / security-negative / traceability + fix existing frontmatter | skill-runtime |
| settings-mcp-defaults | Essential MCP auto-on; Figma/DB opt-in; Settings toggles | mcp-client, midscene-vision |
| agent-docs | `AGENTS.md` + `.ai-context` update; knowledge index hygiene notes | — |

**Build order:** skill-runtime ∥ mcp-client ∥ bundled-knowledge-mcp → midscene-vision → crew-roles ∥ new-skills → settings-mcp-defaults → agent-docs

**MCP default matrix (locked):**

| Server | Mode | Default |
|--------|------|---------|
| qa-compress / qa-crew-memory / qa-project-context / qa-playwright / qa-midscene | Bundled | Always on |
| qa-ingest (markitdown) / qa-persistent-memory | Bundled | Always on |
| external playwright / midscene | External stdio | **Auto-on** |
| external figma / db | External stdio | Disabled (Settings opt-in) |
