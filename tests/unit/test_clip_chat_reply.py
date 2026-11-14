from __future__ import annotations

from ai_qa_test_studio.constants import COPILOT_CHAT_REPLY_MAX_CHARS
from ai_qa_test_studio.ui.controllers.copilot_controller import clip_chat_reply


def test_clip_chat_reply_keeps_short_text() -> None:
    assert clip_chat_reply("Planner: 3 bước") == "Planner: 3 bước"


def test_clip_chat_reply_truncates_long_agent_dump() -> None:
    long = "Analyzer: " + ("rủi ro locator stale. " * 40)
    out = clip_chat_reply(long, max_chars=120)
    assert len(out) <= 120
    assert out.endswith("…")


def test_clip_chat_reply_default_cap() -> None:
    long = "x" * (COPILOT_CHAT_REPLY_MAX_CHARS + 50)
    assert len(clip_chat_reply(long)) <= COPILOT_CHAT_REPLY_MAX_CHARS
