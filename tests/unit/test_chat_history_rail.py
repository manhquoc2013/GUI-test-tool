"""Copilot history rail + store nonempty/prune helpers."""

from __future__ import annotations

from pathlib import Path

import pytest
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import COPILOT_HISTORY_BADGE_RUNNING, COPILOT_MODE_CASE
from ai_qa_test_studio.core.chat_history import ChatHistoryStore
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.ui.widgets.chat_history_rail import ChatHistoryRail
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
    rail = ChatHistoryRail()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="Alpha")
    a.messages.append(CopilotMessage(role="user", content="a"))
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="Beta")
    b.messages.append(CopilotMessage(role="user", content="b"))
    activated: list[str] = []
    rail.session_activated.connect(activated.append)
    rail.set_sessions([a, b], active_id=a.id, running_ids={b.id}, unread_ids=set())
    assert rail.list.count() == 2
    beta_text = rail.list.item(1).text()
    assert COPILOT_HISTORY_BADGE_RUNNING in beta_text
    assert "Beta" in beta_text
    rail._on_item_clicked(rail.list.item(1))
    assert activated == [b.id]
    rail.close()


def test_chat_pane_toggles_history_rail_and_restores_transcript(qapp: QApplication) -> None:
    pane = CopilotChatPane()
    assert pane.history_rail.isHidden()
    pane.toggle_history_rail()
    assert not pane.history_rail.isHidden()
    assert pane.btn_history.isChecked()
    session = CopilotSession(mode=COPILOT_MODE_CASE, title="Saved")
    session.messages.append(CopilotMessage(role="user", content="x"))
    pane.set_history_sessions([session], active_id=session.id)
    assert pane.history_rail.list.count() == 1
    pane.load_session(session)
    assert pane.session.id == session.id
    assert len(pane.session.messages) == 1
    pane.close()
