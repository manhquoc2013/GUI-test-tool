"""Coordinator-led multi-agent exchange (NEXUS/Ruflo hierarchical — not vendored).

Điều phối viên giữ board ngữ cảnh, dispatch chuyên gia, nhận báo cáo, quyết định
bước tiếp (dispatch lại / complete / clarify). Offline dùng heuristic Dev↔QA.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.constants import (
    COMPLEXITY_SIMPLE,
    COORD_ACTION_CLARIFY,
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

_DIGEST_MAX_CHARS = 1800
_REPORT_SUMMARY_MAX = 220
_NOTES_DIGEST_MAX = 240
_SOFT_BRIEF_MAX = 500
_SOFT_SUMMARY_MAX = 200


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
    critique_stale: bool = False
    critique_rounds: int = 0
    dispatched: list[str] = field(default_factory=list)
    reports: list[str] = field(default_factory=list)

    def digest(self, *, max_chars: int = _DIGEST_MAX_CHARS) -> str:
        parts = [
            f"Intent={self.intent}",
            f"Complexity={self.complexity}",
            f"Dispatched={','.join(self.dispatched) or '-'}",
            f"HasPlan={bool(self.plan_text)}",
            f"HasCompose={bool(self.compose_text)}",
            f"HasCritique={bool(self.critique_text)}",
            f"CritiqueApproved={self.critique_approved}",
            f"CritiqueStale={self.critique_stale}",
            f"CritiqueRounds={self.critique_rounds}",
            f"HasAnalyze={bool(self.analyze_text)}",
            f"HasOptimize={bool(self.optimize_text)}",
            f"HasArtifact={bool(self.apply_payload)}",
            f"ReviseDone={self.revise_done}",
        ]
        if self.critique_notes:
            parts.append(f"CritiqueNotes={self.critique_notes[:_NOTES_DIGEST_MAX]}")
        if self.reports:
            parts.append("RecentReports:\n" + "\n".join(self.reports[-4:]))
        blob = "\n".join(parts)
        return blob if len(blob) <= max_chars else blob[: max_chars - 1] + "…"

    def record_report(self, role: str, text: str) -> None:
        summary = " ".join((text or "").split())
        if len(summary) > _REPORT_SUMMARY_MAX:
            summary = summary[: _REPORT_SUMMARY_MAX - 1] + "…"
        self.reports.append(f"{role}: {summary}")

    def mark_compose(self, *, revising: bool) -> None:
        """Composer produced new text — Critic must re-gate (agency Dev↔QA)."""
        self.critique_stale = True
        if revising:
            self.revise_done = True

    def mark_critique(self, *, approved: bool, notes: str, text: str) -> None:
        self.critique_text = text
        self.critique_approved = approved
        self.critique_notes = notes
        self.critique_stale = False
        self.critique_rounds += 1
        if approved:
            self.revise_done = True


def heuristic_next_action(
    board: CrewBoard,
    *,
    run_analyzer: bool,
    run_optimizer: bool,
    max_critique_rounds: int,
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
    if not board.compose_text:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_COMPOSER,
            "goal": "Soạn deliverable theo intent",
            "acceptance": "Artifact/Apply hoặc câu trả lời rõ theo intent",
            "brief": board.plan_text or board.user_text,
        }

    quality_on = max_critique_rounds > 0
    if quality_on and board.critique_stale and board.critique_rounds < max_critique_rounds:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_CRITIC,
            "goal": "Quality gate cho deliverable",
            "acceptance": "APPROVE có căn cứ hoặc REVISE cụ thể",
            "brief": board.compose_text,
        }
    if (
        quality_on
        and not board.critique_approved
        and board.critique_notes
        and board.critique_rounds < max_critique_rounds
    ):
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": ROLE_COMPOSER,
            "goal": "Sửa theo phản biện",
            "acceptance": "Artifact/Apply hoặc câu trả lời rõ theo intent",
            "brief": board.critique_notes,
        }
    if quality_on and not board.critique_approved and board.critique_rounds >= max_critique_rounds:
        return {
            "action": COORD_ACTION_COMPLETE,
            "summary": "Hết ngân sách phản biện — đóng vòng với bản đã sửa gần nhất",
            "to": ROLE_COORDINATOR,
            "goal": "",
            "acceptance": "",
            "brief": "",
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


def _empty_clarify(goal: str = "") -> dict[str, str]:
    return {
        "action": COORD_ACTION_CLARIFY,
        "to": ROLE_COORDINATOR,
        "goal": goal or "Cần làm rõ yêu cầu?",
        "acceptance": "",
        "brief": "",
        "summary": "",
    }


def _empty_complete(summary: str) -> dict[str, str]:
    return {
        "action": COORD_ACTION_COMPLETE,
        "summary": summary,
        "to": ROLE_COORDINATOR,
        "goal": "",
        "acceptance": "",
        "brief": "",
    }


def _soft_parse(text: str, fallback: dict[str, str]) -> dict[str, str]:
    lowered = text.lower()
    if COORD_ACTION_COMPLETE in lowered:
        return _empty_complete(text[:_SOFT_SUMMARY_MAX])
    if COORD_ACTION_CLARIFY in lowered:
        return _empty_clarify(text[:_SOFT_SUMMARY_MAX])
    hits = [role for role in COORD_SPECIALISTS if re.search(rf"\b{re.escape(role)}\b", lowered)]
    if len(hits) == 1:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": hits[0],
            "goal": "Tiếp tục theo quyết định điều phối",
            "acceptance": "Báo cáo rõ cho Điều phối viên",
            "brief": text[:_SOFT_BRIEF_MAX],
        }
    return fallback


def parse_coord_decision(raw: str, fallback: dict[str, str]) -> dict[str, str]:
    """Parse coordinator JSON; fall back to heuristic on failure."""
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
        return _soft_parse(text, fallback)
    if not isinstance(data, dict):
        return fallback
    action = str(data.get("action") or "").strip().lower()
    to = str(data.get("to") or "").strip().lower()
    if action == COORD_ACTION_COMPLETE:
        return _empty_complete(str(data.get("summary") or "Hoàn tất"))
    if action == COORD_ACTION_CLARIFY:
        return _empty_clarify(str(data.get("goal") or data.get("question") or "Cần làm rõ yêu cầu?"))
    if action == COORD_ACTION_DISPATCH and to in COORD_SPECIALISTS:
        return {
            "action": COORD_ACTION_DISPATCH,
            "to": to,
            "goal": str(data.get("goal") or f"Giao việc cho {to}"),
            "acceptance": str(data.get("acceptance") or "Báo cáo kết quả"),
            "brief": str(data.get("brief") or data.get("goal") or ""),
        }
    return fallback
