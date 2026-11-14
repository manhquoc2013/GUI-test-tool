"""v4.2 three-column shell — splitter dock, breakpoints, mutual exclusion."""

from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    COPILOT_STARTER_PROMPTS,
    UI_BREAKPOINT_MEDIUM,
    UI_BREAKPOINT_WIDE,
    UI_DRAWER_COLLAPSED_HEIGHT,
    UI_EDITOR_MIN_WIDTH,
    UI_EXPLORER_MIN_WIDTH,
    UI_THREE_COL_SAFE,
    UI_WINDOW_DEFAULT_HEIGHT,
    UI_WINDOW_DEFAULT_WIDTH,
    UI_WINDOW_MIN_HEIGHT,
    UI_WINDOW_MIN_WIDTH,
)
from ai_qa_test_studio.core.models import Project, TestCase, TestGroup, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.ui.main_window import MainWindow
from ai_qa_test_studio.ui.widgets.badges import ElidedTitleLabel
from ai_qa_test_studio.ui.widgets.data_drawer import TestDataDrawer


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
            name="Shell Smoke",
            root_group=TestGroup(
                id="g1",
                name="Root",
                scenarios=[TestScenario(id="s1", code="TS1", title="Sc", cases=[case])],
            ),
        )
    )


def test_three_column_defaults_wide(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()

    assert window.minimumWidth() == UI_WINDOW_MIN_WIDTH
    assert window.minimumHeight() == UI_WINDOW_MIN_HEIGHT
    assert window.h_splitter.count() == 3
    assert window.right_drawer.is_open is True
    assert window.right_drawer.isVisible()
    assert window.explorer.isVisible()
    assert not window.copilot.starters.isHidden()
    assert len(COPILOT_STARTER_PROMPTS) == 4
    window.close()


def test_laptop_band_hides_explorer_when_dock_opens(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    mid = (UI_BREAKPOINT_MEDIUM + UI_BREAKPOINT_WIDE) // 2
    window.resize(mid, 720)
    qapp.processEvents()
    window.right_drawer.close_drawer()
    window._sidebar_collapsed = False
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()
    assert window.explorer.isVisible()

    window.top_bar.btn_copilot.setChecked(True)
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()
    assert window.right_drawer.is_open is True
    assert window.explorer.isHidden()
    window.close()


def test_mutual_exclusion_under_three_col_safe(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    width = UI_THREE_COL_SAFE - 20
    window.resize(width, 720)
    qapp.processEvents()
    window.right_drawer.open_drawer()
    window._prefer_dock = True
    window._sidebar_collapsed = True
    window.apply_layout_breakpoint(window.width())
    qapp.processEvents()
    assert window.right_drawer.isVisible()
    assert window.explorer.isHidden()

    window._layout.toggle_sidebar()
    qapp.processEvents()
    assert window.explorer.isVisible()
    assert window.right_drawer.is_open is False or window.right_drawer.isHidden()
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


def test_editor_title_elides_long_names(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
    qapp.processEvents()
    long_title = "TC99_ChucNangTaoHoSoNguoiYeuThe_LuongChinh_HappyPath_Extra"
    assert isinstance(window.builder.title_label, ElidedTitleLabel)
    window.builder.title_label.setText(long_title)
    window.builder.resize(420, 400)
    qapp.processEvents()
    assert window.builder.title_label.toolTip() == long_title
    assert len(window.builder.title_label.text()) <= len(long_title)
    window.close()


def test_data_drawer_toggled_emits_and_clamps_height(qapp: QApplication) -> None:
    drawer = TestDataDrawer()
    seen: list[bool] = []
    drawer.toggled.connect(lambda expanded: seen.append(expanded))
    drawer.toggle_collapse()
    assert seen == [False]
    assert drawer.collapsed is True
    assert drawer.minimumHeight() == UI_DRAWER_COLLAPSED_HEIGHT
    assert drawer.maximumHeight() == UI_DRAWER_COLLAPSED_HEIGHT
    drawer.toggle_collapse()
    assert seen == [False, True]
    assert drawer.collapsed is False


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


def test_run_button_is_text_only_primary(qapp: QApplication, tmp_path: Path) -> None:
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


def test_editor_min_width_floor(qapp: QApplication, tmp_path: Path) -> None:
    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    assert window.builder.minimumSizeHint().width() == UI_EDITOR_MIN_WIDTH
    assert window.explorer.minimumWidth() == UI_EXPLORER_MIN_WIDTH
    window.close()


def test_wizard_source_uses_scroll_area(qapp: QApplication, tmp_path: Path) -> None:
    from qfluentwidgets import SmoothScrollArea

    _seed(tmp_path)
    window = MainWindow(data_root=tmp_path)
    window.show()
    wizard = window.copilot.wizard
    scrolls = wizard.source_page.findChildren(SmoothScrollArea)
    assert len(scrolls) >= 1
    assert scrolls[0].widgetResizable() is True
    window.close()
