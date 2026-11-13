from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.constants import COPILOT_MODE_CASE, ROLE_COMPOSER, ROLE_PLANNER
from ai_qa_test_studio.core.chat_history import ChatHistoryStore
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession


def test_persist_and_reload_sessions(tmp_path: Path) -> None:
    store = ChatHistoryStore(tmp_path)
    session = CopilotSession(mode=COPILOT_MODE_CASE, title="Demo")
    session.messages.append(CopilotMessage(role="user", content="Xin chào"))
    store.upsert(session)
    store.save()

    loaded = ChatHistoryStore(tmp_path)
    assert len(loaded.list_sessions()) == 1
    assert loaded.list_sessions()[0].messages[0].content == "Xin chào"


def test_undo_user_turn_removes_following_roles() -> None:
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    session.messages.extend(
        [
            CopilotMessage(role="user", content="A"),
            CopilotMessage(role=ROLE_PLANNER, content="plan"),
            CopilotMessage(role=ROLE_COMPOSER, content="draft"),
            CopilotMessage(role="user", content="B"),
            CopilotMessage(role=ROLE_PLANNER, content="plan2"),
        ]
    )
    undone = session.undo_last_user_turn()
    assert undone == "B"
    assert [m.content for m in session.messages] == ["A", "plan", "draft"]


def test_sliding_window_limits_llm_turns() -> None:
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    for i in range(12):
        session.messages.append(CopilotMessage(role="user", content=f"u{i}"))
        session.messages.append(CopilotMessage(role=ROLE_COMPOSER, content=f"a{i}"))
    window = session.llm_window(max_turns=4)
    assert len(window) == 4
    assert window[0].content == "u10"
