# Spec: Zero-Friction UI Polish (Phase 4C)

**Status**: SUPERSEDED by Airy v4.1 (`SPEC.md` Phase 5.2 + ADR `0003`) — 3-column Browser+Copilot layout replaced by 2-pane + RightDrawer.  
**Parent product**: AI QA Test Studio v4.0 (scaffold archived at `docs/specs/archive/2026-11-13-v4-scaffold/`)  
**Blueprint**: `docs/Bản Thiết Kế Tổng Thể AI QA Test Studio.md` §6 (UI sections overridden by Airy design doc)  
**Capability index**: `CAPABILITY_MAP.md`

## Objective

Nâng cấp workspace PySide6 từ prototype “CLI bọc GUI” thành **Zero-Friction IDE** 4 vùng theo blueprint + UX review, với hành vi đầy đủ (không chỉ chrome):

1. Unified Top Action Bar (bỏ Run/Stop trùng góc phải).
2. Test Explorer tách Suites/Cases ↔ Shared Elements + search + status dots.
3. Tab Spec dạng form + banner UNBOUND; persist thuộc tính case.
4. Browser pane: Launch / Inspect (ghim Shared Element + gắn bước) + Agent Execution Feed.
5. Bottom Drawer thu gọn được; DataGrid format thân thiện; Status pills.

**Users**: QA manual (VN-first labels), automation engineers (YAML/Code tabs giữ EN technical tokens).

**Success**: 7 điểm nghẽn UX review biến mất; Sync/Run/Launch/Inspect/Export wire thật qua `SessionController` + exporters; gates `ruff` / `mypy` / `pytest` xanh; atomic commits theo task.

## Decisions locked (interrogation)

| # | Decision |
|---|---|
| Depth | Full functional |
| Language | VN-first UI; technical tokens EN (`BOUND`, skill ids) |
| Inspector | Ghim Shared Element + gắn bước đang chọn |
| Icons | `qtawesome` (`mdi6` / `fa5s`) |
| Git | Atomic commit mỗi task |

## Architectural invariants (inherited — non-negotiable)

1. `reporting` ↛ `ui` (no PySide6 in reporting CLI).
2. Single BrowserContext; ephemeral CDP `127.0.0.1` in RAM only.
3. No QtWebEngine — screencast PNG + HiDPI `widget_to_css_point`.
4. Accessibility: touch targets ≥44×44; `setAccessibleName` + `deleteLater()` when replacing widgets (macOS `accessibilityLabel` warning).

## Commands

```bash
uv sync
uv add qtawesome
uv run ruff check src tests
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
```

## Project structure (UI touch points)

```text
src/ai_qa_test_studio/
  constants.py                 # UI sizes, viewport, status labels
  ui/
    theme.py                   # apply_theme + load QSS
    theme.qss                  # dark tokens (#0F172A / #1E293B / #334155), 8px grid
    main_window.py             # 4-pane chrome + wiring
    session_controller.py      # Launch/Sync/Run/Stop (extend feed callbacks)
    widgets/
      test_explorer.py         # split + search
      step_builder.py          # Spec form + banner
      embedded_browser.py      # empty state + inspect chrome
      agent_feed.py            # step cards (new)
      data_grid.py             # param formatting + headers
```

## Code style

- Python ≥3.11, strict mypy on `ai_qa_test_studio`, ruff E/F/I/UP/B.
- Zero dead code; no magic URLs/ports/colors — use `constants` / theme tokens.
- VN user-facing strings; no developer-only log copy in the Agent feed.

## Testing strategy

- pytest-qt smoke: window opens, case loads, action bar URL/Sync present, drawer collapse, Spec banner when UNBOUND steps exist.
- Unit: DataGrid parameter formatting; explorer filter; unbound count helper.
- Full suite must stay green; reporting no-PySide6 guard unchanged.

## Boundaries

**In scope**: UI chrome + functional wiring of Launch/Inspect/Sync/Run/Stop/Export/New Case/Skill select; theme; qtawesome.

**Out of scope**: New MCP servers; dual-loop algorithm changes; Figma MCP live; redesign of Wizard internals beyond toolbar entry; light-theme redesign (dark IDE tokens per review).
