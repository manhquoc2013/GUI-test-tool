# Explorer entity forms Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Full suite/case create forms, Explorer preview edit+run with browser inheritance (default Playwright Chromium), timestamps local `HH:mm:ss dd/MM/yyyy`, workspace Case mới header + ? tooltip.

**Architecture:** Extend `TestSuite`/`TestCase` + `EntityTime`/`RunTargetResolve` helpers; shared create dialog FXML; enrich Explorer preview; slim Workspace toolbar.

**Tech Stack:** JavaFX 26, Jackson, existing ProjectStore / DialogTheme / BrowserDetector.

**Spec:** `docs/superpowers/specs/2026-07-25-explorer-entity-forms-design.md`

## Tasks

- [x] Task 1: Model fields + EntityTime + resolve browser/headless helpers
- [x] Task 2: ProjectStore create/update/backfill
- [x] Task 3: Shared EntityFormDialog (suite/case create + field binding helpers)
- [x] Task 4: Explorer preview form + save + run resolve
- [x] Task 5: Workspace layout + Case mới modal + record/run resolve
- [x] Task 6: Compile + update `.ai-context.md`
