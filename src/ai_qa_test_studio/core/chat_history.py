"""Persist Copilot chat sessions under data_root/copilot/."""

from __future__ import annotations

import json
import logging
from pathlib import Path

from ai_qa_test_studio.constants import COPILOT_DIRNAME, COPILOT_SESSIONS_FILENAME
from ai_qa_test_studio.core.chat_models import CopilotSession

logger = logging.getLogger(__name__)


class ChatHistoryStore:
    def __init__(self, data_root: Path) -> None:
        self.root = data_root / COPILOT_DIRNAME
        self.root.mkdir(parents=True, exist_ok=True)
        self.path = self.root / COPILOT_SESSIONS_FILENAME
        self._sessions: dict[str, CopilotSession] = {}
        self.load()

    def load(self) -> None:
        self._sessions.clear()
        if not self.path.is_file():
            return
        try:
            payload = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("copilot_sessions_load_failed path=%s err=%s", self.path, exc)
            return
        for item in payload.get("sessions") or []:
            try:
                session = CopilotSession.model_validate(item)
                self._sessions[session.id] = session
            except Exception as exc:  # noqa: BLE001
                logger.warning("copilot_session_skip err=%s", exc)

    def save(self) -> None:
        payload = {
            "sessions": [s.model_dump(mode="json") for s in self._sessions.values()],
        }
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(self.path)

    def upsert(self, session: CopilotSession) -> None:
        session.touch()
        self._sessions[session.id] = session

    def delete(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)

    def get(self, session_id: str) -> CopilotSession | None:
        return self._sessions.get(session_id)

    def list_sessions(self) -> list[CopilotSession]:
        return sorted(self._sessions.values(), key=lambda s: s.updated_at, reverse=True)
