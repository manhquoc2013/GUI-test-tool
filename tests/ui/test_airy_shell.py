"""Airy v4.1 shell smoke — 2-pane, drawer closed, chat starters, breakpoints."""

from __future__ import annotations

import inspect
from pathlib import Path

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    COPILOT_STARTER_PROMPTS,
    UI_BREAKPOINT_MEDIUM,
    UI_BREAKPOINT_WIDE,
    UI_EDITOR_MIN_WIDTH,
    UI_EXPLORER_MIN_WIDTH,
    UI_SIDEBAR_WIDTH_COMPACT,
    UI_WINDOW_DEFAULT_HEIGHT,
    UI_WINDOW_DEFAULT_WIDTH,
    UI_WINDOW_MIN_HEIGHT,
    UI_WINDOW_MIN_WIDTH,
)
from ai_qa_test_studio.core.models import Project, TestCase, TestGroup, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.ui.main_window import MainWindow
from ai_qa_test_studio.ui.widgets.browser_agent_pane import BrowserAgentPane
from ai_qa_test_studio.ui.widgets.compose_wizard_panel import ComposeWizardPanel
from ai_qa_test_studio.ui.widgets.embedded_browser import ScreencastViewport


@pytest.fixture(scope="session")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def _seed(tmp_path: Path) -> None:
    storage = ProjectStorage(tmp_path / "v4")
    case = TestCase(
        id="c1",
        code="TC1",
        title="Sample",
        steps=[
            TestStep(
                step_no=1,
                action="goto",
                intent_hint="open",
                input_data="/",
                binding_status="UNBOUND",
            )
        ],
    )
    storage.save(
        Project(
            id="p1",
            name="Airy Smoke",
            root_group=TestGroup(
                id="g1",
                name="Root",
                scenarios=[TestScenario(id="s1", code="TS1", title="Sc", cases=[case])],
            ),
        )
    )


