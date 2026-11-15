"""Lazy Chromium install via Playwright driver CLI (frozen-safe, singleflight)."""

from __future__ import annotations

import asyncio
import logging
import os
import re
import shutil
import subprocess
import sys
import threading
from collections.abc import Callable
from pathlib import Path

from ai_qa_test_studio.constants import (
    CHROMIUM_BINARY_GLOBS,
    CHROMIUM_INSTALL_TIMEOUT_SEC,
    CHROMIUM_PROGRESS_PERCENT_RE,
    PLAYWRIGHT_BROWSERS_PATH_ENV,
)
from ai_qa_test_studio.runtime_paths import browsers_dir

logger = logging.getLogger(__name__)

ProgressCb = Callable[[int | None, str], None]

_PERCENT_RE = re.compile(CHROMIUM_PROGRESS_PERCENT_RE)
_lock: asyncio.Lock | None = None
_install_task: asyncio.Task[None] | None = None
_active_proc: subprocess.Popen[str] | None = None
_cancel_flag = threading.Event()


def _get_lock() -> asyncio.Lock:
    global _lock
    if _lock is None:
        _lock = asyncio.Lock()
    return _lock


def chromium_ready(root: Path | None = None) -> bool:
    """Fast path check — pathlib only (no sync_playwright / no Node spawn)."""
    base = root if root is not None else browsers_dir()
    if not base.is_dir():
        return False
    for pattern in CHROMIUM_BINARY_GLOBS:
        for candidate in base.glob(pattern):
            if not candidate.is_file():
                continue
            if sys.platform.startswith("win"):
                return True
            if os.access(candidate, os.X_OK):
                return True
    return False


def _purge_chromium_dirs(base: Path) -> None:
    if not base.is_dir():
        return
    for child in base.iterdir():
        name = child.name.lower()
        if child.is_dir() and ("chromium" in name or "ffmpeg" in name):
            try:
                shutil.rmtree(child, ignore_errors=False)
            except OSError as exc:
                logger.warning("chromium_purge_failed path=%s err=%s", child, exc)
                shutil.rmtree(child, ignore_errors=True)


def _parse_percent(line: str) -> int | None:
    match = _PERCENT_RE.search(line)
    if match is None:
        return None
    return max(0, min(100, int(match.group(1))))


def _terminate_proc(proc: subprocess.Popen[str]) -> None:
    if proc.poll() is not None:
        return
    proc.terminate()
    try:
        proc.wait(timeout=15)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=30)


def _install_chromium_sync(*, on_progress: ProgressCb | None = None) -> None:
    """Run driver ``install chromium`` with merged stdout; raise on failure/cancel."""
    global _active_proc
    from playwright._impl._driver import compute_driver_executable, get_driver_env

    browsers = browsers_dir()
    browsers.mkdir(parents=True, exist_ok=True)
    os.environ[PLAYWRIGHT_BROWSERS_PATH_ENV] = str(browsers)

    driver_executable, driver_cli = compute_driver_executable()
    env = get_driver_env()
    env[PLAYWRIGHT_BROWSERS_PATH_ENV] = str(browsers)

    cmd = [str(driver_executable), str(driver_cli), "install", "chromium"]
    logger.info("chromium_install_start cmd=%s browsers=%s", cmd, browsers)
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env=env,
        text=True,
        bufsize=1,
    )
    _active_proc = proc
    assert proc.stdout is not None
    code: int | None = None
    try:
        if _cancel_flag.is_set():
            _terminate_proc(proc)
            proc.wait(timeout=60)
            _purge_chromium_dirs(browsers)
            raise InterruptedError("chromium_install_cancelled")
        for line in proc.stdout:
            text = line.rstrip()
            if on_progress is not None:
                on_progress(_parse_percent(text), text)
            if _cancel_flag.is_set():
                _terminate_proc(proc)
                proc.wait(timeout=60)
                _purge_chromium_dirs(browsers)
                raise InterruptedError("chromium_install_cancelled")
        code = proc.wait(timeout=CHROMIUM_INSTALL_TIMEOUT_SEC)
    except subprocess.TimeoutExpired:
        _terminate_proc(proc)
        proc.wait(timeout=60)
        _purge_chromium_dirs(browsers)
        raise TimeoutError("chromium_install_timeout") from None
    finally:
        _active_proc = None

    if code is None or code != 0:
        _purge_chromium_dirs(browsers)
        raise RuntimeError(f"chromium_install_failed exit={code}")
    if not chromium_ready(browsers):
        _purge_chromium_dirs(browsers)
        raise RuntimeError("chromium_install_incomplete")


def request_cancel_install() -> None:
    """Cancel in-flight install (UI Cancel). Waits for process exit then purge is sync-side."""
    _cancel_flag.set()
    proc = _active_proc
    if proc is not None:
        _terminate_proc(proc)


async def ensure_chromium(*, on_progress: ProgressCb | None = None) -> None:
    """Install Chromium once (singleflight). No-op when already ready."""
    global _install_task
    if chromium_ready():
        if on_progress is not None:
            on_progress(100, "chromium_ready")
        return

    async with _get_lock():
        if chromium_ready():
            if on_progress is not None:
                on_progress(100, "chromium_ready")
            return
        if _install_task is None or _install_task.done():
            _cancel_flag.clear()

            async def _body() -> None:
                await asyncio.to_thread(_install_chromium_sync, on_progress=on_progress)

            _install_task = asyncio.create_task(_body())
        task = _install_task

    await task


def reset_bootstrap_state_for_tests() -> None:
    """Clear module install state (unit tests only)."""
    global _install_task, _active_proc, _lock
    _install_task = None
    _active_proc = None
    _lock = None
    _cancel_flag.clear()
