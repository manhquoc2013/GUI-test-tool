# Build Guide — AI QA Test Studio v4.1

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Python | **≥ 3.11** | Managed via `uv` recommended |
| [uv](https://docs.astral.sh/uv/) | latest | Syncs `.venv` from `pyproject.toml` / `uv.lock` |
| Playwright browsers | Chromium (min) | `uv run playwright install chromium` |

Optional: Qt platform plugins for your OS (shipped with PySide6 wheels).

Verify:

```bash
uv --version
uv run python -V    # ≥ 3.11
```

---

## Development setup

```bash
git clone <repository-url>
cd GUI-test-tool
uv sync --extra dev
uv run playwright install chromium
```

### Run the GUI

```bash
uv run python -m ai_qa_test_studio
# or
uv run ai-qa-test-studio
```

- Default window **1200×720**, minimum **800×600**.  
- Data directory: `./data` (project, config, copilot sessions, exports).

### Headless reporting CLI

```bash
uv run ai-qa-report --help
uv run python -m ai_qa_test_studio.reporting.cli --help
```

Reporting modules must remain free of PySide6 imports (CI / no display).

---

## Quality gates

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest
```

Focused UI smoke:

```bash
uv run pytest tests/ui -q
```

---

## Packaging (optional)

```bash
uv sync --extra build
uv run pyinstaller   # use project .spec when present
```

Produce a single-folder or onefile desktop binary with PyInstaller; bundle Chromium separately or document `playwright install` on first run.

---

## Environment notes

| Topic | Guidance |
|-------|----------|
| AI keys | System Settings → AI connections, or env vars consumed by gateway |
| MCP | Bundled servers start with the app; external Playwright MCP is optional in Settings |
| macOS AX | Prefer hide/show tree filter over clear+rebuild; InfoBar instead of modal in async slots |
| Display | Requires a desktop session (or virtual framebuffer) for GUI tests |

---

## Legacy JavaFX (≤3.x)

Maven / `mvn javafx:run` / DMG–EXE installers apply **only** to archived sources under `docs/legacy/javafx/`. They are not used by the v4 Python product.
