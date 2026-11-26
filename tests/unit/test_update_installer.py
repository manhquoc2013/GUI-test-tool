"""Installer unit tests: zip hardening, payload detection, helper scripts."""

from __future__ import annotations

import zipfile
from pathlib import Path

import pytest

from ai_qa_test_studio.constants import APP_DATA_DIRNAME, APP_EXE_NAME, APP_SUPPORT_DIRNAME
from ai_qa_test_studio.core.update_installer import (
    InstallError,
    build_posix_helper_script,
    build_windows_helper_script,
    payload_root,
    safe_extract_zip,
)


def _write_zip(path: Path, entries: dict[str, bytes]) -> Path:
    with zipfile.ZipFile(path, "w") as bundle:
        for name, payload in entries.items():
            bundle.writestr(name, payload)
    return path


def test_safe_extract_nested_bundle(tmp_path: Path) -> None:
    archive = _write_zip(
        tmp_path / "update.zip",
        {f"{APP_SUPPORT_DIRNAME}/{APP_EXE_NAME}": b"binary", f"{APP_SUPPORT_DIRNAME}/lib.so": b"x"},
    )
    root = safe_extract_zip(archive, tmp_path / "out")
    assert root.name == APP_SUPPORT_DIRNAME
    assert (root / APP_EXE_NAME).read_bytes() == b"binary"


def test_safe_extract_flat_archive(tmp_path: Path) -> None:
    archive = _write_zip(tmp_path / "flat.zip", {APP_EXE_NAME: b"bin", "lib/x.so": b"x"})
    root = safe_extract_zip(archive, tmp_path / "out")
    assert (root / APP_EXE_NAME).read_bytes() == b"bin"


def test_safe_extract_rejects_path_traversal(tmp_path: Path) -> None:
    archive = _write_zip(tmp_path / "evil.zip", {"../escape.txt": b"pwned"})
    with pytest.raises(InstallError):
        safe_extract_zip(archive, tmp_path / "out")
    assert not (tmp_path / "escape.txt").exists()


def test_safe_extract_rejects_absolute_path(tmp_path: Path) -> None:
    archive = _write_zip(tmp_path / "abs.zip", {"/etc/passwd": b"pwned"})
    with pytest.raises(InstallError):
        safe_extract_zip(archive, tmp_path / "out")


def test_payload_root_prefers_app_named_dir(tmp_path: Path) -> None:
    root = tmp_path / "out"
    (root / APP_SUPPORT_DIRNAME).mkdir(parents=True)
    (root / "README").write_text("x", encoding="utf-8")
    assert payload_root(root).name == APP_SUPPORT_DIRNAME


def test_windows_helper_waits_for_pid_and_preserves_data() -> None:
    script = build_windows_helper_script(
        staging_payload=Path(r"C:\stage\bundle"),
        install_target=Path(r"C:\Program Files\AIQATestStudio"),
        exe_name=f"{APP_EXE_NAME}.exe",
        pid=4242,
        log_path=Path(r"C:\stage\update.log"),
    )
    assert '"PID eq 4242"' in script
    assert "robocopy" in script and "/MIR" in script
    assert APP_DATA_DIRNAME in script  # excluded from the mirror
    assert f'start "" "C:\\Program Files\\AIQATestStudio\\{APP_EXE_NAME}.exe"' in script


def test_posix_helper_waits_and_relaunches() -> None:
    script = build_posix_helper_script(
        staging_payload=Path("/tmp/stage/bundle"),
        install_target=Path("/opt/ai-qa-test-studio"),
        exe_name=APP_EXE_NAME,
        pid=99,
        log_path=Path("/tmp/stage/update.log"),
    )
    assert "while kill -0 99" in script
    assert "rsync -a --delete" in script
    assert f"/opt/ai-qa-test-studio/{APP_EXE_NAME}" in script
