"""ChatRunRegistry — parallel Copilot turns keyed by session id."""

from __future__ import annotations

import asyncio

from ai_qa_test_studio.constants import COPILOT_MODE_CASE
from ai_qa_test_studio.core.chat_models import CopilotSession
from ai_qa_test_studio.core.chat_run_registry import ChatRunRegistry


def test_start_run_tracks_live_session_and_status() -> None:
    registry = ChatRunRegistry()
    session = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    run = registry.start_run(session, "working")
    assert run is not None
    assert registry.is_running(session.id)
    assert registry.get_live_session(session.id) is session
    assert run.status == "working"
    registry.update_status(session.id, "planner…")
    assert registry.get_run(session.id).status == "planner…"


def test_request_cancel_is_per_session() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="B")
    registry.start_run(a, "a")
    registry.start_run(b, "b")
    registry.request_cancel(a.id)
    assert registry.is_cancel_requested(a.id)
    assert not registry.is_cancel_requested(b.id)
    assert registry.cancel_event(a.id).is_set()
    assert not registry.cancel_event(b.id).is_set()


def test_finish_run_marks_unread_when_not_viewed() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="B")
    registry.start_run(a, "a")
    registry.finish_run(a.id, viewed_session_id=b.id)
    assert not registry.is_running(a.id)
    assert registry.is_unread(a.id)
    registry.mark_read(a.id)
    assert not registry.is_unread(a.id)


def test_finish_run_clears_unread_when_viewed() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    registry.start_run(a, "a")
    registry.finish_run(a.id, viewed_session_id=a.id)
    assert not registry.is_unread(a.id)


def test_count_background_runs_excludes_viewed() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    b = CopilotSession(mode=COPILOT_MODE_CASE, title="B")
    registry.start_run(a, "a")
    registry.start_run(b, "b")
    assert registry.count_background_runs(a.id) == 1
    assert registry.count_background_runs(None) == 2


def test_forget_drops_run_unread_and_cancel() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    registry.start_run(a, "a")
    registry.request_cancel(a.id)
    registry.finish_run(a.id, viewed_session_id="other")
    assert registry.is_unread(a.id)
    registry.start_run(a, "again")
    registry.forget(a.id)
    assert not registry.is_running(a.id)
    assert not registry.is_unread(a.id)
    assert not registry.is_cancel_requested(a.id)


def test_start_run_resets_prior_cancel_flag() -> None:
    registry = ChatRunRegistry()
    a = CopilotSession(mode=COPILOT_MODE_CASE, title="A")
    registry.start_run(a, "a")
    registry.request_cancel(a.id)
    registry.finish_run(a.id, viewed_session_id=a.id)
    registry.start_run(a, "again")
    assert not registry.is_cancel_requested(a.id)
    assert isinstance(registry.cancel_event(a.id), asyncio.Event)
    assert not registry.cancel_event(a.id).is_set()
