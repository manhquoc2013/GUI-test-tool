"""Route Copilot turn events to the live session, updating UI only when viewed."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from ai_qa_test_studio.constants import (
    COPILOT_STOP_MARKER,
    COPILOT_STOP_SUFFIX,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CREW,
    ROLE_CRITIC,
    ROLE_PLANNER,
    ROLE_SYSTEM,
)
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.ui.bubble_summary import summarize_for_bubble

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class CopilotTurnSink:
    """Writes into one live ``CopilotSession``; paints the pane only while it is viewed."""

    def __init__(self, window: MainWindow, session: CopilotSession) -> None:
        self._w = window
        self.session = session
        self.session_id = session.id

    def viewing(self) -> bool:
        return self._w.copilot.session.id == self.session_id

    def set_busy(self, busy: bool) -> None:
        if self.viewing():
            self._w.copilot.set_busy(busy)

    def set_status(self, text: str) -> None:
        self._w._chat_runs.update_status(self.session_id, text)
        if self.viewing():
            self._w.copilot.set_status(text)

    def set_streaming_status(self, text: str, *, role: str | None = None) -> None:
        self._w._chat_runs.update_status(self.session_id, text)
        if self.viewing():
            self._w.copilot.set_streaming_status(text, role=role)

    def set_apply_payload(self, payload: dict[str, Any] | None) -> None:
        if self.viewing():
            self._w.copilot.set_apply_payload(payload)

    def begin_role(self, role: str) -> None:
        if self.viewing():
            self._w.copilot.begin_role(role)
            return
        self.session.messages.append(CopilotMessage(role=role, content=""))

    def preview_role(self, role: str, summary: str) -> None:
        if self.viewing():
            self._w.copilot.preview_role(role, summary)

    def end_role(self, role: str, text: str, *, summary: str = "") -> None:
        if self.viewing():
            self._w.copilot.end_role(role, text, summary=summary)
            return
        display = summary or text
        if self.session.messages and self.session.messages[-1].role == role:
            self.session.messages[-1].content = text
            self.session.messages[-1].summary = display
        else:
            self.session.messages.append(
                CopilotMessage(role=role, content=text, summary=display)
            )

    def append_status_summary(self, role: str, text: str, *, summary: str = "") -> None:
        if self.viewing():
            self._w.copilot.append_status_summary(role, text, summary=summary)
            return
        self.session.messages.append(
            CopilotMessage(role=role, content=text, summary=summary or text)
        )

    def append_agent_message(
        self, from_role: str, to_role: str, text: str, *, summary: str = ""
    ) -> None:
        if self.viewing():
            self._w.copilot.append_agent_message(
                from_role, to_role, text, summary=summary
            )
            return
        body = f"{from_role} → {to_role}: {text}"
        display = (summary or "").strip() or summarize_for_bubble(
            text, from_role=from_role, to_role=to_role
        )
        self.session.messages.append(
            CopilotMessage(role=ROLE_CREW, content=body, summary=display)
        )

    def append_clarify_note(self, role: str, text: str) -> None:
        if self.viewing():
            self._w.copilot.append_clarify_note(role, text)
            return
        body = f"Clarify ({role}): {text}"
        self.session.messages.append(
            CopilotMessage(role=ROLE_SYSTEM, content=body, summary=body)
        )

    def mark_stopped(self, role: str | None = None) -> None:
        if self.viewing():
            self._w.copilot.mark_stopped(role)
            return
        for msg in self.session.messages:
            if msg.role == "user":
                continue
            content = msg.content or ""
            if COPILOT_STOP_MARKER in content:
                continue
            if not content.strip():
                msg.content = COPILOT_STOP_MARKER
                msg.summary = COPILOT_STOP_MARKER
                msg.status = "stopped"
        if self.session.messages:
            last = self.session.messages[-1]
            if last.role != "user" and COPILOT_STOP_MARKER not in (last.content or ""):
                last.content = (last.content or "") + COPILOT_STOP_SUFFIX
                if COPILOT_STOP_MARKER not in (last.summary or ""):
                    last.summary = (last.summary or "") + COPILOT_STOP_SUFFIX
                last.status = "stopped"

    def attach_apply_to_latest_crew(self, payload: dict[str, Any]) -> None:
        crew_roles = {ROLE_COMPOSER, ROLE_PLANNER, ROLE_CRITIC, ROLE_COORDINATOR}
        for msg in reversed(self.session.messages):
            if msg.role in crew_roles:
                msg.apply_payload = dict(payload)
                break
