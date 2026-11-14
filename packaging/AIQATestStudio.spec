# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller onedir spec for AI QA Test Studio (Chromium not bundled)."""

from __future__ import annotations

import sys
from pathlib import Path

from PyInstaller.utils.hooks import collect_all, collect_data_files

ROOT = Path(SPECPATH).resolve().parent
SRC = ROOT / "src"
SKILLS = ROOT / "skills"
MCP_JSON = ROOT / "mcp_servers.json"
ICON_ICO = ROOT / "assets" / "icon.ico"
ICON_ICNS = ROOT / "assets" / "icon.icns"

datas: list[tuple[str, str]] = []
binaries: list[tuple[str, str]] = []
hiddenimports: list[str] = []

for pkg in ("playwright", "qfluentwidgets", "mcp", "markitdown", "keyring"):
    try:
        pkg_datas, pkg_bins, pkg_hidden = collect_all(pkg)
        datas += pkg_datas
        binaries += pkg_bins
        hiddenimports += pkg_hidden
    except Exception as exc:  # noqa: BLE001
        print(f"collect_all({pkg}) skipped: {exc}", file=sys.stderr)

datas += collect_data_files("PySide6")
if SKILLS.is_dir():
    datas.append((str(SKILLS), "skills"))
if MCP_JSON.is_file():
    datas.append((str(MCP_JSON), "."))

# Exclude Playwright browser cache if somehow present in package tree
datas = [
    item
    for item in datas
    if ".local-browsers" not in str(item[0]).replace("\\", "/")
]

hiddenimports += [
    "ai_qa_test_studio",
    "ai_qa_test_studio.__main__",
    "ai_qa_test_studio.runtime_paths",
    "ai_qa_test_studio.app_metadata",
    "keyring.backends",
    "keyring.backends.macOS",
    "keyring.backends.Windows",
    "keyring.backends.SecretService",
    "openai",
    "google.genai",
]

block_cipher = None

a = Analysis(
    [str(SRC / "ai_qa_test_studio" / "__main__.py")],
    pathex=[str(SRC)],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe_kwargs: dict = {
    "name": "AIQATestStudio",
    "debug": False,
    "bootloader_ignore_signals": False,
    "strip": False,
    "upx": False,
    "console": False,
    "disable_windowed_traceback": False,
    "argv_emulation": False,
    "target_arch": None,
    "codesign_identity": None,
    "entitlements_file": None,
}
if sys.platform == "darwin" and ICON_ICNS.is_file():
    exe_kwargs["icon"] = str(ICON_ICNS)
elif ICON_ICO.is_file():
    exe_kwargs["icon"] = str(ICON_ICO)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    **exe_kwargs,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="AIQATestStudio",
)

if sys.platform == "darwin":
    app = BUNDLE(
        coll,
        name="AIQATestStudio.app",
        icon=str(ICON_ICNS) if ICON_ICNS.is_file() else None,
        bundle_identifier="com.manhquoc.aiqateststudio",
        info_plist={
            "CFBundleDisplayName": "AI QA Test Studio",
            "CFBundleName": "AIQATestStudio",
            "CFBundleIdentifier": "com.manhquoc.aiqateststudio",
            "NSHumanReadableCopyright": "Copyright © 2026 Mạnh Quốc",
            "NSHighResolutionCapable": True,
        },
    )
