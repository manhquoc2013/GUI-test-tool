# Spec: AI QA Test Studio v4.0

**Status**: ACTIVE  
**Prior initiative**: archived at `docs/specs/archive/2026-11-13-javafx-ui-redesign/`  
**Blueprint**: `docs/Bản Thiết Kế Tổng Thể AI QA Test Studio.md`  
**Capability index**: `CAPABILITY_MAP.md`

## Objective

Rebuild Test Studio as a cross-platform **Autonomous MCP-Powered AI QA Studio** (Python ≥3.11, PySide6 + qasync) that:

1. Eliminates LLM one-shot / max_tokens breakage via Dual-Loop (ReAct + verification) and 5-layer token governance.
2. Supports **Shift-Left**: semantic `UNBOUND` scripts from SRS/Figma → `BOUND`/`HEALED` on real URL via Auto-Binder.
3. Uses plug-and-play MCP + modular `skills/*/SKILL.md` (Progressive Disclosure).
4. Keeps Shared Element Repository + hybrid DOM/vision fallback.
5. Ships ISO/IEC/IEEE 29119 hierarchy and enterprise HTML/Excel/pytest export.

**Users**: manual QA (no-code), automation engineers (YAML/Python), CI (headless reporting).

**Success**: end-to-end path Wizard → Scenario/Case → UNBOUND→BOUND→HEALED → Dual-Loop run → HTML/Excel/CLI report; gates in `tasks/todo.md` all green; zero PySide6 import in reporting CLI.

## Tech Stack

| Area | Choice |
|---|---|
| Language / packaging | Python ≥3.11, `uv`, package `ai_qa_test_studio` |
| UI | PySide6 + qasync (no QtWebEngine) |
| Browser | Playwright Python — **single** BrowserContext + ephemeral CDP |
| MCP | `mcp` SDK; stdio for playwright / midscene / figma / db (+ user); **not** headroom/markitdown |
| Token / docs | in-process `headroom-ai`, `markitdown[docx,pdf,xlsx]`, `tiktoken` |
| Models | Pydantic v2 |
| LLM | `openai`, `google-genai` |
| Reports | Jinja2 HTML, openpyxl Excel |
| Tests | pytest, pytest-qt, ruff, mypy |
| Ship | PyInstaller (optional extra) |

## Architectural invariants (non-negotiable)

1. **`reporting-packaging` ↛ `ui-workspace`** — only `core-models-storage` + `agent-runtime`. Phase 5 subprocess test: run `python -m ai_qa_test_studio.reporting.cli` and assert `"PySide6" not in sys.modules`.
2. **Single Browser Context** — `browser/engine.py` owns one Playwright context; CDP on ephemeral free port bound to `127.0.0.1`. `@playwright/mcp` and `midscene` attach only via that CDP/screenshots — never `--isolated` / second Chromium in production.
3. **CDP config in-memory** — launch with `--remote-debugging-port=0`; parse real port from `ws://127.0.0.1:<port>/...`; inject into `StdioServerParameters` in RAM. **Never** write ephemeral port back to `mcp_servers.json`. At scaffold time, verify CLI flag via `npx @playwright/mcp@latest --help` (`--cdp-endpoint` vs `--browser-url`).
4. **In-process headroom + markitdown** — declared as `markitdown[docx,pdf,xlsx]` (**not** `[all]`). MCP stdio only for playwright, midscene, figma, db (+ user). `BinaryResolver` finds absolute `node`/`npx` (Homebrew, nvm, fnm, `npx.cmd`).
5. **No QtWebEngine** — viewport = CDP `Page.startScreencast` + canvas widget. HiDPI hit-test:  
   `css_x = (widget_x - offset_x) * (viewport_width / rendered_width)` (same for y).
6. **Phase 2 MCP smoke uses stub only** — Windows: `asyncio.WindowsProactorEventLoopPolicy` at entry. Real `@playwright/mcp` only after Phase 3 CDP exists.
7. **Legacy locator normalizer** — `id=`/`css=`/`xpath=`/`By.*` → Playwright; `${var}` → `{{var}}` on import.

## Commands

```bash
uv sync
uv run ruff check .
uv run mypy src
uv run pytest
uv run python -m ai_qa_test_studio
uv run python -m ai_qa_test_studio.reporting.cli --help
# Legacy reference only (not primary gate):
cd legacy/javafx && mvn -o test
```

## Project Structure

```text
legacy/javafx/                 # archived JavaFX app (Phase 1 move)
pyproject.toml
mcp_servers.json               # static templates; no runtime port writes
skills/                        # modular testing skills
src/ai_qa_test_studio/
  __main__.py
  core/                        # models, storage, env, normalizer, token_budget
  browser/                     # engine, dom_distiller, locator_resolver, visual_inspector
  agent/                       # mcp_manager, skill_loader, generators, binder, dual_loop, healer
  reporting/                   # zero PySide6 imports
  ui/                          # PySide6 workspace
tests/
  fixtures/mcp_stub/
docs/                          # blueprint + knowledge (not runtime)
data/
SPEC.md
CAPABILITY_MAP.md
tasks/plan.md
tasks/todo.md
```

