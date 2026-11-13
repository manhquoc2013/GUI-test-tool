#!/usr/bin/env bash
set -euo pipefail
# PyInstaller packaging helpers (Chromium not bundled — first-run install).
uv run pyinstaller --noconfirm --windowed --name "AIQATestStudio" \
  --paths src \
  src/ai_qa_test_studio/__main__.py
