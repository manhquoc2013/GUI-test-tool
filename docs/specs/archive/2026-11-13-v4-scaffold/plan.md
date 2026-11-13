# Plan: AI QA Test Studio v4.0

Binding specs: `SPEC.md`, `CAPABILITY_MAP.md`, approved Cursor plan (review-revised + 3 residual mitigations).

## Dependency graph

```text
foundation-scaffold
├── core-models-storage ──► token-governance ──► browser-engine ──► agent-runtime
│                                                              ├──► ui-workspace
│                                                              └──► reporting-packaging
└── mcp-host-skills ──────────────────────────────────────────┘
```

`reporting-packaging` depends on `core-models-storage` + `agent-runtime` only (never `ui-workspace`).

## Phase plan

### Phase 0 — Spec bootstrap (done when this file + SPEC/CAPABILITY_MAP/todo/.ai-context exist)
Archive JavaFX initiative; lock invariants into docs.

### Phase 1 — Core models & migration (`foundation-scaffold` + `core-models-storage`)
- Move Java tree → `legacy/javafx/`.
- `uv` project, package `ai_qa_test_studio`, deps per SPEC (`markitdown[docx,pdf,xlsx]`).
- Pydantic ISO models + SharedElement; storage; env/secrets; importer + `legacy_locator_normalizer`.
- Gate: ruff/mypy/pytest importer idempotent; root has no Java build files.

### Phase 2 — MCP host, skills, token (`token-governance` + `mcp-host-skills`)
- In-process headroom + markitdown + tiktoken + sliding window (no live Page).
- `mcp_manager` + BinaryResolver; `mcp_servers.json` templates; stub MCP fixture.
- Progressive skill loader + 4 seed skills; Proactor + qasync entry stub.
- Gate: qasync + **stub** MCP stdio smoke (no `@playwright/mcp` / Chromium).

### Phase 3 — Browser + agent (`browser-engine` + `agent-runtime`)
- Single context; `--remote-debugging-port=0`; parse WS port; inject CDP into `StdioServerParameters` in RAM (confirm flag via `--help`).
- DOMDistiller, locator resolver, Auto-Binder 3-tier, Dual-Loop, Healer; midscene via same CDP/screenshots.
- Gate: UNBOUND→BOUND→HEALED on local HTML; assert one browser process.

### Phase 4A — UI core (`ui-workspace` slice)
- 3 panes + drawer; Spec/No-Code/YAML/Code sync; Run/Stop logs; first-run Chromium install.
- Gate: pytest-qt open/edit/run.

### Phase 4B — Wizard & inspector
- AI Wizard; CDP screencast + HiDPI coord transform; Command Palette; QSS a11y.
- Gate: contrast + ≥44px targets; empty/skeleton/error.

### Phase 5 — Reporting & packaging (`reporting-packaging`)
- HTML Visual Replay, IEEE 829 Excel/RTM, pytest+GHA export, PyInstaller.
- Subprocess CLI test: `"PySide6" not in sys.modules`.
- Gate: headless export; packaging dry-run.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Dirty git from ephemeral CDP port | In-memory `StdioServerParameters` only |
| Wrong playwright-mcp CLI flag | `npx @playwright/mcp@latest --help` at Phase 3 |
| HiDPI miss-clicks | Letterbox-aware CSS coord formula |
| Fat PyInstaller from markitdown | Extras `docx,pdf,xlsx` only |
| Reporting accidentally imports UI | Phase 5 subprocess module assert |
| Dual Chromium from early MCP smoke | Stub MCP in Phase 2 |

## Parallelism after Phase 3

`ui-workspace` (4A/4B) and `reporting-packaging` (5) may proceed in parallel once `agent-runtime` gates pass; merge sequentially to avoid file races on shared models.
