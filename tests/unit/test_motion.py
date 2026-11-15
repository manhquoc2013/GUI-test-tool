"""Unit tests for Fluent motion helpers — registry, reduce_motion, effect cleanup."""

from __future__ import annotations

import time
from collections.abc import Callable

import pytest

pytest.importorskip("PySide6")

from PySide6.QtWidgets import QApplication, QHBoxLayout, QLabel, QSplitter, QVBoxLayout, QWidget

from ai_qa_test_studio.constants import (
    UI_DRAWER_COLLAPSED_HEIGHT,
    UI_DRAWER_EXPANDED_HEIGHT,
    UI_MOTION_CHAT_MS,
    UI_WIDGET_SIZE_MAX,
)
from ai_qa_test_studio.ui.motion import (
    CHANNEL_WIDTH,
    animate_splitter_size,
    bind_motion_enabled,
    clear_registry_for_tests,
    fade_in,
    motion_enabled,
    registry_size,
    slide_height,
    stop_channel,
    unlock_height,
)
from ai_qa_test_studio.ui.qt_compat import opacity_effects_supported


def _require_compositing() -> None:
    if not opacity_effects_supported():
        pytest.skip("QGraphicsOpacityEffect unsafe on Linux headless/CI QPA")


@pytest.fixture
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


@pytest.fixture(autouse=True)
def _reset_motion(qapp: QApplication) -> None:
    clear_registry_for_tests()
    bind_motion_enabled(lambda: True)
    yield
    clear_registry_for_tests()
    bind_motion_enabled(lambda: True)


def _pump_until(qapp: QApplication, predicate: Callable[[], bool], *, timeout_s: float = 1.5) -> bool:
    end = time.monotonic() + timeout_s
    while time.monotonic() < end:
        qapp.processEvents()
        if predicate():
            return True
    return predicate()


def test_bind_motion_enabled_gate(qapp: QApplication) -> None:
    bind_motion_enabled(lambda: False)
    assert motion_enabled() is False
    bind_motion_enabled(lambda: True)
    assert motion_enabled() is True


def test_fade_in_clears_graphics_effect(qapp: QApplication) -> None:
    _require_compositing()
    host = QWidget()
    host.show()
    label = QLabel("hi", host)
    label.show()
    qapp.processEvents()
    done = {"ok": False}
    fade_in(label, duration_ms=40, on_done=lambda: done.__setitem__("ok", True))
    assert registry_size() == 1
    assert _pump_until(qapp, lambda: done["ok"])
    assert label.graphicsEffect() is None
    assert registry_size() == 0
    host.close()