def test_airy_defaults_and_drawer(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    assert window.minimumWidth() == UI_WINDOW_MIN_WIDTH
    assert window.minimumHeight() == UI_WINDOW_MIN_HEIGHT
    assert window.width() >= UI_WINDOW_DEFAULT_WIDTH - 40
    assert window.height() >= UI_WINDOW_DEFAULT_HEIGHT - 80
    assert window.right_drawer.is_open is False
    assert window.right_drawer.isHidden()
    assert not window.copilot.starters.isHidden()
    assert len(COPILOT_STARTER_PROMPTS) == 4

    window.top_bar.btn_copilot.setChecked(True)
    assert window.right_drawer.is_open is True
    assert window.copilot.starters.isVisible()
    window.top_bar.btn_copilot.setChecked(False)
    assert window.right_drawer.is_open is False

    window.resize(UI_BREAKPOINT_MEDIUM - 20, 640)
    window.apply_layout_breakpoint(window.width())
    assert window._overlay_mode is True
    window.close()


def test_chat_undo_from_message(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.right_drawer.open_drawer()
    window._sync_drawer_geometry()
    pane = window.copilot
    pane.append_user("hello")
    assert pane.starters.isHidden()
    pane.begin_role("composer")
    pane.end_role("composer", "world")
    assert len(pane.session.messages) == 2
    user_id = pane.session.messages[0].id
    pane._on_undo_row(user_id)
    assert pane.session.messages == []
    assert pane.input_box.toPlainText() == "hello"
    assert not pane.starters.isHidden()
    window.close()


def test_settings_signal_emits_without_arity_error(qapp: QApplication) -> None:
    """clicked/triggered must not pass checked=bool into Signal() with 0 args."""
    from qfluentwidgets import Action, RoundMenu
    from qfluentwidgets import FluentIcon as FIF

    from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane
    from ai_qa_test_studio.ui.widgets.top_toolbar import UnifiedTopBar

    hits: list[str] = []
    bar = UnifiedTopBar()
    bar.settings_requested.connect(lambda: hits.append("bar"))
    menu = RoundMenu(parent=bar)
    action = Action(
        FIF.SETTING,
        "System Settings",
        triggered=lambda: bar.settings_requested.emit(),
    )
    menu.addAction(action)
    action.trigger()

    pane = CopilotChatPane()
    pane.settings_requested.connect(lambda: hits.append("pane"))
    pane.btn_settings.click()
    assert hits == ["bar", "pane"]


def test_run_button_is_text_only_primary(qapp: QApplication, tmp_path: Path) -> None:
    """Run CTA has no Fluent icon — avoids icon/label paint overlap."""
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    btn = window.top_bar.btn_run
    assert btn.icon().isNull()
    window.top_bar.set_running(True)
    assert btn.text() == "Dừng"
    window.top_bar.set_running(False)
    assert btn.text() == "Chạy"
    window.close()


def test_top_bar_density_compacts_labels(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.top_bar.apply_density(UI_BREAKPOINT_WIDE)
    assert window.top_bar.btn_wizard.text() == "Tạo bằng AI"
    window.top_bar.apply_density(UI_BREAKPOINT_MEDIUM)
    assert window.top_bar.btn_wizard.text() == "Tạo AI"
    window.top_bar.apply_density(UI_BREAKPOINT_MEDIUM - 40)
    assert window.top_bar.btn_wizard.text() == ""
    assert window.top_bar.btn_copilot.text() == ""
    window.close()


def test_sidebar_and_steps_table_readable(qapp: QApplication, tmp_path: Path) -> None:
    """Sidebar fits 3 explorer tabs; Steps table keeps Intent/Data/Binding on-screen."""
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()

    assert window.explorer.isVisible()
    assert window.explorer.width() >= UI_EXPLORER_MIN_WIDTH
    assert window.h_splitter.sizes()[0] >= UI_SIDEBAR_WIDTH_COMPACT - 1

    table = window.builder.table
    assert table.columnCount() == 5
    visible_cols = sum(1 for c in range(5) if table.columnWidth(c) > 0)
    assert visible_cols == 5
    assert table.columnWidth(2) > 0  # Intent stretches
    assert table.columnWidth(3) > 0  # Data stretches
    window.close()


def test_sidebar_splitter_rejects_collapse(qapp: QApplication, tmp_path: Path) -> None:
    """Dragging the H-splitter must not squash explorer below EXPLORER_MIN."""
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()

    assert window.h_splitter.childrenCollapsible() is False
    total = sum(window.h_splitter.sizes()) or window.workspace.width()
    window.h_splitter.setSizes([40, max(1, total - 40)])
    qapp.processEvents()
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()

    assert window.explorer.isVisible()
    assert window.explorer.width() >= UI_EXPLORER_MIN_WIDTH
    assert window.h_splitter.sizes()[0] >= UI_EXPLORER_MIN_WIDTH
    window.close()


def test_sidebar_drag_can_grow_and_layout_preserves_size(qapp: QApplication, tmp_path: Path) -> None:
    """Editor minSizeHint is capped so setSizes can widen the sidebar."""
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()

    assert window.builder.minimumSizeHint().width() == UI_EDITOR_MIN_WIDTH
    total = sum(window.h_splitter.sizes()) or window.workspace.width()
    target = min(360, window.explorer.maximumWidth())
    window.h_splitter.setSizes([target, max(UI_EDITOR_MIN_WIDTH, total - target)])
    qapp.processEvents()
    assert window.h_splitter.sizes()[0] >= target - 5
    window._sync_drawer_geometry()
    qapp.processEvents()
    assert window.h_splitter.sizes()[0] >= target - 5
    window.close()


def test_editor_title_elides_long_names(qapp: QApplication, tmp_path: Path) -> None:
    """Long case titles stay on one line with tooltip holding the full string."""
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    long_title = "TC99_ChucNangTaoHoSoNguoiYeuThe_LuongChinh_HappyPath_Extra"
    window.builder._set_title(long_title)
    window.builder.resize(420, 400)
    qapp.processEvents()
    window.builder._elide_title()
    assert window.builder.title_label.toolTip() == long_title
    assert len(window.builder.title_label.text()) <= len(long_title)
    assert "…" in window.builder.title_label.text() or window.builder.title_label.text() == long_title
    window.close()


def test_right_drawer_push_vs_overlay(qapp: QApplication, tmp_path: Path) -> None:
    """≥960 push (reserve width); <960 overlay (splitter full width)."""
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    window.top_bar.btn_copilot.setChecked(True)
    window.apply_layout_breakpoint(window.width())
    window._sync_drawer_geometry()
    qapp.processEvents()

    drawer = window.right_drawer
    ws = window.workspace
    assert drawer.is_open is True
    assert drawer.isVisible()
    assert window._overlay_mode is False
    assert window.h_splitter.width() < ws.width()
    assert drawer.x() + drawer.width() == ws.width()
    assert drawer.height() == ws.height()
    assert drawer.width() > 0

    for width, expect_overlay in (
        (UI_BREAKPOINT_WIDE + 40, False),
        (UI_BREAKPOINT_MEDIUM + 10, False),
        (UI_BREAKPOINT_MEDIUM - 40, True),
        (900, True),
    ):
        window.resize(width, 700)
        qapp.processEvents()
        window.apply_layout_breakpoint(window.width())
        window._sync_drawer_geometry()
        qapp.processEvents()
        assert drawer.is_open is True
        assert drawer.isVisible()
        assert drawer.width() > 0
        assert drawer.height() == ws.height()
        assert drawer.x() + drawer.width() == ws.width()
        assert window._overlay_mode is expect_overlay
        if expect_overlay:
            assert window.h_splitter.geometry().width() == ws.width()
        else:
            assert window.h_splitter.geometry().width() < ws.width()

    window.close()


def test_require_case_and_sync_url_avoid_blocking_dialogs() -> None:
    """@asyncSlot paths must not call QMessageBox / QInputDialog (qasync clash)."""
    require_src = inspect.getsource(MainWindow._require_case)
    sync_src = inspect.getsource(MainWindow._sync_url)
    assert "QMessageBox" not in require_src
    assert "QInputDialog" not in sync_src
    assert "_toast_warn" in require_src or "_toast_error" in require_src
    assert "DEFAULT_TARGET_URL" in sync_src


def test_close_event_schedules_session_stop() -> None:
    src = inspect.getsource(MainWindow.closeEvent)
    assert "request_cancel" in src
    assert "ensure_future" in src or "create_task" in src
    assert "session.stop" in src
    assert "_copilot_cancel" in src or "_stop_copilot" in src


def test_escape_stops_copilot_before_browser(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.copilot.set_busy(True)
    window._copilot_cancel = __import__("asyncio").Event()
    window._busy = True
    stopped: list[str] = []
    window._stop_copilot = lambda: stopped.append("copilot")  # type: ignore[method-assign]
    window._stop_browser = lambda: stopped.append("browser")  # type: ignore[method-assign]
    window._on_escape()
    assert stopped == ["copilot"]
    window.copilot.set_busy(False)
    window._on_escape()
    assert stopped == ["copilot", "browser"]
    window.close()


def test_build_copilot_context_skips_dom_when_browser_busy(
    qapp: QApplication, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window._busy = True
    called = {"dom": False}

    async def _fake_extract(_page: object) -> tuple[str, object]:
        called["dom"] = True
        return "DOM", object()

    monkeypatch.setattr(window._dom_distiller, "extract", _fake_extract)
    window.session._started = True  # type: ignore[attr-defined]

    import asyncio

    ctx = asyncio.run(
        window._build_copilot_context(
            {"include_case": False, "include_dom": True, "include_last_error": False}
        )
    )
    assert called["dom"] is False
    assert "dom" not in ctx
    window.close()


def test_signal_emit_wrappers_avoid_arity_error(qapp: QApplication) -> None:
    """clicked(bool) must not connect directly to Signal() with 0 args."""
    hits: list[str] = []
    browser = BrowserAgentPane()
    browser.launch_browser_clicked.connect(lambda: hits.append("browser"))
    browser.btn_launch.click()

    embedded = ScreencastViewport()
    embedded.launch_requested.connect(lambda: hits.append("embedded"))
    embedded.btn_launch.click()
    embedded.empty_cta.click()

    wizard = ComposeWizardPanel()
    wizard.cancelled.connect(lambda: hits.append("wizard"))
    wizard.btn_cancel.click()
    assert hits == ["browser", "embedded", "embedded", "wizard"]
