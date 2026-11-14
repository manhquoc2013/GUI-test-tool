from __future__ import annotations

from ai_qa_test_studio.constants import (
    COPILOT_CHAT_PROSE_MAX_CHARS,
    COPILOT_CHAT_REPLY_MAX_CHARS,
)
from ai_qa_test_studio.ui.controllers.copilot_controller import clip_chat_reply


def test_clip_chat_reply_keeps_short_text() -> None:
    assert clip_chat_reply("Planner: 3 bước") == "Planner: 3 bước"


def test_clip_chat_reply_keeps_chat_paragraphs() -> None:
    raw = (
        "Thời tiết hôm nay:\n"
        "\n"
        "- Nắng nhẹ buổi sáng\n"
        "- Chiều có thể có mưa\n"
        "\n"
        "Mang áo mưa nếu ra ngoài lâu."
    )
    out = clip_chat_reply(raw, max_chars=COPILOT_CHAT_REPLY_MAX_CHARS)
    assert "Thời tiết hôm nay:" in out
    assert out.count("\n") >= 3
    assert "- Nắng nhẹ buổi sáng" in out
    assert "Mang áo mưa" in out


def test_clip_chat_reply_still_compacts_coverage() -> None:
    long = (
        "Coverage xong 20/20 cửa sổ · 59/59 chunks → 66 scenario\n"
        "- S01 ok\n"
        "- S02 ok\n"
    )
    out = clip_chat_reply(long, max_chars=120)
    assert "\n" not in out or "S01" not in out
    assert "66 scenario" in out or "Coverage" in out
    assert len(out) <= 120


def test_clip_chat_reply_prose_cap() -> None:
    long = ("Đoạn giải thích dài. " * 200) + "\n\n" + ("Ý tiếp theo. " * 200)
    out = clip_chat_reply(long)
    assert len(out) <= COPILOT_CHAT_PROSE_MAX_CHARS + 1
    assert "\n" in out or len(out) < len(long)
