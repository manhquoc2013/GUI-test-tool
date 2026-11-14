"""Copilot history rail + store nonempty/prune helpers."""

from __future__ import annotations

from pathlib import Path

import pytest
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    COPILOT_HISTORY_BADGE_RUNNING,
    COPILOT_HISTORY_POPOVER_MAX_HEIGHT,
    COPILOT_MODE_CASE,
)
from ai_qa_test_studio.core.chat_history import ChatHistoryStore
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.ui.widgets.chat_history_rail import ChatHistoryRail, _HistorySessionRow
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


@pytest.fixture
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app  # type: ignore[return-value]


def test_list_nonempty_and_prune_empties(tmp_path: Path) -> None:
    store = ChatHistoryStore(tmp_path)
    empty = CopilotSession(mode=COPILOT_MODE_CASE, title="Empty")
    filled = CopilotSession(mode=COPILOT_MODE_CASE, title="Filled")
    filled.messages.append(CopilotMessage(role="user", content="hi"))
    store.upsert(empty)
    store.upsert(filled)
    store.save()
    assert len(store.list_sessions()) == 2
    assert [s.title for s in store.list_nonempty()] == ["Filled"]
    removed = store.prune_empties()
    assert removed == 1
    assert len(store.list_sessions()) == 1


def test_history_rail_lists_and_activates(qapp: QApplication) -> None:
    del qapp
    rail = ChatHistoryRail()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="Alpha")
    a.messages.append(CopilotMessage(role="user", content="a"))
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="Beta")
    b.messages.append(CopilotMessage(role="user", content="b"))
    activated: list[str] = []
    deleted: list[str] = []
    rail.session_activated.connect(activated.append)
    rail.delete_requested.connect(deleted.append)
    rail.set_sessions([a, b], active_id=a.id, running_ids={b.id}, unread_ids=set())
    assert rail.list.count() == 2
    row_b = rail.list.itemWidget(rail.list.item(1))
    assert isinstance(row_b, _HistorySessionRow)
    assert COPILOT_HISTORY_BADGE_RUNNING in row_b.title.text()
    assert "Beta" in row_b.title.text()
    assert not row_b.btn_delete.isHidden()
    assert row_b.btn_delete.toolTip()
    row_b.activated.emit(b.id)
    assert activated == [b.id]
    row_b.delete_clicked.emit(b.id)
    assert deleted == [b.id]
    # Header no longer hosts a global delete control.
    assert not hasattr(rail, "btn_delete")
    rail.close()


def test_chat_pane_toggles_history_rail_and_restores_transcript(qapp: QApplication) -> None:
    from PySide6.QtCore import QPoint, Qt
    from PySide6.QtGui import QKeyEvent, QMouseEvent

    pane = CopilotChatPane()
    pane.resize(420, 640)
    pane.show()
    qapp.processEvents()
    assert pane.history_rail.isHidden()
    pane.toggle_history_rail()
    assert not pane.history_rail.isHidden()
    assert pane.btn_history.isChecked()
    # Compact popover under the history button — not a full-height side drawer.
    assert pane.history_rail.parent() is pane
    assert pane.history_rail.testAttribute(Qt.WidgetAttribute.WA_StyledBackground)
    assert pane.history_rail.height() < pane.height()
    assert pane.history_rail.height() <= COPILOT_HISTORY_POPOVER_MAX_HEIGHT
    assert pane._history_app_filter
    # Click outside closes the popover.
    outside = pane.mapToGlobal(QPoint(pane.width() - 4, pane.height() - 4))
    press = QMouseEvent(
        QMouseEvent.Type.MouseButtonPress,
        QPoint(0, 0),
        outside,
        Qt.MouseButton.LeftButton,
        Qt.MouseButton.LeftButton,
        Qt.KeyboardModifier.NoModifier,
    )
    pane.eventFilter(qapp, press)
    assert pane.history_rail.isHidden()
    assert not pane._history_app_filter

    pane.toggle_history_rail()
    assert not pane.history_rail.isHidden()
    escape = QKeyEvent(QKeyEvent.Type.KeyPress, Qt.Key.Key_Escape, Qt.KeyboardModifier.NoModifier)
    assert pane.eventFilter(qapp, escape) is True
    assert pane.history_rail.isHidden()

    pane.toggle_history_rail()
    session = CopilotSession(mode=COPILOT_MODE_CASE, title="Saved")
    session.messages.append(CopilotMessage(role="user", content="x"))
    pane.set_history_sessions([session], active_id=session.id)
    assert pane.history_rail.list.count() == 1
    row = pane.history_rail.list.itemWidget(pane.history_rail.list.item(0))
    assert isinstance(row, _HistorySessionRow)
    # Selecting a session closes the popover.
    row.activated.emit(session.id)
    assert pane.history_rail.isHidden()
    pane.load_session(session)
    assert pane.session.id == session.id
    assert len(pane.session.messages) == 1
    pane.close()


def test_history_popover_height_scales_with_rows(qapp: QApplication) -> None:
    del qapp
    rail = ChatHistoryRail()
    empty_h = rail.preferred_popover_height(max_height=400)
    sessions = []
    for idx in range(4):
        session = CopilotSession(mode=COPILOT_MODE_CASE, title=f"S{idx}")
        session.messages.append(CopilotMessage(role="user", content="x"))
        sessions.append(session)
    rail.set_sessions(sessions, active_id=sessions[0].id)
    filled_h = rail.preferred_popover_height(max_height=400)
    assert filled_h > empty_h
    assert filled_h <= COPILOT_HISTORY_POPOVER_MAX_HEIGHT
    rail.close()


def test_end_role_repaints_after_load_session_clears_streaming_row(qapp: QApplication) -> None:
    from ai_qa_test_studio.constants import ROLE_COMPOSER

    pane = CopilotChatPane()
    pane.begin_role(ROLE_COMPOSER)
    assert pane._streaming_row is not None
    live = pane.session
    # Simulate switch-away + switch-back while the turn is open.
    pane.load_session(live)
    assert pane._streaming_row is None
    pane.end_role(ROLE_COMPOSER, "full draft body", summary="short draft")
    assert pane._rows[-1]._text == "short draft"
    assert live.messages[-1].content == "full draft body"
    pane.close()
