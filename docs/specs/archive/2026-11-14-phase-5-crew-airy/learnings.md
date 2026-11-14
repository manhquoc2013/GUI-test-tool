# Learnings — Phase 5.1 / 5.2

## Crew (5.1)
- Hybrid UI: keep full artifacts in crew-memory refs; chat only ≤160 char summaries.
- Clarify must use `show()` + `asyncio.Future`, never blocking exec inside `@asyncSlot`.
- Bundled MCP in-process avoids npx/install friction; keep external Playwright optional.
- ModelRouter `(task, complexity)→tier` lets one turn use different models per role.
- Migrate flat `aiConfig` → `connections[]` for backward-compatible settings.json.

## Airy UI (5.2)
- Drawer as sibling with manual geometry (push vs overlay) beats a 3-way splitter for “closed = 0 width”.
- Breakpoint **959 is width**, not height; min height 600 is independent.
- Qt `isVisible()` is false when ancestors are hidden — UI tests should use `isHidden()` for own flag or open the drawer first.
- Per-message Copy/Undo keeps chrome to one composer row; undo-from-id shares session truncate semantics with undo-last.
- Agent Feed belongs under Browser tab so Copilot chrome stays ≤2 rows + starters.