Keep: `docs/`, `docs/knowledge/`, `LICENSE`, `.git`. Do not ship `docs/knowledge/*` as runtime deps.

## Code Style

- Package imports: `from ai_qa_test_studio.core.models import TestCase`.
- Pydantic v2 models; no bare `Any` in public APIs; structured logging with context.
- Async I/O for browser/MCP; never block Qt main thread with DOM crush / LLM.
- Constants / config for ports, timeouts, token caps — no magic numbers.
- Example binding status enum usage:

```python
from typing import Literal
from pydantic import BaseModel, Field

class TestStep(BaseModel):
    step_no: int
    action: Literal["goto", "click", "fill", "select", "press_enter", "hover",
                    "assert_visible", "assert_text", "assert_url", "verify_db"]
    element_ref: str | None = None
    intent_hint: str
    input_data: str | None = None
    expected_result: str = ""
    binding_status: Literal["UNBOUND", "BOUND", "HEALED"] = "UNBOUND"
```

**UI (Phase 4):** Progressive disclosure, QSS design tokens (dark/light), empty/skeleton/error states, contrast ≥ 4.5:1, targets ≥ 44×44. Design tokens authored in Phase 4B (no `scripts/ui-search.sh` in repo).

## Testing Strategy

| Level | Where | Focus |
|---|---|---|
| Unit | `tests/unit/` | models, normalizer, token budget, skill loader, BinaryResolver |
| Integration | `tests/integration/` | storage/import; qasync+stub MCP (Phase 2); UNBOUND→BOUND→HEALED on local HTML (Phase 3); single browser process assert |
| UI smoke | `tests/ui/` | pytest-qt open/edit/run (4A); a11y contrast (4B) |
| Reporting guard | `tests/reporting/` | CLI subprocess; `"PySide6" not in sys.modules` |

Offline-first: HTML fixtures local; LLM mocked where possible. No dependency on external staging sites in CI.

## Migration (Java → v4)

- Hierarchy expands to Project → SharedElementRepo → Group → Scenario → Suite → Case → Steps.
- Steps map via normalizer; `enabled=false` preserved as skip slot.
- Assertions + TestDataProfile → `test_data_rows` / profile store.
- Settings/AI keys → env/secrets (best-effort; never plaintext in logs).
- `.java` scripts kept as reference artifacts; runtime = YAML / No-Code / pytest-playwright.
- Importer idempotent over all legacy `suite.json`.

## Boundaries

**Always**
- Honor the seven architectural invariants above.
- Run `ruff` / `mypy` / `pytest` before claiming a phase done.
- Keep reporting free of PySide6 imports.
- Inject CDP endpoint in memory only.

**Ask first**
- Adding MCP servers beyond the declared set.
- Changing ISO model fields that affect importer compatibility.
- Enabling live Figma/DB MCP (needs credentials).
- Parallel Swarm (`ruflo`) beyond single-worker stub.

**Never**
- Commit secrets / API keys.
- Spawn a second Chromium for agent/inspector/MCP in production paths.
- Use `markitdown[all]` or QtWebEngine.
- Port 729 JavaFX UI tests as the v4 gate.
- Auto-commit / auto-branch (user owns git).

## Phased delivery (~16 days)

| Phase | Focus | Exit gate |
|---|---|---|
| 0 | Archive + SPEC/CAPABILITY_MAP/tasks/.ai-context | Docs consistent with invariants |
| 1 | legacy move, models, storage, importer+normalizer | Idempotent import; root free of Java build files |
| 2 | token in-process, MCP host+BinaryResolver, skills, stub | qasync + stub MCP stdio smoke |
| 3 | browser CDP, DOMDistiller, binder, dual-loop, healer | UNBOUND→BOUND→HEALED; one browser process |
| 4A | Shell + editor sync + Run/Stop + first-run Chromium | pytest-qt open/edit/run |
| 4B | Wizard, CDP Inspector (HiDPI math), palette, QSS a11y | contrast + 44px; empty/skeleton/error |
| 5 | HTML/Excel/code export, PyInstaller, reporting CLI guard | CLI headless; no PySide6 in reporting process |

## Success Criteria

- [ ] Capability map modules implemented in dependency order with phase gates green.
- [ ] Legacy suites import with normalized locators/vars; Tier-1 bind can succeed without AI when locators match.
- [ ] Agent + Auto-Binder + Inspector share one BrowserContext via ephemeral CDP injected in RAM.
- [ ] Reporting CLI runs headless without importing PySide6 (automated assertion).
- [ ] Dual-Loop respects atomic tool calls and token caps on fixture workloads.
- [ ] Packaged app first-run installs Chromium when missing; markitdown extras stay slim.

## Open Questions

None blocking execution — residual CDP CLI flag name confirmed at Phase 3 scaffold via `--help`.
