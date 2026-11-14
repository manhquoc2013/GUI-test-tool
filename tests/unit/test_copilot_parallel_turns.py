"""Parallel Copilot turns: background session keeps messages without hijacking the viewed pane."""

from __future__ import annotations

from ai_qa_test_studio.constants import COPILOT_MODE_CASE, ROLE_COMPOSER
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.core.chat_run_registry import ChatRunRegistry
from ai_qa_test_studio.ui.controllers.copilot_turn_sink import CopilotTurnSink


class _FakePane:
    def __init__(self, session: CopilotSession) -> None:
        self.session = session
        self._busy = False
        self.roles: list[str] = []
        self.ended: list[tuple[str, str]] = []
        self.statuses: list[str] = []

    @property
    def is_busy(self) -> bool:
        return self._busy

    def set_busy(self, busy: bool) -> None:
        self._busy = busy

    def set_status(self, text: str) -> None:
        self.statuses.append(text)

    def begin_role(self, role: str) -> None:
        self.roles.append(role)
        self.session.messages.append(CopilotMessage(role=role, content=""))

    def end_role(self, role: str, text: str, *, summary: str = "") -> None:
        self.ended.append((role, text))
        if self.session.messages and self.session.messages[-1].role == role:
            self.session.messages[-1].content = text
            self.session.messages[-1].summary = summary or text

    def preview_role(self, role: str, summary: str) -> None:
        del role, summary

    def append_status_summary(self, role: str, text: str, *, summary: str = "") -> None:
        self.session.messages.append(
            CopilotMessage(role=role, content=text, summary=summary or text)
        )

    def append_agent_message(
        self, from_role: str, to_role: str, text: str, *, summary: str = ""
    ) -> None:
        body = f"{from_role} → {to_role}: {text}"
        self.session.messages.append(
            CopilotMessage(role="crew", content=body, summary=summary or body)
        )

    def append_clarify_note(self, role: str, text: str) -> None:
        body = f"Clarify ({role}): {text}"
        self.session.messages.append(CopilotMessage(role="system", content=body, summary=body))

    def set_apply_payload(self, payload: dict | None) -> None:
        del payload

    def mark_stopped(self, role: str | None = None) -> None:
        del role
        self._busy = False


class _FakeWindow:
    def __init__(self) -> None:
        self._chat_runs = ChatRunRegistry()
        a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
        a.messages.append(CopilotMessage(role="user", content="hi-a"))
        self.copilot = _FakePane(a)


def test_turn_sink_writes_background_session_without_touching_viewed_ui() -> None:
    w = _FakeWindow()
    background = CopilotSession(mode=COPILOT_MODE_CASE, title="B")
    background.messages.append(CopilotMessage(role="user", content="hi-b"))
    w._chat_runs.start_run(background, "working")
    sink = CopilotTurnSink(w, background)  # type: ignore[arg-type]
    assert not sink.viewing()
    sink.begin_role(ROLE_COMPOSER)
    sink.end_role(ROLE_COMPOSER, "draft-b", summary="short-b")
    assert w.copilot.roles == []
    assert w.copilot.ended == []
    assert background.messages[-1].content == "draft-b"
    assert background.messages[-1].summary == "short-b"


def test_turn_sink_forwards_to_pane_when_viewing() -> None:
    w = _FakeWindow()
    target = w.copilot.session
    w._chat_runs.start_run(target, "working")
    sink = CopilotTurnSink(w, target)  # type: ignore[arg-type]
    assert sink.viewing()
    sink.begin_role(ROLE_COMPOSER)
    sink.end_role(ROLE_COMPOSER, "draft-a", summary="short-a")
    assert w.copilot.roles == [ROLE_COMPOSER]
    assert w.copilot.ended == [(ROLE_COMPOSER, "draft-a")]


def test_request_cancel_viewed_does_not_cancel_background() -> None:
    """Stop isolation: cancelling B must not set A's cancel Event."""
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="B")
    run_a = registry.start_run(a, "a")
    run_b = registry.start_run(b, "b")
    registry.request_cancel(b.id)
    assert run_b.cancel.is_set()
    assert not run_a.cancel.is_set()
    assert registry.is_cancel_requested(b.id)
    assert not registry.is_cancel_requested(a.id)


def test_cancel_all_running_sessions() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="B")
    run_a = registry.start_run(a, "a")
    run_b = registry.start_run(b, "b")
    registry.cancel_all()
    assert run_a.cancel.is_set()
    assert run_b.cancel.is_set()