def test_fade_in_headless_is_instant(qapp: QApplication, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AIQA_DISABLE_QT_EFFECTS", "1")
    host = QWidget()
    label = QLabel("x", host)
    host.show()
    label.show()
    qapp.processEvents()
    called = {"n": 0}
    fade_in(label, duration_ms=UI_MOTION_CHAT_MS, on_done=lambda: called.__setitem__("n", 1))
    qapp.processEvents()
    assert called["n"] == 1
    assert label.graphicsEffect() is None
    assert registry_size() == 0
    host.close()


def test_fade_in_reduce_motion_instant(qapp: QApplication) -> None:
    bind_motion_enabled(lambda: False)
    host = QWidget()
    label = QLabel("x", host)
    host.show()
    label.show()
    qapp.processEvents()
    called = {"n": 0}
    fade_in(label, duration_ms=UI_MOTION_CHAT_MS, on_done=lambda: called.__setitem__("n", 1))
    qapp.processEvents()
    assert called["n"] == 1
    assert label.graphicsEffect() is None
    assert registry_size() == 0
    host.close()


def test_race_second_fade_stops_first_on_done(qapp: QApplication) -> None:
    _require_compositing()
    host = QWidget()
    label = QLabel("race", host)
    host.show()
    label.show()
    qapp.processEvents()
    hits: list[str] = []
    fade_in(label, duration_ms=250, on_done=lambda: hits.append("first"))
    assert registry_size() == 1
    fade_in(label, duration_ms=40, on_done=lambda: hits.append("second"))
    assert registry_size() == 1
    assert _pump_until(qapp, lambda: "second" in hits)
    assert hits == ["second"]
    assert label.graphicsEffect() is None
    host.close()


def test_destroyed_clears_registry(qapp: QApplication) -> None:
    _require_compositing()
    host = QWidget()
    label = QLabel("bye", host)
    host.show()
    label.show()
    qapp.processEvents()
    fade_in(label, duration_ms=500)
    assert registry_size() == 1
    label.deleteLater()
    assert _pump_until(qapp, lambda: registry_size() == 0)
    host.close()


def test_slide_height_then_unlock(qapp: QApplication) -> None:
    host = QWidget()
    layout = QVBoxLayout(host)
    panel = QWidget(host)
    layout.addWidget(panel)
    host.resize(200, 300)
    host.show()
    qapp.processEvents()
    done = {"ok": False}

    def _done() -> None:
        unlock_height(panel, collapsed=False)
        done["ok"] = True

    slide_height(
        panel,
        UI_DRAWER_COLLAPSED_HEIGHT,
        UI_DRAWER_EXPANDED_HEIGHT,
        duration_ms=40,
        on_done=_done,
    )
    assert _pump_until(qapp, lambda: done["ok"])
    assert panel.minimumHeight() == 0
    assert panel.maximumHeight() == UI_WIDGET_SIZE_MAX
    host.close()


def test_slide_height_reduce_motion(qapp: QApplication) -> None:
    bind_motion_enabled(lambda: False)
    panel = QWidget()
    panel.show()
    qapp.processEvents()
    slide_height(panel, 10, 80, duration_ms=200)
    assert panel.minimumHeight() == 80
    assert panel.maximumHeight() == 80
    assert registry_size() == 0
    panel.close()


def test_animate_splitter_size_reduce_motion(qapp: QApplication) -> None:
    bind_motion_enabled(lambda: False)
    host = QWidget()
    layout = QHBoxLayout(host)
    splitter = QSplitter(host)
    left = QWidget()
    right = QWidget()
    left.setMinimumWidth(40)
    right.setMinimumWidth(0)
    splitter.addWidget(left)
    splitter.addWidget(right)
    layout.addWidget(splitter)
    host.resize(400, 200)
    host.show()
    qapp.processEvents()
    splitter.setSizes([280, 0])
    qapp.processEvents()
    animate_splitter_size(splitter, 1, 0, 120, host=right, duration_ms=200)
    qapp.processEvents()
    assert splitter.sizes()[1] >= 100
    assert registry_size() == 0
    host.close()


def test_animate_splitter_size_keeps_side_pane_clamped(qapp: QApplication) -> None:
    """Dock width changes must not inflate a max-capped side pane (dead gap)."""
    bind_motion_enabled(lambda: False)
    host = QWidget()
    layout = QHBoxLayout(host)
    splitter = QSplitter(host)
    explorer = QWidget()
    editor = QWidget()
    dock = QWidget()
    explorer.setMinimumWidth(100)
    explorer.setMaximumWidth(200)
    editor.setMinimumWidth(200)
    dock.setMinimumWidth(0)
    dock.setMaximumWidth(300)
    splitter.addWidget(explorer)
    splitter.addWidget(editor)
    splitter.addWidget(dock)
    layout.addWidget(splitter)
    host.resize(900, 200)
    host.show()
    qapp.processEvents()
    splitter.setSizes([180, 720, 0])
    qapp.processEvents()
    animate_splitter_size(splitter, 2, 0, 250, host=dock, duration_ms=200)
    qapp.processEvents()
    sizes = splitter.sizes()
    assert sizes[0] <= 200
    assert sizes[2] >= 200
    assert sizes[1] >= 200
    host.close()


def test_stop_channel_prevents_splitter_on_done(qapp: QApplication) -> None:
    """Sidebar snap-close must cancel dock open on_done (no resurrect)."""
    host = QWidget()
    layout = QHBoxLayout(host)
    splitter = QSplitter(host)
    left = QWidget()
    right = QWidget()
    left.setMinimumWidth(40)
    right.setMinimumWidth(0)
    splitter.addWidget(left)
    splitter.addWidget(right)
    layout.addWidget(splitter)
    host.resize(400, 200)
    host.show()
    qapp.processEvents()
    splitter.setSizes([280, 0])
    qapp.processEvents()
    done = {"n": 0}
    animate_splitter_size(
        splitter,
        1,
        0,
        120,
        host=right,
        duration_ms=400,
        on_done=lambda: done.__setitem__("n", done["n"] + 1),
    )
    assert registry_size() == 1
    stop_channel(right, CHANNEL_WIDTH)
    assert registry_size() == 0
    qapp.processEvents()
    time.sleep(0.05)
    qapp.processEvents()
    assert done["n"] == 0
    host.close()
