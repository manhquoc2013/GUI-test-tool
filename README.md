# AI QA Test Studio

> Autonomous Shift-Left & MCP-powered AI QA desktop studio — Python / PySide6 / Playwright.

[![Python](https://img.shields.io/badge/Python-3.11+-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![PySide6](https://img.shields.io/badge/PySide6-Fluent-41CD52?logo=qt&logoColor=white)](https://doc.qt.io/qtforpython/)
[![Playwright](https://img.shields.io/badge/Playwright-Python-2DAD59?logo=playwright&logoColor=white)](https://playwright.dev/python/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Version:** 4.1 (Airy UI) · Package: `ai_qa_test_studio`  
**Spec:** [`SPEC.md`](SPEC.md) · **Architecture:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · **User guide:** [`docs/guide/USER_GUIDE.md`](docs/guide/USER_GUIDE.md)

JavaFX Test Studio (≤3.x) lives under `docs/legacy/javafx/` for reference only.

---

## What it does

- **ISO 29119-style workspace** — Explorer (Kịch bản / Elements / Báo cáo) + airy Editor (Spec · Steps · YAML · Pytest).
- **QA Copilot crew** — Planner → Composer → Critic with short UI summaries, clarify popup, Apply artifacts.
- **Airy shell** — Default **2-pane** (Sidebar + Canvas); Copilot / Browser in a **right drawer** (closed on start).
- **CDP screencast** — Launch Chromium, Inspect, Sync/bind UNBOUND steps, Dual-Loop run.
- **Bundled MCP** — In-process `qa-compress` / `qa-crew-memory` / `qa-project-context` (zero install).
- **System Settings** — Multi AI API connections, multi models, task×complexity routing.
- **Exports** — HTML Visual Replay, Excel IEEE 829 + RTM, Pytest project scaffold.

---

## Quick start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Python | ≥ 3.11 |
| [uv](https://github.com/astral-sh/uv) | latest |
| Playwright browsers | installed via `playwright install` |

### Run

```bash
git clone <repository-url>
cd GUI-test-tool
uv sync --extra dev
uv run playwright install chromium
uv run python -m ai_qa_test_studio
```

Default window: **1200×720** (min **800×600**). Data root defaults to `./data`.

### Gates

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

Headless report CLI (no GUI):

```bash
uv run python -m ai_qa_test_studio.reporting.cli --help
```

---

## Layout (Airy v4.1)

```text
┌─ Top Bar: ☰ · Tạo bằng AI · URL · Sync(N) · Chạy · Copilot · ⋯ ─┐
│ Sidebar │              Editor canvas              │ [Drawer]   │
│ Kịch bản│  title · Spec|Steps|YAML|Pytest         │ Copilot or │
│ Elements│  step cards / form                      │ Browser    │
│ Báo cáo │                                         │ (on demand)│
└─────────────────────────────────────────────────────────────────┘
```

| Shortcut | Action |
|----------|--------|
| `Ctrl+K` | Command Palette |
| `Ctrl+\` | Toggle sidebar |
| `Ctrl+I` | Toggle Copilot drawer |
| `Ctrl+Enter` | Run current case |
| `Esc` | Close drawer / Stop |
| `Ctrl+S` | Save project |

---

## Project layout

```text
src/ai_qa_test_studio/
  agent/          # orchestrator, model_router, bundled MCP, Dual-Loop
  browser/        # CDP engine, DOM distiller, visual inspector
  core/           # models, storage, settings, chat history
  ui/             # main_window, theme, widgets (Airy)
  reporting/      # HTML / Excel / Pytest (no PySide6)
docs/             # ARCHITECTURE, ADRs, design, guide
tests/            # unit / ui / integration
skills/           # Progressive skill packs
```

---

## Documentation map

| Doc | Purpose |
|-----|---------|
| [`SPEC.md`](SPEC.md) | Binding product requirements (Crew + Airy) |
| [`CAPABILITY_MAP.md`](CAPABILITY_MAP.md) | Module responsibilities |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Runtime architecture |
| [`docs/BUILD.md`](docs/BUILD.md) | Dev / package build |
| [`docs/guide/USER_GUIDE.md`](docs/guide/USER_GUIDE.md) | End-user guide (VN) |
| [`docs/adr/`](docs/adr/) | Architecture Decision Records |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history |
| [`docs/RELEASE_NOTES.md`](docs/RELEASE_NOTES.md) | Release narratives |

---

## License

MIT — see [LICENSE](LICENSE).
