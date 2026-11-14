# Learnings — MCP + Skills + Agents

- Progressive skills only help when catalog/body reach LLM context; combo UI alone is dead weight.
- External MCP spawn without JSON-RPC `tools/call` is not a client — use the official `mcp` SDK + AsyncExitStack sessions.
- Midscene must share app CDP; bundled heuristic is a fallback, not a vision substitute.
- Knowledge MCP capabilities (headroom/markitdown/memory) belong as bundled in-process servers for zero-install UX.
- Analyzer/Optimizer should stay Settings opt-in to preserve default Copilot latency.
- `docs/knowledge/` pollutes codegraph — keep AGENTS.md pointing agents at `src/` + active SPEC.
