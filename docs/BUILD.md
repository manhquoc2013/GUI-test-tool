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

## Packaging (zero-install release)

End users do **not** need Python / `uv` / Node. Chromium is **not** bundled — the app downloads it lazily on first Launch/Sync/Run.

### Metadata SSOT

```bash
uv run python -m ai_qa_test_studio --print-metadata-json
uv run python -m ai_qa_test_studio --version
uv run python -m ai_qa_test_studio --smoke-check   # Qt + paths; CI gate
```

Packaging scripts must consume JSON from `--print-metadata-json` (do not regex `constants.py`).

### Portable ZIP (all OS)

```bash
# macOS / Linux
bash scripts/package_app.sh
# Windows
./scripts/package_app.ps1
```

Artifacts: `dist/AIQATestStudio-<version>-<os>-<arch>.zip`.

| Channel | Data directory |
|---------|----------------|
| Portable ZIP | `{app_dir}/data` next to the binary (writable) |
| Windows Setup / Linux `.deb` | falls back to `%LOCALAPPDATA%\AIQATestStudio` or `~/.local/share/AIQATestStudio` |

### Setup installers

```bash
# After package_app (onedir / .app present)
./scripts/build-installer-win.ps1    # Inno → *-Setup.exe
bash scripts/build-dmg.sh            # macOS .dmg (+ codesign/notarize if secrets set)
bash scripts/build-deb.sh            # Linux .deb → /opt/ai-qa-test-studio + /usr/local/bin/ai-qa-test-studio
```

### Signing / authenticity (optional secrets)

- Always: `bash scripts/make_checksums.sh dist` → `SHA256SUMS` + `SHA256SUMS.asc`
- Manifest signature: `RELEASE_SIGNING_KEY` (armored OpenPGP secret key) → `scripts/release_sign.py`. The public half is pinned in `src/ai_qa_test_studio/resources/release_signing_key.asc`; the in-app updater refuses anything not signed by it.
- Windows: `WINDOWS_CERT_PFX` accepts **a path or the base64 body** of the `.pfx` + `WINDOWS_CERT_PASSWORD` → `scripts/windows_sign.ps1` (root `.exe`, `_internal/**/*.exe`, and `*.dll|*.pyd` with `-IncludeLibraries`). Inno also signs the Setup **and the generated uninstaller** through `SignTool=aiqa` + `SignedUninstaller=yes`, wired by `scripts/inno_sign.ps1`.
- macOS: `MACOS_CODESIGN_IDENTITY` → `scripts/macos_codesign.sh` (**no `--deep`**; inside-out + hardened runtime + entitlements in `packaging/entitlements.plist`). `build-dmg.sh` then codesigns the DMG (not just the app), notarizes it and staples it. Notarization needs **either** `MACOS_NOTARY_APPLE_ID` + `MACOS_NOTARY_TEAM_ID` + `MACOS_NOTARY_PASSWORD` (preferred on CI, no keychain profile needed) **or** `MACOS_NOTARY_PROFILE`.
- macOS certificate import: `MACOS_CERT_P12` (base64 `.p12`) + `MACOS_CERT_PASSWORD`. CI imports it into an ephemeral keychain (with `set-key-partition-list`, mandatory on macOS 11+) and deletes the keychain afterwards.

Both platform jobs end with an explicit verification step (`Get-AuthenticodeSignature` / `codesign --verify` + `spctl`), so a broken timestamp, expired cert or missing ticket fails the release instead of shipping silently. Without the secrets the build still succeeds but warns loudly and produces unsigned artifacts.

Push every secret in one shot (streams values over stdin, never argv):

```bash
gh auth login
WINDOWS_CERT_PFX_FILE=/path/cert.pfx WINDOWS_CERT_PASSWORD=... \
MACOS_CERT_P12_FILE=/path/devid.p12 MACOS_CERT_PASSWORD=... \
MACOS_CODESIGN_IDENTITY="Developer ID Application: Name (TEAMID)" \
MACOS_NOTARY_APPLE_ID=... MACOS_NOTARY_TEAM_ID=... MACOS_NOTARY_PASSWORD=... \
  bash scripts/setup_github_secrets.sh
```

`publish` refuses to run without `RELEASE_SIGNING_KEY` (`scripts/release_sign.py` exits non-zero), so unsigned updates can never reach users.

CI: [`.github/workflows/release-v4.yml`](../.github/workflows/release-v4.yml) builds ZIP + Setup per OS on `v*` tags. **Do not** run `playwright install` before PyInstaller.

### Linux end-user shared libraries

If Chromium fails to start after download, install (Ubuntu/Debian example):

```bash
sudo apt-get install -y libnss3 libatk-bridge2.0-0 libgbm1 libasound2t64 || sudo apt-get install -y libasound2
```

External MCP (`npx` Playwright/Midscene) remains optional — without Node the app uses bundled MCP fallback.

### Arch notes

`macos-latest` CI produces **arm64**. Intel Macs need a separate build host if required.

---

## Environment notes

| Topic | Guidance |
|-------|----------|
| AI keys | System Settings → AI connections (keyring; fail-closed if keyring missing) |
| MCP | Bundled servers start with the app; external Playwright MCP is optional in Settings |
| macOS AX | Prefer hide/show tree filter over clear+rebuild; InfoBar instead of modal in async slots |
| Display | Requires a desktop session (or virtual framebuffer) for GUI tests |
| Gatekeeper | Unsigned macOS builds may need right-click → Open until notarized |

---

## Legacy JavaFX (≤3.x)

Maven / `mvn javafx:run` / legacy `scripts/build-portable.sh` apply **only** to archived JavaFX sources. v4 uses PyInstaller + `release-v4.yml`.
