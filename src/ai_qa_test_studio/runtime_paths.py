"""Frozen-aware app / data / browsers paths — stdlib only (no Playwright imports)."""

from __future__ import annotations

import logging
import os
import sys
from pathlib import Path

from ai_qa_test_studio.constants import (
    APP_BROWSERS_DIRNAME,
    APP_DATA_DIRNAME,
    APP_SUPPORT_DIRNAME,
    PLAYWRIGHT_BROWSERS_PATH_ENV,
)

logger = logging.getLogger(__name__)

_data_root_override: Path | None = None
_used_user_fallback: bool = False


def is_frozen() -> bool:
    return bool(getattr(sys, "frozen", False))


def app_dir() -> Path:
    """Directory that owns the portable layout (sibling of ``data/`` when writable)."""
    if not is_frozen():
        return Path.cwd().resolve()
    exe = Path(sys.executable).resolve()
    if sys.platform == "darwin":
        # .../AIQATestStudio.app/Contents/MacOS/AIQATestStudio → folder containing .app
        parts = exe.parts
        for i, part in enumerate(parts):
            if part.endswith(".app"):
                return Path(*parts[:i]).resolve() if i > 0 else exe.parent
        # Fallback: Contents/MacOS → parents[3] when layout matches
        if len(exe.parents) >= 4 and exe.parents[1].name == "Contents":
            return exe.parents[3]
    return exe.parent


def user_data_root() -> Path:
    """OS user-writable application data directory."""
    home = Path.home()
    if sys.platform == "darwin":
        return (home / "Library" / "Application Support" / APP_SUPPORT_DIRNAME).resolve()
    if sys.platform.startswith("win"):
        local = os.environ.get("LOCALAPPDATA", "").strip()
        base = Path(local) if local else home / "AppData" / "Local"
        return (base / APP_SUPPORT_DIRNAME).resolve()
    xdg = os.environ.get("XDG_DATA_HOME", "").strip()
    if xdg:
        return (Path(xdg) / APP_SUPPORT_DIRNAME).resolve()
    return (home / ".local" / "share" / APP_SUPPORT_DIRNAME).resolve()


def _try_create_writable(candidate: Path) -> bool:
    try:
        candidate.mkdir(parents=True, exist_ok=True)
        probe = candidate / ".write_probe"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
        return True
    except OSError as exc:
        logger.info("data_root_not_writable path=%s err=%s", candidate, exc)
        return False


def data_root(*, prefer_portable: bool = True) -> Path:
    """Writable data root: portable ``{app_dir}/data`` or user Application Support."""
    global _data_root_override, _used_user_fallback
    if _data_root_override is not None:
        return _data_root_override

    portable = (app_dir() / APP_DATA_DIRNAME).resolve()
    if prefer_portable and _try_create_writable(portable):
        _used_user_fallback = False
        _data_root_override = portable
        return portable

    fallback = user_data_root()
    fallback.mkdir(parents=True, exist_ok=True)
    _used_user_fallback = True
    _data_root_override = fallback
    logger.info("data_root_fallback path=%s", fallback)
    return fallback


def used_user_data_fallback() -> bool:
    data_root()
    return _used_user_fallback


def browsers_dir() -> Path:
    path = data_root() / APP_BROWSERS_DIRNAME
    path.mkdir(parents=True, exist_ok=True)
    return path


def apply_playwright_browsers_env() -> Path:
    """Set ``PLAYWRIGHT_BROWSERS_PATH`` and return browsers directory."""
    path = browsers_dir()
    os.environ[PLAYWRIGHT_BROWSERS_PATH_ENV] = str(path)
    return path


def reset_path_cache_for_tests() -> None:
    """Clear cached data root (unit tests only)."""
    global _data_root_override, _used_user_fallback
    _data_root_override = None
    _used_user_fallback = False
