"""Tests for chromium_bootstrap (mocked driver / no network)."""

from __future__ import annotations

import asyncio
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from ai_qa_test_studio.browser import chromium_bootstrap as cb


@pytest.fixture(autouse=True)
def _reset() -> None:
    cb.reset_bootstrap_state_for_tests()
    yield
    cb.reset_bootstrap_state_for_tests()


def test_chromium_ready_false_empty(tmp_path: Path) -> None:
    assert cb.chromium_ready(tmp_path) is False


def test_chromium_ready_true_when_binary(tmp_path: Path) -> None:
    chrome = tmp_path / "chromium-123" / "chrome-linux" / "chrome"
    chrome.parent.mkdir(parents=True)
    chrome.write_text("x", encoding="utf-8")
    chrome.chmod(0o755)
    assert cb.chromium_ready(tmp_path) is True


def test_parse_percent() -> None:
    assert cb._parse_percent("Downloading Chromium 45%") == 45
    assert cb._parse_percent("no percent") is None


@pytest.mark.asyncio
async def test_ensure_singleflight_one_popen(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(cb, "browsers_dir", lambda: tmp_path)
    monkeypatch.setattr(cb, "chromium_ready", lambda root=None: False)

    calls: list[int] = []

    def _fake_install(*, on_progress=None) -> None:  # noqa: ANN001
        calls.append(1)
        # Mark ready after first install
        chrome = tmp_path / "chromium-1" / "chrome"
        chrome.parent.mkdir(parents=True, exist_ok=True)
        chrome.write_text("x", encoding="utf-8")
        chrome.chmod(0o755)

    monkeypatch.setattr(cb, "_install_chromium_sync", _fake_install)

    def _ready(_root: Path | None = None) -> bool:
        return (tmp_path / "chromium-1" / "chrome").is_file()

    monkeypatch.setattr(cb, "chromium_ready", _ready)

    await asyncio.gather(cb.ensure_chromium(), cb.ensure_chromium())
    assert len(calls) == 1


@pytest.mark.asyncio
async def test_cancel_terminates_before_purge(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    order: list[str] = []

    proc = MagicMock()
    proc.poll.return_value = None
    proc.stdout = iter(["Downloading 10%\n"])
    proc.wait.return_value = 1

    def _terminate(p: MagicMock) -> None:
        order.append("terminate")
        p.poll.return_value = 0

    def _purge(_base: Path) -> None:
        order.append("purge")

    monkeypatch.setattr(cb, "browsers_dir", lambda: tmp_path)
    monkeypatch.setattr(cb, "_terminate_proc", _terminate)
    monkeypatch.setattr(cb, "_purge_chromium_dirs", _purge)
    monkeypatch.setattr(cb, "chromium_ready", lambda root=None: False)

    with (
        patch("playwright._impl._driver.compute_driver_executable", return_value=("node", "cli")),
        patch("playwright._impl._driver.get_driver_env", return_value={}),
        patch("subprocess.Popen", return_value=proc),
    ):
        cb._cancel_flag.set()
        with pytest.raises(InterruptedError):
            await asyncio.to_thread(cb._install_chromium_sync)

    assert order.index("terminate") < order.index("purge")
