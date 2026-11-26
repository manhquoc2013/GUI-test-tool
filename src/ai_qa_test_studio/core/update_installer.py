"""Stage a verified update and hand the swap to an out-of-process helper.

A running PyInstaller bundle cannot overwrite its own ``.exe``/``.so`` files, so
the installer writes a tiny wait-then-swap script, detaches it, and the app
exits. The helper waits for the app PID, mirrors the staged tree over the
install directory (never touching user ``data``), then relaunches the app.
"""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

from ai_qa_test_studio.constants import (
    APP_DATA_DIRNAME,
    APP_SUPPORT_DIRNAME,
    APP_UPDATE_HELPER_FILENAME,
    APP_UPDATE_LOG_FILENAME,
    APP_UPDATE_PROCESS_POLL_S,
    APP_UPDATE_STAGING_DIRNAME,
)
from ai_qa_test_studio.runtime_paths import app_dir, is_frozen, user_data_root

logger = logging.getLogger(__name__)

_MAX_UNCOMPRESSED_BYTES = 4_294_967_296
_UPDATE_LOG_MAX_LINES = 400


class InstallError(RuntimeError):
    """Raised when the update cannot be staged or launched."""


def install_root() -> Path:
    """Directory holding the running bundle (onedir folder on Win/Linux)."""
    if not is_frozen():
        return app_dir()
    return Path(sys.executable).resolve().parent


def staging_root() -> Path:
    root = user_data_root() / APP_UPDATE_STAGING_DIRNAME
    root.mkdir(parents=True, exist_ok=True)
    return root


def staging_dir(version: str) -> Path:
    safe = "".join(ch for ch in version if ch.isalnum() or ch in ".-_") or "latest"
    target = staging_root() / safe
    target.mkdir(parents=True, exist_ok=True)
    return target


def is_install_writable() -> bool:
    """Probe the install directory; ``Program Files`` needs elevation we never request."""
    probe = install_root() / f".aiqa-write-probe-{os.getpid()}"
    try:
        probe.write_text("ok", encoding="utf-8")
    except OSError as exc:
        logger.info("install_root_not_writable path=%s err=%s", install_root(), exc)
        return False
    finally:
        probe.unlink(missing_ok=True)
    return True


def safe_extract_zip(archive: Path, destination: Path) -> Path:
    """Extract ``archive`` refusing absolute paths, ``..`` escapes, and symlinks."""
    destination.mkdir(parents=True, exist_ok=True)
    root = destination.resolve()
    total = 0
    with zipfile.ZipFile(archive) as bundle:
        for info in bundle.infolist():
            name = info.filename.replace("\\", "/")
            if name.startswith("/") or name.startswith("../") or "/../" in name:
                raise InstallError(f"unsafe archive entry: {info.filename}")
            mode = (info.external_attr >> 16) & 0o170000
            if mode == 0o120000:
                raise InstallError(f"symlink entries are not allowed: {info.filename}")
            total += max(info.file_size, 0)
            if total > _MAX_UNCOMPRESSED_BYTES:
                raise InstallError("archive expands beyond the size limit")
            target = (root / name).resolve()
            if root != target and root not in target.parents:
                raise InstallError(f"archive entry escapes destination: {info.filename}")
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(info) as source, target.open("wb") as sink:
                shutil.copyfileobj(source, sink, length=1024 * 1024)
    return payload_root(destination)


def payload_root(extracted: Path) -> Path:
    """Portable archives nest the bundle as ``<AppName>/`` (or ``<AppName>.app``)."""
    entries = list(extracted.iterdir())
    directories = [item for item in entries if item.is_dir()]
    preferred = [item for item in directories if item.name in {APP_SUPPORT_DIRNAME, f"{APP_SUPPORT_DIRNAME}.app"}]
    if len(preferred) == 1:
        return preferred[0]
    if len(entries) == 1 and len(directories) == 1:
        return directories[0]
    return extracted


def _windows_path(path: Path | str) -> str:
    """Render a path the way ``cmd`` expects, regardless of the host running the tests."""
    return str(path).replace("/", "\\").replace('"', "")


