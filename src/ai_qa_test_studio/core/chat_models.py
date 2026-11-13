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
    content: str = ""
    status: str = MSG_STATUS_COMPLETE
    apply_payload: dict[str, Any] | None = None
    created_at: str = Field(default_factory=_now_iso)


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
        text = self.messages[last_user_idx].content
        self.messages = self.messages[:last_user_idx]
        self.touch()
        return text

    def llm_window(self, max_turns: int = COPILOT_SLIDING_TURNS) -> list[CopilotMessage]:
        """Return up to max_turns recent user/assistant-role pairs (message count capped)."""
        if max_turns <= 0:
            return []
        return self.messages[-max_turns:]
