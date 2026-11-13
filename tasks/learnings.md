# Learnings — Phase 5.1 Agent Crew

- Hybrid UI: keep full artifacts in crew-memory refs; chat only ≤160 char summaries.
- Clarify must use `show()` + `asyncio.Future`, never blocking exec inside `@asyncSlot`.
- Bundled MCP in-process avoids npx/install friction; keep external Playwright optional.
- ModelRouter `(task, complexity)→tier` lets one turn use different models per role.
- Migrate flat `aiConfig` → `connections[]` for backward-compatible settings.json.