def build_windows_helper_script(
    *,
    staging_payload: Path,
    install_target: Path,
    exe_name: str,
    pid: int,
    log_path: Path,
) -> str:
    """Windows ``cmd`` script: wait for PID, mirror the tree, relaunch the app."""
    source = _windows_path(staging_payload)
    target = _windows_path(install_target).rstrip("\\")
    exe = f"{target}\\{exe_name}"
    user_data = f"{target}\\{APP_DATA_DIRNAME}"
    log = _windows_path(log_path)
    return "\n".join(
        [
            "@echo off",
            "setlocal",
            f'echo [%DATE% %TIME%] helper start pid={pid} >> "{log}"',
            ":wait",
            f'tasklist /FI "PID eq {pid}" | find "{pid}" >nul 2>&1',
            "if not errorlevel 1 (",
            f"  timeout /t {APP_UPDATE_PROCESS_POLL_S} /nobreak >nul",
            "  goto wait",
            ")",
            f'echo [%DATE% %TIME%] app exited, swapping >> "{log}"',
            f'robocopy "{source}" "{target}" /MIR /XD "{user_data}" /R:3 /W:2 /NFL /NDL /NJH /NJS /NP >> "{log}" 2>&1',
            "if errorlevel 8 (",
            f'  echo [%DATE% %TIME%] robocopy failed >> "{log}"',
            "  exit /b 1",
            ")",
            f'start "" "{exe}"',
            f'echo [%DATE% %TIME%] relaunched >> "{log}"',
            "endlocal",
            "",
        ]
    )


def build_posix_helper_script(
    *,
    staging_payload: Path,
    install_target: Path,
    exe_name: str,
    pid: int,
    log_path: Path,
) -> str:
    """POSIX ``sh`` script: wait for PID, rsync the tree, relaunch the app."""
    return "\n".join(
        [
            "#!/bin/sh",
            "set -eu",
            f'log="{log_path}"',
            f'echo "$(date -u) helper start pid={pid}" >> "$log"',
            f"while kill -0 {pid} 2>/dev/null; do sleep {APP_UPDATE_PROCESS_POLL_S}; done",
            'echo "$(date -u) app exited, swapping" >> "$log"',
            f'rsync -a --delete --exclude "{APP_DATA_DIRNAME}/" '
            f'"{staging_payload}/" "{install_target}/" >> "$log" 2>&1',
            f'nohup "{install_target / exe_name}" >/dev/null 2>&1 &',
            'echo "$(date -u) relaunched" >> "$log"',
            "",
        ]
    )


def _detached_flags() -> int:
    if sys.platform.startswith("win"):
        return int(getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)) | int(getattr(subprocess, "DETACHED_PROCESS", 0))
    return 0


def launch_swap_helper(
    *,
    staging_payload: Path,
    version: str,
    exe_name: str,
    pid: int | None = None,
) -> Path:
    """Write and detach the swap helper. Caller must quit the app right after."""
    target = install_root()
    work = staging_dir(version)
    log_path = work / APP_UPDATE_LOG_FILENAME
    current_pid = os.getpid() if pid is None else pid

    if sys.platform.startswith("win"):
        script = work / APP_UPDATE_HELPER_FILENAME
        script.write_text(
            build_windows_helper_script(
                staging_payload=staging_payload,
                install_target=target,
                exe_name=exe_name,
                pid=current_pid,
                log_path=log_path,
            ),
            encoding="utf-8",
            newline="\r\n",
        )
        subprocess.Popen(  # noqa: S603 — fixed argv, own generated script
            ["cmd", "/c", str(script)],
            creationflags=_detached_flags(),
            close_fds=True,
            cwd=str(work),
        )
        return script

    script = work / APP_UPDATE_HELPER_FILENAME.replace(".cmd", ".sh")
    script.write_text(
        build_posix_helper_script(
            staging_payload=staging_payload,
            install_target=target,
            exe_name=exe_name,
            pid=current_pid,
            log_path=log_path,
        ),
        encoding="utf-8",
    )
    script.chmod(0o700)
    subprocess.Popen(  # noqa: S603 — fixed argv, own generated script
        ["/bin/sh", str(script)],
        start_new_session=True,
        close_fds=True,
        cwd=str(work),
    )
    return script


def read_helper_log(version: str) -> str:
    """Tail the helper log so the next launch can explain a failed swap."""
    safe = "".join(ch for ch in version if ch.isalnum() or ch in ".-_") or "latest"
    path = user_data_root() / APP_UPDATE_STAGING_DIRNAME / safe / APP_UPDATE_LOG_FILENAME
    if not path.is_file():
        return ""
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return ""
    return "\n".join(lines[-_UPDATE_LOG_MAX_LINES:])
