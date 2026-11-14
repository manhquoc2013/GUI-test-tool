from __future__ import annotations

from ai_qa_test_studio.agent.crew_exchange import CrewBoard, build_final_report
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    COMPLEXITY_COMPLEX,
    COPILOT_FINAL_REPORT_EMPTY,
    INTENT_COMPOSE,
)
from ai_qa_test_studio.ui.bubble_summary import format_final_report_bubble


def test_build_final_report_includes_artifact_and_specialist_results() -> None:
    board = CrewBoard(
        user_text="Tạo kịch bản",
        mode="compose",
        ctx_block="",
        intent=INTENT_COMPOSE,
        complexity=COMPLEXITY_COMPLEX,
        plan_text="**Mục tiêu:** Tạo bộ test scenario cho hồ sơ người yếu thế.",
        compose_text=(
            "Coverage xong 20/20 cửa sổ · 59/59 chunks → 66 scenario / 165 case — Apply."
        ),
        critique_text="REVISE\n- Thiếu steps/expected trong vài scenario.",
        critique_approved=False,
        critique_notes="Thiếu steps/expected",
        apply_payload={
            "kind": APPLY_KIND_COMPOSE,
            "scenarios": [
                {"code": "T1", "cases": [{}, {}]},
                {"code": "T2", "cases": [{}]},
            ],
        },
    )
    report = build_final_report(board, closing="Hoàn tất điều phối")
    assert "Hoàn tất điều phối" in report
    assert "2 scenario / 3 case" in report
    assert "Kế hoạch:" in report
    assert "Soạn thảo:" in report and "66 scenario" in report
    assert "REVISE" in report
    assert "Quality gate: REVISE" in report


def test_build_final_report_empty_board() -> None:
    board = CrewBoard(
        user_text="hi",
        mode="case",
        ctx_block="",
        intent="explain",
        complexity="simple",
    )
    assert build_final_report(board) == COPILOT_FINAL_REPORT_EMPTY


def test_format_final_report_bubble_keeps_multiline() -> None:
    raw = (
        "Hoàn tất điều phối\n"
        "Artifact: 66 scenario / 165 case — Tải Excel hoặc Apply.\n"
        "Phản biện: REVISE · Thiếu steps/expected."
    )
    out = format_final_report_bubble(raw, max_chars=420)
    assert "Artifact:" in out
    assert "Phản biện:" in out
    assert out.count("\n") >= 2
