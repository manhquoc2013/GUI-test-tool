# Learnings — Bugbot P0 + Midscene MCP

## Decisions
- Bundled `qa-midscene` is the zero-install heal path; external `@midscene/mcp` is optional via npx+CDP.
- Bundled `qa-playwright` exposes screenshot/cdp_info against the single BrowserContext.
- `role=None` → ROLE_SYSTEM (compress/get/locate/screenshot only).
- Esc: sync `request_cancel` + `create_task(stop)`; stop finally clears `_busy`.

## Patterns
- Vision bridge: DomDistiller → mcp.invoke(qa-midscene.locate) → healer vision_locate.
- ExternalMcpBridge never launches a second Chromium; CDP injected in-memory only.

## Gotchas
- Playwright page.screenshot is async — sync MCP screenshot returns available=False with reason async_page; heal uses DomDistiller path.
- Starting npx Midscene without network/cache will log and fall back to bundled.
