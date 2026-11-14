"""Per-session Copilot run tracking (parallel turns + unread badges)."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field

from ai_qa_test_studio.constants import SUPERVISOR_KEY_COPILOT_PREFIX
from ai_qa_test_studio.core.chat_models import CopilotSession


@dataclass
class ChatRun:
    """Live state of the single in-flight turn of one conversation."""

    session: CopilotSession
    status: str = ""
    cancel: asyncio.Event = field(default_factory=asyncio.Event)

    @property
    def supervisor_key(self) -> str:
        return f"{SUPERVISOR_KEY_COPILOT_PREFIX}{self.session.id}"


class ChatRunRegistry:
    """Tracks the AI turn in flight for every conversation in the workspace.

    Runs are keyed by session id so a conversation can keep generating while the
    user reads another: the history rail badges it as running, and a finished
    result is marked unread until that conversation is opened.
    """

    def __init__(self) -> None:
        self._runs: dict[str, ChatRun] = {}
        self._unread: set[str] = set()
        self._cancel_requested: set[str] = set()

    @staticmethod
    def supervisor_key_for(session_id: str) -> str:
        return f"{SUPERVISOR_KEY_COPILOT_PREFIX}{session_id}"

    def start_run(self, session: CopilotSession, initial_status: str = "") -> ChatRun:
        session_id = session.id
        self._cancel_requested.discard(session_id)
        run = ChatRun(session=session, status=initial_status or "", cancel=asyncio.Event())
        self._runs[session_id] = run
        return run

    def request_cancel(self, session_id: str) -> None:
        if not session_id:
            return
        self._cancel_requested.add(session_id)
        run = self._runs.get(session_id)
        if run is not None:
            run.cancel.set()

    def is_cancel_requested(self, session_id: str) -> bool:
        return bool(session_id) and session_id in self._cancel_requested

    def cancel_event(self, session_id: str) -> asyncio.Event:
        run = self._runs.get(session_id)
        if run is not None:
            return run.cancel
        # Detached cancel handle for callers that cancel after finish.
        event = asyncio.Event()
        if session_id in self._cancel_requested:
            event.set()
        return event

    def get_run(self, session_id: str) -> ChatRun | None:
        return self._runs.get(session_id) if session_id else None

    def is_running(self, session_id: str) -> bool:
        return bool(session_id) and session_id in self._runs

    def get_live_session(self, session_id: str) -> CopilotSession | None:
        run = self.get_run(session_id)
        return run.session if run is not None else None

    def update_status(self, session_id: str, status: str) -> None:
        run = self.get_run(session_id)
        if run is None:
            return
        if status and status.strip():
            run.status = status

    def finish_run(self, session_id: str, viewed_session_id: str | None) -> None:
        if not session_id:
            return
        self._cancel_requested.discard(session_id)
        if self._runs.pop(session_id, None) is None:
            return
        if session_id == viewed_session_id:
            self._unread.discard(session_id)
        else:
            self._unread.add(session_id)

    def is_unread(self, session_id: str) -> bool:
        return bool(session_id) and session_id in self._unread

    def count_unread(self) -> int:
        return len(self._unread)

    def mark_read(self, session_id: str) -> None:
        if session_id:
            self._unread.discard(session_id)

    def count_background_runs(self, viewed_session_id: str | None) -> int:
        if viewed_session_id is None:
            return len(self._runs)
        return sum(1 for sid in self._runs if sid != viewed_session_id)

    def forget(self, session_id: str) -> None:
        if not session_id:
            return
        self._runs.pop(session_id, None)
        self._unread.discard(session_id)
        self._cancel_requested.discard(session_id)

    def running_session_ids(self) -> set[str]:
        return set(self._runs.keys())

    def cancel_all(self) -> None:
        """Ask every in-flight turn to stop (shutdown / app close)."""
        for session_id in list(self._runs.keys()):
            self.request_cancel(session_id)
