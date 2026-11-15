"""Headless UI motion / smooth-scroll / loading-state contracts (offscreen Qt)."""

from __future__ import annotations

import os
import time
from collections.abc import Callable

import pytest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

pytest.importorskip("PySide6")

from PySide6.QtWidgets import (  # noqa: E402
    QAbstractItemView,
    QApplication,
    QLabel,
    QListWidget,
    QTableWidget,
    QTreeWidget,
    QWidget,
)

from ai_qa_test_studio.constants import (  # noqa: E402
    UI_MOTION_CHAT_MS,
    UI_MOTION_FAST_MS,
    UI_MOTION_NORMAL_MS,
)
from ai_qa_test_studio.ui.motion import (  # noqa: E402
    bind_motion_enabled,
    clear_registry_for_tests,
    fade_in,
    fade_in_slide,
    motion_enabled,
    registry_size,
)
from ai_qa_test_studio.ui.qt_compat import opacity_effects_supported  # noqa: E402
from ai_qa_test_studio.ui.smooth_scroll import (  # noqa: E402
    SmartAutoScroller,
    apply_pixel_scroll,
    apply_scroll_area_smooth,
    make_jump_button,
    uses_smooth_scroll_delegate,
)


def _require_compositing() -> None:
    if not opacity_effects_supported():
        pytest.skip("QGraphicsOpacityEffect unsafe on Linux headless/CI QPA")


@pytest.fixture(scope="module")
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


def _pump_until(qapp: QApplication, predicate: Callable[[], bool], *, timeout_s: float = 2.0) -> bool:
    end = time.monotonic() + timeout_s
    while time.monotonic() < end:
        qapp.processEvents()
        if predicate():
            return True
    return predicate()


def test_motion_timing_constants_in_fluent_range() -> None:
    assert 120 <= UI_MOTION_FAST_MS <= 150
    assert 160 <= UI_MOTION_NORMAL_MS <= 220
    assert 120 <= UI_MOTION_CHAT_MS <= 220


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


def test_fade_in_slide_clears_effect_and_reduce_motion(qapp: QApplication) -> None:
    _require_compositing()
    host = QWidget()
    host.show()
    label = QLabel("slide", host)
    label.show()
    qapp.processEvents()
    done = {"ok": False}
    fade_in_slide(label, duration_ms=40, on_done=lambda: done.__setitem__("ok", True))
    assert _pump_until(qapp, lambda: done["ok"])
    assert label.graphicsEffect() is None

    bind_motion_enabled(lambda: False)
    called = {"n": 0}
    fade_in_slide(label, duration_ms=200, on_done=lambda: called.__setitem__("n", 1))
    qapp.processEvents()
    assert called["n"] == 1
    assert label.graphicsEffect() is None
    assert motion_enabled() is False
    host.close()


def test_delete_later_clears_registry_safely(qapp: QApplication) -> None:
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


def test_item_views_use_scroll_per_pixel(qapp: QApplication) -> None:
    import sys

    tree = QTreeWidget()
    table = QTableWidget(2, 2)
    listing = QListWidget()
    for view in (tree, table, listing):
        delegate = apply_pixel_scroll(view)
        assert view.verticalScrollMode() == QAbstractItemView.ScrollMode.ScrollPerPixel
        assert view.horizontalScrollMode() == QAbstractItemView.ScrollMode.ScrollPerPixel
        if sys.platform == "darwin":
            assert delegate is None
            assert uses_smooth_scroll_delegate() is False
        else:
            assert delegate is not None
            assert uses_smooth_scroll_delegate() is True
    tree.close()
    table.close()
    listing.close()


def test_scroll_area_smooth_skipped_on_darwin(qapp: QApplication) -> None:
    import sys

    from PySide6.QtWidgets import QScrollArea

    area = QScrollArea()
    delegate = apply_scroll_area_smooth(area)
    if sys.platform == "darwin":
        assert delegate is None
        assert uses_smooth_scroll_delegate() is False
    else:
        assert delegate is not None
    area.close()


def test_smart_auto_scroll_pauses_when_user_scrolls_up(qapp: QApplication) -> None:
    host = QWidget()
    listing = QListWidget(host)
    listing.setFixedSize(240, 120)
    for i in range(80):
        listing.addItem(f"line-{i}")
    jump = make_jump_button(host)
    scroller = SmartAutoScroller(listing, jump_button=jump)
    host.resize(260, 200)
    host.show()
    listing.show()
    qapp.processEvents()
    scroller.scroll_to_bottom()
    qapp.processEvents()
    assert scroller.following is True
    assert jump.isHidden()

    bar = listing.verticalScrollBar()
    assert bar.maximum() > 0
    bar.setValue(0)
    qapp.processEvents()
    assert scroller.following is False
    assert jump.isVisible()

    jump.click()
    qapp.processEvents()
    assert scroller.following is True
    assert jump.isHidden()
    host.close()


def test_main_window_smoke_tabs_dock_drawer(qapp: QApplication, tmp_path) -> None:
    from ai_qa_test_studio.ui.main_window import MainWindow

    bind_motion_enabled(lambda: False)
    window = MainWindow(data_root=tmp_path)
    window.show()
    qapp.processEvents()

    # Explorer / editor / chat list modes are ScrollPerPixel.
    assert (
        window.explorer.suite_tree.verticalScrollMode()
        == QAbstractItemView.ScrollMode.ScrollPerPixel
    )
    assert (
        window.builder.table.verticalScrollMode()
        == QAbstractItemView.ScrollMode.ScrollPerPixel
    )
    assert hasattr(window.copilot, "thinking_ring")
    assert hasattr(window.copilot, "_auto_scroll")
    assert hasattr(window._run_logs, "_auto_scroll")

    # Toggle dock + sidebar without crash.
    window._layout.toggle_copilot_drawer()
    qapp.processEvents()
    window._layout.toggle_sidebar()
    qapp.processEvents()
    window.builder.mode_switch.setCurrentItem("yaml")
    qapp.processEvents()
    window.builder.mode_switch.setCurrentItem("nocode")
    qapp.processEvents()
    window.data_drawer.toggle_collapse()
    qapp.processEvents()

    # Loading state hooks exist.
    window.set_mcp_warmup(True)
    qapp.processEvents()
    assert window._mcp_warmup_ring.isVisible()
    window.set_mcp_warmup(False)
    qapp.processEvents()

    window.builder.set_step_run_status(1, "Running")
    qapp.processEvents()
    window.builder.clear_step_run_status()
    qapp.processEvents()

    window.close()
    qapp.processEvents()
    assert True


def test_editor_and_explorer_have_popup_stacks(qapp: QApplication) -> None:
    from qfluentwidgets import PopUpAniStackedWidget

    from ai_qa_test_studio.ui.widgets.editor_pane import EditorPane
    from ai_qa_test_studio.ui.widgets.explorer_pane import ExplorerPane

    explorer = ExplorerPane()
    editor = EditorPane()
    assert isinstance(explorer.stack, PopUpAniStackedWidget)
    assert isinstance(editor.stack, PopUpAniStackedWidget)
    explorer.close()
    editor.close()
