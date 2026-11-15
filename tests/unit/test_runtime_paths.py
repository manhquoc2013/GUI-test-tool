"""Tests for frozen-aware runtime_paths (no Playwright import)."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

from ai_qa_test_studio.runtime_paths import (
    app_dir,
    browsers_dir,
    data_root,
    is_frozen,
    reset_path_cache_for_tests,
)


@pytest.fixture(autouse=True)
def _reset_paths() -> None:
    reset_path_cache_for_tests()
    yield
    reset_path_cache_for_tests()


def test_runtime_paths_source_has_no_playwright_import() -> None:
    import ai_qa_test_studio.runtime_paths as rp

    source = Path(rp.__file__).read_text(encoding="utf-8")
    assert "import playwright" not in source
    assert "from playwright" not in source


def test_import_runtime_paths_does_not_add_playwright_module() -> None:
    """Isolated subprocess: importing runtime_paths must not load playwright."""
    import os
    import subprocess

    repo_root = Path(__file__).resolve().parents[2]
    script = (
        "import sys\n"
        "import ai_qa_test_studio.runtime_paths\n"
        "bad = [n for n in sys.modules if n == 'playwright' or n.startswith('playwright.')]\n"
        "raise SystemExit(1 if bad else 0)\n"
    )
    env = os.environ.copy()
    env["PYTHONPATH"] = str(repo_root / "src")
    env["PYTHONIOENCODING"] = "utf-8"
    proc = subprocess.run(
        [sys.executable, "-c", script],
        check=False,
        capture_output=True,
        text=True,
        cwd=str(repo_root),
        env=env,
    )
    assert proc.returncode == 0, proc.stderr


def test_dev_data_root_writable(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("ai_qa_test_studio.runtime_paths.is_frozen", lambda: False)
    monkeypatch.chdir(tmp_path)
    root = data_root()
    assert root == (tmp_path / "data").resolve()
    assert root.is_dir()
    assert browsers_dir().parent == root


def test_user_data_fallback_on_permission(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr("ai_qa_test_studio.runtime_paths.is_frozen", lambda: True)
    blocked = tmp_path / "blocked_app"
    blocked.mkdir()
    monkeypatch.setattr("ai_qa_test_studio.runtime_paths.app_dir", lambda: blocked)

    def _fail_portable(candidate: Path) -> bool:
        return False

    monkeypatch.setattr(
        "ai_qa_test_studio.runtime_paths._try_create_writable", _fail_portable
    )
    fallback_base = tmp_path / "user_support"
    monkeypatch.setattr(
        "ai_qa_test_studio.runtime_paths.user_data_root",
        lambda: fallback_base,
    )
    root = data_root()
    assert root == fallback_base.resolve()
    from ai_qa_test_studio.runtime_paths import used_user_data_fallback

    assert used_user_data_fallback() is True


def test_macos_app_dir_parents(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("ai_qa_test_studio.runtime_paths.is_frozen", lambda: True)
    monkeypatch.setattr(sys, "platform", "darwin")
    fake = Path(
        "/Users/me/Apps/AIQATestStudio.app/Contents/MacOS/AIQATestStudio"
    )
    monkeypatch.setattr(sys, "executable", str(fake))
    result = app_dir()
    # Structural assert — avoid drive-letter / Path(*parts) equality traps on Windows runners
    assert result.name == "Apps"
    assert not any(part.endswith(".app") for part in result.parts)


def test_is_frozen_default() -> None:
    assert is_frozen() is False
