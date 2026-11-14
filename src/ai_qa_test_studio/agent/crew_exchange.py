"""Coordinator-led multi-agent exchange (NEXUS/Ruflo hierarchical — not vendored).

Điều phối viên giữ board ngữ cảnh, dispatch chuyên gia, nhận báo cáo, quyết định
bước tiếp (dispatch lại / complete). Offline dùng heuristic state machine.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.constants import (
    COMPLEXITY_SIMPLE,
    COORD_ACTION_COMPLETE,
    COORD_ACTION_DISPATCH,
    COORD_SPECIALISTS,
    INTENT_COMPOSE,
    INTENT_EXPLAIN,
    ROLE_ANALYZER,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
    ROLE_OPTIMIZER,
    ROLE_PLANNER,
)


@dataclass
class CrewBoard:
    """Shared turn context owned by the coordinator."""

    user_text: str
    mode: str
    ctx_block: str
    intent: str
    complexity: str
    plan_text: str = ""
    compose_text: str = ""
    critique_text: str = ""
    analyze_text: str = ""
    optimize_text: str = ""
    apply_payload: dict[str, Any] | None = None
    critique_approved: bool = True
    critique_notes: str = ""
    revise_done: bool = False
    dispatched: list[str] = field(default_factory=list)
    reports: list[str] = field(default_factory=list)

    def digest(self, *, max_chars: int = 1800) -> str:
        parts = [
            f"Intent={self.intent}",
            f"Complexity={self.complexity}",
            f"Dispatched={','.join(self.dispatched) or '-'}",
            f"HasPlan={bool(self.plan_text)}",
            f"HasCompose={bool(self.compose_text)}",
            f"HasCritique={bool(self.critique_text)}",
            f"CritiqueApproved={self.critique_approved}",
            f"HasAnalyze={bool(self.analyze_text)}",
            f"HasOptimize={bool(self.optimize_text)}",
            f"HasArtifact={bool(self.apply_payload)}",
            f"ReviseDone={self.revise_done}",
        ]
        if self.critique_notes:
            parts.append(f"CritiqueNotes={self.critique_notes[:240]}")
        if self.reports:
            parts.append("RecentReports:\n" + "\n".join(self.reports[-4:]))
        blob = "\n".join(parts)
        return blob if len(blob) <= max_chars else blob[: max_chars - 1] + "…"

    def record_report(self, role: str, text: str) -> None:
        summary = " ".join((text or "").split())
        if len(summary) > 220:
            summary = summary[:219] + "…"
        self.reports.append(f"{role}: {summary}")


def heuristic_next_action(
    board: CrewBoard,
    *,
    run_analyzer: bool,
    run_optimizer: bool,
) -> dict[str, str]:
    """Deterministic coordinator policy when LLM is offline / parse fails."""
    dispatched = set(board.dispatched)
    need_plan = (
        board.intent == INTENT_COMPOSE
        and board.complexity != COMPLEXITY_SIMPLE
        and not board.compose_text
        and ROLE_PLANNER not in dispatched
    )
    if need_plan:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_PLANNER,
            "goal": "Lập kế hoạch giao việc cho đội",
            "acceptance": "Mục tiêu + bước + acceptance rõ",
            "brief": board.user_text,
        }
    if not board.compose_text or (
        not board.critique_approved and board.critique_notes and not board.revise_done
    ):
        revise = bool(board.compose_text and not board.critique_approved)
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_COMPOSER,
            "goal": "Sửa theo phản biện" if revise else "Soạn deliverable theo intent",
            "acceptance": "Artifact/Apply hoặc câu trả lời rõ theo intent",
            "brief": board.critique_notes if revise else (board.plan_text or board.user_text),
        }
    if ROLE_CRITIC not in dispatched:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_CRITIC,
            "goal": "Quality gate cho deliverable",
            "acceptance": "APPROVE có căn cứ hoặc REVISE cụ thể",
            "brief": board.compose_text,
        }
    if run_analyzer and ROLE_ANALYZER not in dispatched and board.intent != INTENT_EXPLAIN:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_ANALYZER,
            "goal": "Phân tích rủi ro / nguyên nhân",
            "acceptance": "Gợi ý heal có căn cứ",
            "brief": board.compose_text,
        }
    if run_optimizer and ROLE_OPTIMIZER not in dispatched and board.intent == INTENT_COMPOSE:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_OPTIMIZER,
            "goal": "Tối ưu suite P0 / coverage",
            "acceptance": "Giữ / bỏ / bổ sung có lý do",
            "brief": board.compose_text,
        }
    return {
        "action": COORD_ACTION_COMPLETE,
        "summary": "Đủ deliverable — đóng vòng điều phối",
        "to": ROLE_COORDINATOR,
        "goal": "",
        "acceptance": "",
        "brief": "",
    }


def parse_coord_decision(raw: str, fallback: dict[str, str]) -> dict[str, str]:
    """Parse coordinator JSON; fall back to heuristic on failure."""
    import json
    import re

    text = (raw or "").strip()
    if not text:
        return fallback
    try:
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            data = json.loads(text[start : end + 1])
        else:
            data = json.loads(text)
    except (json.JSONDecodeError, TypeError, ValueError):
        # Soft parse: look for dispatch to=<role>
        lowered = text.lower()
        if COORD_ACTION_COMPLETE in lowered:
            return {
                "action": COORD_ACTION_COMPLETE,
                "summary": text[:200],
                "to": ROLE_COORDINATOR,
                "goal": "",
                "acceptance": "",
                "brief": "",
            }
        for role in COORD_SPECIALISTS:
            if re.search(rf"\b{role}\b", lowered):
                return {
                    "action": COORD_ACTION_DISPATCH,
                    "to": role,
                    "goal": "Tiếp tục theo quyết định điều phối",
                    "acceptance": "Báo cáo rõ cho Điều phối viên",
                    "brief": text[:500],
                }
        return fallback
    if not isinstance(data, dict):
        return fallback
    action = str(data.get("action") or "").strip().lower()
    to = str(data.get("to") or "").strip().lower()
    if action == COORD_ACTION_COMPLETE:
        return {
            "action": COORD_ACTION_COMPLETE,
            "summary": str(data.get("summary") or "Hoàn tất"),
            "to": ROLE_COORDINATOR,
            "goal": "",
            "acceptance": "",
            "brief": "",
        }
    if action == COORD_ACTION_DISPATCH and to in COORD_SPECIALISTS:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": to,
            "goal": str(data.get("goal") or f"Giao việc cho {to}"),
            "acceptance": str(data.get("acceptance") or "Báo cáo kết quả"),
            "brief": str(data.get("brief") or data.get("goal") or ""),
        }
    return fallback
