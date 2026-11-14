"""Copilot session / message models (persisted chat history)."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel, Field

from ai_qa_test_studio.constants import (
    COPILOT_MODE_CASE,
    COPILOT_SLIDING_TURNS,
    MSG_STATUS_COMPLETE,
)


def _now_iso() -> str:
    return datetime.now(UTC).isoformat()


class CopilotMessage(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    role: str
    # Full body for agent/LLM handoff (never clipped).
    content: str = ""
    # Short bubble text for the transcript UI; empty → UI may clip content.
    summary: str = ""
    status: str = MSG_STATUS_COMPLETE
    apply_payload: dict[str, Any] | None = None
    created_at: str = Field(default_factory=_now_iso)

    def bubble_text(self) -> str:
        """Prefer short summary for display; fall back to full content."""
        short = (self.summary or "").strip()
        return short if short else self.content


class CopilotSession(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    title: str = "Phiên mới"
    mode: str = COPILOT_MODE_CASE
    case_id: str | None = None
    created_at: str = Field(default_factory=_now_iso)
    updated_at: str = Field(default_factory=_now_iso)
    messages: list[CopilotMessage] = Field(default_factory=list)

    def touch(self) -> None:
        self.updated_at = _now_iso()

    def undo_last_user_turn(self) -> str | None:
        """Remove last user message and all following role outputs; return user text."""
        last_user_idx: int | None = None
        for idx in range(len(self.messages) - 1, -1, -1):
            if self.messages[idx].role == "user":
                last_user_idx = idx
                break
        if last_user_idx is None:
            return None
        return self.undo_from_index(last_user_idx)

    def undo_from_index(self, index: int) -> str | None:
        """Remove user message at index and everything after; return that user text."""
        if index < 0 or index >= len(self.messages):
            return None
        if self.messages[index].role != "user":
            return None
        text = self.messages[index].content
        self.messages = self.messages[:index]
        self.touch()
        return text

    def undo_from_message_id(self, message_id: str) -> str | None:
        for idx, msg in enumerate(self.messages):
            if msg.id == message_id and msg.role == "user":
                return self.undo_from_index(idx)
        return None

    def llm_window(
        self,
        max_turns: int = COPILOT_SLIDING_TURNS,
        max_chars: int | None = None,
    ) -> list[CopilotMessage]:
        """Return up to max_turns recent messages, optionally trimmed by content chars."""
        if max_turns <= 0:
            return []
        window = self.messages[-max_turns:]
        if max_chars is None:
            return window
        while len(window) > 1 and sum(len(m.content) for m in window) > max_chars:
            window = window[1:]
        return window
