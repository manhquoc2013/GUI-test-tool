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


def test_llm_window_trims_oldest_by_max_chars() -> None:
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    session.messages.extend(
        [
            CopilotMessage(role="user", content="AAAA"),
            CopilotMessage(role=ROLE_COMPOSER, content="BBBB"),
            CopilotMessage(role="user", content="CCCC"),
            CopilotMessage(role=ROLE_COMPOSER, content="DD"),
        ]
    )
    window = session.llm_window(max_turns=10, max_chars=6)
    assert [m.content for m in window] == ["CCCC", "DD"]
    assert sum(len(m.content) for m in window) <= 6


def test_llm_window_keeps_latest_when_over_budget() -> None:
    session = CopilotSession(mode=COPILOT_MODE_CASE)
    session.messages.append(CopilotMessage(role="user", content="X" * 20))
    window = session.llm_window(max_turns=5, max_chars=5)
    assert len(window) == 1
    assert window[0].content == "X" * 20


def test_copilot_controller_passes_lean_tail_as_max_chars() -> None:
    import inspect

    from ai_qa_test_studio.ui.controllers.copilot_controller import CopilotController

    src = inspect.getsource(CopilotController.on_prompt)
    assert "lean_tail_max_tokens" in src
    assert "max_chars=" in src


def test_message_bubble_text_prefers_summary_keeps_full_content() -> None:
    msg = CopilotMessage(
        role=ROLE_COMPOSER,
        content="FULL draft with 18 scenarios and detailed steps…",
        summary="Draft 18 scenario — Tải Excel / Apply.",
    )
    assert msg.bubble_text() == "Draft 18 scenario — Tải Excel / Apply."
    assert "18 scenarios" in msg.content


def test_persist_summary_and_full_content(tmp_path: Path) -> None:
    store = ChatHistoryStore(tmp_path)
    session = CopilotSession(mode=COPILOT_MODE_CASE, title="Hybrid")
    session.messages.append(
        CopilotMessage(
            role=ROLE_COMPOSER,
            content="FULL BODY " + ("x" * 200),
            summary="short summary",
        )
    )
    store.upsert(session)
    store.save()
    loaded = ChatHistoryStore(tmp_path).list_sessions()[0]
    assert loaded.messages[0].summary == "short summary"
    assert loaded.messages[0].content.startswith("FULL BODY")
    assert loaded.messages[0].bubble_text() == "short summary"
