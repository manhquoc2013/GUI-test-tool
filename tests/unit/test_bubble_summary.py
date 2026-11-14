from __future__ import annotations

from ai_qa_test_studio.constants import (
    COPILOT_HANDOFF_HEADER,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
)
from ai_qa_test_studio.ui.bubble_summary import summarize_for_bubble
from ai_qa_test_studio.ui.controllers.copilot_controller import clip_chat_reply


def test_coverage_keeps_headline_drops_section_noise() -> None:
    raw = (
        "Coverage xong 20/20 cửa sổ · 59/59 chunks · 20 mục → 66 scenario / 165 case "
        "— Tải Excel hoặc Apply.\n"
        "- S01 w1/1 Mở đầu: ok\n"
        "- S02 w1/1 Tiếp · chunk 3: ok\n"
        "→ 66 scenario draft sẵn sàng — bấm Tải Excel hoặc Apply vào Editor."
    )
    out = summarize_for_bubble(raw, max_chars=180)
    assert "66 scenario" in out
    assert "S01" not in out
    assert "[HANDOFF]" not in out


def test_assign_handoff_without_result_is_short_dispatch() -> None:
    raw = (
        f"{COPILOT_HANDOFF_HEADER}\n"
        "From: Điều phối viên (coordinator)\n"
        "To: Chuyên gia phản biện (critic)\n"
        "Goal: Quality gate cho deliverable\n"
        "Acceptance: APPROVE có căn cứ hoặc REVISE cụ thể\n"
        "---\n"
        "Hãy review artifact theo SRS người dùng gửi."
    )
    out = summarize_for_bubble(
        raw,
        max_chars=160,
        from_role=ROLE_COORDINATOR,
        to_role=ROLE_CRITIC,
    )
    assert out == "→ Giao Chuyên gia phản biện"
    assert "Quality gate" not in out
    assert "Goal:" not in out


def test_report_handoff_uses_agent_result_body() -> None:
    raw = (
        f"{COPILOT_HANDOFF_HEADER}\n"
        "From: Chuyên gia kiểm thử (composer)\n"
        "To: Điều phối viên (coordinator)\n"
        "Goal: Báo cáo coverage\n"
        "Acceptance: Đủ mục đã soạn\n"
        "---\n"
        "Coverage xong 20/20 cửa sổ · 59/59 chunks · 20 mục → 66 scenario / 165 case "
        "— Tải Excel hoặc Apply."
    )
    out = summarize_for_bubble(
        raw,
        max_chars=180,
        from_role=ROLE_COMPOSER,
        to_role=ROLE_COORDINATOR,
    )
    assert out.startswith("Chuyên gia kiểm thử:")
    assert "66 scenario" in out
    assert "Goal:" not in out
    assert "Acceptance:" not in out


def test_revise_verdict_summary_from_agent_return() -> None:
    raw = (
        "REVISE\n"
        "- Thiếu steps / expected / BVA trong scenario.\n"
        "- Không có dữ liệu kiểm thử cụ thể.\n"
        "- JSON bị cắt giữa chừng."
    )
    out = summarize_for_bubble(raw, max_chars=160)
    assert out.startswith("REVISE")
    assert "steps" in out.lower() or "BVA" in out


def test_critic_report_handoff_keeps_revise_findings() -> None:
    raw = (
        f"{COPILOT_HANDOFF_HEADER}\n"
        "From: Chuyên gia phản biện (critic)\n"
        "To: Điều phối viên (coordinator)\n"
        "Goal: Báo cáo kết quả\n"
        "Acceptance: Điều phối viên cập nhật board\n"
        "---\n"
        "REVISE\n"
        "- Thiếu nội dung test scenario thực tế: không có steps/expected.\n"
        "- Không có dữ liệu kiểm thử cụ thể (input/output).\n"
    )
    out = summarize_for_bubble(
        raw,
        max_chars=200,
        from_role=ROLE_CRITIC,
        to_role=ROLE_COORDINATOR,
    )
    assert "REVISE" in out
    assert "steps" in out.lower() or "scenario" in out.lower()
    assert "Báo cáo kết quả" not in out


def test_clip_chat_reply_delegates() -> None:
    out = clip_chat_reply("Planner: 3 bước")
    assert out == "Planner: 3 bước"


def test_chat_prose_keeps_paragraph_breaks() -> None:
    raw = (
        "Đây là câu trả lời chat.\n"
        "\n"
        "Đoạn hai giải thích thêm.\n"
        "- Ý A\n"
        "- Ý B\n"
        "\n"
        "Kết luận ngắn."
    )
    out = summarize_for_bubble(raw, max_chars=180)
    assert "Đây là câu trả lời chat." in out
    assert "Đoạn hai giải thích thêm." in out
    assert "- Ý A" in out
    assert "- Ý B" in out
    assert "Kết luận ngắn." in out
    assert "\n\n" in out
    assert " · " not in out


def test_sanitize_strips_meta_report_and_markdown() -> None:
    from ai_qa_test_studio.ui.bubble_summary import sanitize_user_reply, usable_explain_answer

    raw = (
        "## Kết quả\n"
        "**10 × 200 + 300 = 2.300**\n"
        "\n"
        "## Báo cáo cho Điều phối viên\n"
        "- **Intent nhận được:** explain\n"
        "- Skill đang gắn (a11y-audit) không áp dụng\n"
    )
    out = sanitize_user_reply(raw)
    assert "10 × 200 + 300 = 2.300" in out
    assert "##" not in out
    assert "**" not in out
    assert "Intent" not in out
    assert "a11y" not in out
    assert usable_explain_answer("Câu trả lời đã có: 10×200+300 = 2.300") is True
    assert usable_explain_answer("Hoàn tất điều phối") is False
