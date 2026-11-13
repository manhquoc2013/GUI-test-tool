"""Airy v4.1 shell smoke — 2-pane, drawer closed, chat starters, breakpoints."""

from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    COPILOT_STARTER_PROMPTS,
    UI_BREAKPOINT_MEDIUM,
    UI_WINDOW_DEFAULT_HEIGHT,
    UI_WINDOW_DEFAULT_WIDTH,
    UI_WINDOW_MIN_HEIGHT,
    UI_WINDOW_MIN_WIDTH,
)
from ai_qa_test_studio.core.models import Project, TestCase, TestGroup, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.ui.main_window import MainWindow


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
