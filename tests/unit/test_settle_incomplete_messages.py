"""Blank agent bubbles must not resurrect as typing after reload."""

from __future__ import annotations

from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    COPILOT_INCOMPLETE_MARKER,
    COPILOT_MODE_CASE,
    COPILOT_STOP_MARKER,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
)
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


def test_session_settle_incomplete_stamps_blank_agents() -> None:
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    session.messages.append(CopilotMessage(role="user", content="hi"))
    session.messages.append(CopilotMessage(role=ROLE_COORDINATOR, content=""))
    session.messages.append(CopilotMessage(role=ROLE_COMPOSER, content=""))
    assert session.settle_incomplete_messages() is True
    assert session.messages[1].content == COPILOT_INCOMPLETE_MARKER
    assert session.messages[2].summary == COPILOT_INCOMPLETE_MARKER
    assert session.settle_incomplete_messages() is False


def test_load_session_settles_zombie_typing(qapp: QApplication) -> None:
    del qapp
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    session.messages.append(CopilotMessage(role="user", content="tạo kịch bản"))
    session.messages.append(CopilotMessage(role=ROLE_COORDINATOR, content=""))
    session.messages.append(CopilotMessage(role=ROLE_COMPOSER, content=""))

    pane = CopilotChatPane()
    settled = pane.load_session(session, settle_incomplete=True)
    assert settled is True
    assert len(pane._rows) == 3
    assert not pane._rows[1].is_typing()
    assert not pane._rows[2].is_typing()
    assert pane._rows[1]._text == COPILOT_INCOMPLETE_MARKER
    assert pane._rows[2]._text == COPILOT_INCOMPLETE_MARKER
    pane.close()


def test_load_session_keeps_live_typing_when_not_settling(qapp: QApplication) -> None:
    del qapp
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    session.messages.append(CopilotMessage(role=ROLE_COMPOSER, content=""))
    pane = CopilotChatPane()
    settled = pane.load_session(session, settle_incomplete=False)
    assert settled is False
    assert pane._rows[0].is_typing()
    pane.close()


def test_mark_stopped_persists_summary(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    pane.mark_stopped()
    assert pane.session.messages[0].summary == COPILOT_STOP_MARKER
    assert not pane._rows[0].is_typing()
    pane.close()
