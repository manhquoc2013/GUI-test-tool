# Remediation — s-review deep pass

Tracked from multi-axis review + [Security Review](5d0584ff-2f67-4bbb-8066-a43d04368b65) + [Bugbot](405af40f-4754-4bf3-adbf-a11ba435214b).

## Must fix

1. Harden `_extract_css_hint` + reject garbage so bundled Midscene fallback runs
2. Add `aiLocate`; never fall back to `tool_names[0]`
3. `persistent_memory.bind_project` in Copilot/`_bind_project_context`
4. Reconnect external MCP when CDP endpoint changes
5. `aclose` AsyncExitStack on `connect()` failure (no orphan npx)

## Should fix

6. Sandbox `qa-ingest` under project root
7. Atomic + absolute path for `mcp_servers.json` sync (or user overlay)
8. Regression tests for 1–5

## Nice to have

9. Soft-fail corrupt `.usage.json`
10. Coerce memory `limit` safely
