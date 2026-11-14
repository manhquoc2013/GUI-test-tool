"""User-facing chat bubble text — synthesize from agent results, not protocol."""

from __future__ import annotations

import json
import re
from typing import Any

from ai_qa_test_studio.agent.crew_exchange import looks_like_coord_protocol
from ai_qa_test_studio.agent.result_snippet import (
    VERDICT_RE,
    coverage_headline,
)
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_SWARM_AUDIT,
    APPLY_KIND_TEST_DATA,
    CHAT_ROLE_LABELS,
    COPILOT_CHAT_PROSE_MAX_CHARS,
    COPILOT_CHAT_REPLY_MAX_CHARS,
    COPILOT_COORD_COMPLETE_FALLBACK,
    COPILOT_COORD_JSON_HIDDEN,
    COPILOT_EXPLAIN_META_MARKERS,
    COPILOT_FINAL_REPORT_MAX_CHARS,
    COPILOT_HANDOFF_HEADER,
    COPILOT_JSON_STREAM_PROGRESS,
    COPILOT_STRUCTURED_DRAFT_BANNER,
    ROLE_COORDINATOR,
)

_JSON_FENCE_RE = re.compile(r"```(?:json)?\s*([\s\S]*?)```", re.IGNORECASE)
_RAW_JSON_OBJ_RE = re.compile(r"\{[\s\S]*\}")
_CODE_FIELD_RE = re.compile(r'"code"\s*:')

_MD_HEADING_RE = re.compile(r"(?m)^\s{0,3}#{1,6}\s+")
_MD_BOLD_RE = re.compile(r"\*\*(.+?)\*\*")
_MD_ITALIC_RE = re.compile(r"(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)")
_META_SECTION_RE = re.compile(
    r"(?is)\n+\s*#{0,6}\s*Báo cáo cho Điều phối[^\n]*\n.*$",
)

_HANDOFF_GOAL_RE = re.compile(r"(?im)^\s*Goal:\s*(.+?)\s*$")
_HANDOFF_BODY_RE = re.compile(r"(?is)^.*?---\s*(.*)\s*$")
_BULLET_RE = re.compile(r"(?m)^\s*[-*•]\s+(.+)$")
_PROTOCOL_LINE_RE = re.compile(
    r"(?i)^(From|To|Goal|Acceptance|\[HANDOFF\])\b",
)
_SECTION_OK_RE = re.compile(r"(?i)^S\d+\b.*:\s*ok\s*$")


def role_label(role: str) -> str:
    return CHAT_ROLE_LABELS.get(role, role)


def _clip(text: str, max_chars: int) -> str:
    compact = " ".join((text or "").split())
    if len(compact) <= max_chars:
        return compact
    cut = compact[: max(0, max_chars - 1)].rstrip()
    for sep in (". ", " · ", "; ", ", ", " — ", " - "):
        idx = cut.rfind(sep)
        if idx >= max_chars // 2:
            cut = cut[: idx + len(sep)].rstrip()
            break
    return cut.rstrip(" ·,;:-") + "…"


def _is_noise_line(line: str) -> bool:
    stripped = line.strip()
    if not stripped or stripped == "(empty)":
        return True
    if _PROTOCOL_LINE_RE.match(stripped):
        return True
    if stripped.startswith("---"):
        return True
    if stripped.startswith("→") and "Apply" in stripped:
        return True
    if _SECTION_OK_RE.match(stripped.lstrip("-*• ").strip()):
        return True
    return False


def _collect_result_lines(text: str, *, limit: int = 4) -> list[str]:
    lines: list[str] = []
    for raw_line in (text or "").splitlines():
        if _is_noise_line(raw_line):
            continue
        stripped = raw_line.strip().lstrip("-*• ").strip()
        if not stripped:
            continue
        lines.append(stripped)
        if len(lines) >= limit:
            break
    return lines


def _verdict_summary(text: str, max_chars: int) -> str | None:
    verdict_match = VERDICT_RE.search(text or "")
    if not verdict_match:
        return None
    verdict = verdict_match.group(1).upper()
    bullets = [
        m.group(1).strip()
        for m in _BULLET_RE.finditer(text or "")
        if not _is_noise_line(m.group(1))
    ]
    if not bullets:
        after = (text or "")[verdict_match.end() :].lstrip(" -:·")
        lines = _collect_result_lines(after, limit=2)
        head = " · ".join(lines) if lines else after.strip()
        return _clip(f"{verdict} · {head}", max_chars) if head else verdict
    parts = [verdict]
    for bullet in bullets[:3]:
        piece = _clip(bullet, max(32, max_chars - len(" · ".join(parts)) - 3))
        candidate = " · ".join([*parts, piece])
        if len(candidate) > max_chars:
            break
        parts.append(piece)
    return " · ".join(parts)


def _result_core(text: str, max_chars: int) -> str:
    """Short synthesis grounded in the agent return body."""
    raw = (text or "").strip()
    if not raw or raw == "(empty)":
        return ""

    coverage = coverage_headline(raw)
    if coverage:
        return _clip(coverage, max_chars)

    verdict = _verdict_summary(raw, max_chars)
    if verdict:
        return verdict

    lines = _collect_result_lines(raw, limit=3)
    if not lines:
        return ""
    if len(lines) == 1:
        return _clip(lines[0], max_chars)
    return _clip(" · ".join(lines), max_chars)


def _is_compact_agent_result(text: str) -> bool:
    """Coverage / APPROVE|REVISE dumps stay one-line; chat prose does not."""
    raw = (text or "").strip()
    if not raw:
        return False
    if coverage_headline(raw) is not None:
        return True
    first = next((ln.strip() for ln in raw.splitlines() if ln.strip()), "")
    return bool(first and VERDICT_RE.search(first))


def _strip_markdown_markers(text: str) -> str:
    """QLabel is plain text — drop ## / ** so chat stays readable."""
    out = _MD_HEADING_RE.sub("", text or "")
    out = _MD_BOLD_RE.sub(r"\1", out)
    out = _MD_ITALIC_RE.sub(r"\1", out)
    return out


def sanitize_user_reply(text: str) -> str:
    """Drop coordinator-meta dumps; keep the user-facing answer body."""
    raw = (text or "").replace("\r\n", "\n").replace("\r", "\n").strip()
    if not raw:
        return ""
    if looks_like_coord_protocol(raw):
        return COPILOT_COORD_JSON_HIDDEN

    cut = _META_SECTION_RE.sub("", raw).strip()
    lines: list[str] = []
    blank_run = 0
    for line in cut.splitlines():
        lowered = line.strip().casefold()
        if any(marker in lowered for marker in COPILOT_EXPLAIN_META_MARKERS):
            # Drop this line and everything after (meta trail).
            break
        stripped = line.rstrip()
        if not stripped.strip():
            blank_run += 1
            if blank_run == 1 and lines:
                lines.append("")
            continue
        blank_run = 0
        lines.append(_strip_markdown_markers(stripped))
    return "\n".join(lines).strip()


def format_prose_bubble(
    text: str,
    *,
    max_chars: int = COPILOT_CHAT_PROSE_MAX_CHARS,
) -> str:
    """Keep paragraph breaks and blank-line separators for readable chat."""
    out = sanitize_user_reply(text)
    if not out:
        return ""
    if out == COPILOT_COORD_JSON_HIDDEN:
        return out

    lines = out.splitlines()
    cap = max(80, int(max_chars))
    if len(out) <= cap:
        return out

    kept: list[str] = []
    used = 0
    for line in lines:
        piece = line
        extra = len(piece) + (1 if kept else 0)
        if used + extra > cap:
            break
        kept.append(piece)
        used += extra
    if kept:
        body = "\n".join(kept).rstrip()
        return body if body == out else body + "…"
    return _clip(out, cap)


def usable_explain_answer(text: str) -> bool:
    """True when coordinator summary is already a user-facing answer."""
    raw = sanitize_user_reply(text)
    if not raw or raw == COPILOT_COORD_JSON_HIDDEN:
        return False
    compact = " ".join(raw.split())
    if len(compact) < 8:
        return False
    noise = {
        COPILOT_COORD_COMPLETE_FALLBACK.casefold(),
        COPILOT_COORD_JSON_HIDDEN.casefold(),
        "hoàn tất",
        "đủ deliverable",
        "đã có câu trả lời — đóng vòng điều phối",
    }
    return compact.casefold() not in noise


def _parse_handoff(text: str) -> tuple[str, str]:
    """Return (goal, body) from a structured handoff packet."""
    raw = text or ""
    goal_match = _HANDOFF_GOAL_RE.search(raw)
    goal = (goal_match.group(1).strip() if goal_match else "") or ""
    body_match = _HANDOFF_BODY_RE.search(raw)
    body = (body_match.group(1).strip() if body_match else "") or ""
    if not body and COPILOT_HANDOFF_HEADER not in raw:
        body = raw.strip()
    return goal, body


def _handoff_bubble(
    text: str,
    *,
    from_role: str | None,
    to_role: str | None,
    max_chars: int,
) -> str:
    _goal, body = _parse_handoff(text)
    del _goal  # Protocol goal is not the user-facing result.
    result = _result_core(body, max(48, max_chars - 28))
    frm = role_label(from_role) if from_role else ""
    to = role_label(to_role) if to_role else ""

    # Specialist → coordinator: always show the returned result.
    if to_role == ROLE_COORDINATOR and from_role and from_role != ROLE_COORDINATOR:
        if result:
            return _clip(f"{frm}: {result}", max_chars)
        return _clip(f"{frm}: chưa có kết quả", max_chars)

    # Coordinator → specialist: only surface body when it carries a prior
    # agent result (coverage / verdict). Plain task briefs stay a short cue.
    if from_role == ROLE_COORDINATOR and to_role and to_role != ROLE_COORDINATOR:
        if result and (
            coverage_headline(body) is not None or VERDICT_RE.search(body) is not None
        ):
            return _clip(f"→ {to}: {result}", max_chars)
        return _clip(f"→ Giao {to}", max_chars)

    if result:
        prefix = f"{frm}: " if frm else ""
        return _clip(prefix + result, max_chars)
    if to:
        return _clip(f"→ {to}", max_chars)
    return _clip(frm or "Đã chuyển việc", max_chars)


def extract_structured_payload(text: str) -> dict[str, Any] | None:
    """Parse compose/test-data JSON from assistant text when present."""
    raw = (text or "").strip()
    if not raw:
        return None
    candidates: list[str] = []
    for match in _JSON_FENCE_RE.finditer(raw):
        candidates.append(match.group(1).strip())
    if raw.startswith("{") and raw.endswith("}"):
        candidates.append(raw)
    else:
        obj_match = _RAW_JSON_OBJ_RE.search(raw)
        if obj_match:
            candidates.append(obj_match.group(0))
    for blob in candidates:
        try:
            parsed = json.loads(blob)
        except (json.JSONDecodeError, TypeError):
            continue
        if not isinstance(parsed, dict):
            continue
        kind = str(parsed.get("kind") or "")
        if kind == APPLY_KIND_TEST_DATA and isinstance(parsed.get("rows"), list):
            return parsed
        if isinstance(parsed.get("scenarios"), list) or isinstance(parsed.get("cases"), list):
            if not kind:
                parsed = {**parsed, "kind": APPLY_KIND_COMPOSE}
            return parsed
        if kind in {APPLY_KIND_COMPOSE, "scenarios", "compose"}:
            return parsed
    return None


def format_structured_payload_summary(payload: dict[str, Any] | None) -> str:
    """Render Apply-ready payload as a compact Markdown summary card (no raw JSON)."""
    if not isinstance(payload, dict):
        return ""
    kind = str(payload.get("kind") or "")
    # Large suites: never dump hundreds of HTML table rows into the chat bubble.
    if kind == APPLY_KIND_COMPOSE or isinstance(payload.get("scenarios"), list):
        from ai_qa_test_studio.agent.batch_compose import (
            format_large_suite_summary_card,
            is_large_suite_payload,
        )

        if is_large_suite_payload(payload):
            return format_large_suite_summary_card(payload)
    if kind == APPLY_KIND_SWARM_AUDIT:
        markdown = str(payload.get("markdown") or "").strip()
        if markdown:
            return markdown
        raw_metrics = payload.get("metrics")
        metrics: dict[str, Any] = raw_metrics if isinstance(raw_metrics, dict) else {}
        return (
            "## Báo cáo kiểm toán (SWARM_AUDIT)\n\n"
            f"| Tổng Case | Pass | Healed | Failed | Blocked | Độ phủ |\n"
            f"| ---: | ---: | ---: | ---: | ---: | ---: |\n"
            f"| {metrics.get('case_total', 0)} | {metrics.get('pure_pass', 0)} | "
            f"{metrics.get('healed', 0)} | {metrics.get('failed', 0)} | "
            f"{metrics.get('blocked', 0)} | {metrics.get('requirement_coverage_pct', 0)}% |"
        )
    lines = [COPILOT_STRUCTURED_DRAFT_BANNER, ""]
    if kind == APPLY_KIND_TEST_DATA:
        rows = payload.get("rows") or []
        count = len(rows) if isinstance(rows, list) else 0
        lines.append(f"**Test Data:** {count} bộ sẵn sàng Apply.")
        return "\n".join(lines).strip()

    scenarios = payload.get("scenarios")
    if not isinstance(scenarios, list):
        root_cases = payload.get("cases")
        if isinstance(root_cases, list):
            scenarios = [{"code": "TS", "title": "Suite", "cases": root_cases}]
        else:
            return ""

    lines.extend(
        [
            "| Mã | Tên kịch bản | Số Test Case | Case con |",
            "| --- | --- | ---: | --- |",
        ]
    )
    for item in scenarios:
        if not isinstance(item, dict):
            continue
        code = str(item.get("code") or "—").strip() or "—"
        title = str(item.get("title") or item.get("name") or "—").strip() or "—"
        raw_cases = item.get("cases")
        scenario_cases: list[Any] = raw_cases if isinstance(raw_cases, list) else []
        case_names: list[str] = []
        for case in scenario_cases:
            if isinstance(case, dict):
                name = str(case.get("title") or case.get("code") or "").strip()
                if name:
                    case_names.append(name)
        case_list = ", ".join(case_names) if case_names else "—"
        lines.append(
            f"| `{code}` | {title} | {len(scenario_cases)} | {case_list} |"
        )
    if len(lines) <= 4:
        return ""
    return "\n".join(lines).strip()


def structured_stream_progress(partial_text: str) -> str | None:
    """Compact progress label while a large JSON artifact is streaming."""
    raw = partial_text or ""
    if "{" not in raw:
        return None
    if '"scenarios"' not in raw and '"cases"' not in raw and '"rows"' not in raw:
        return None
    count = len(_CODE_FIELD_RE.findall(raw))
    if count <= 0:
        return COPILOT_JSON_STREAM_PROGRESS.format(count=0)
    return COPILOT_JSON_STREAM_PROGRESS.format(count=count)


def strip_raw_json_for_bubble(text: str) -> str:
    """Remove fenced/raw JSON blobs from visible bubble prose."""
    raw = (text or "").strip()
    if not raw:
        return ""
    without_fences = _JSON_FENCE_RE.sub("", raw).strip()
    if without_fences.startswith("{") and '"code"' in without_fences:
        return ""
    cleaned = _RAW_JSON_OBJ_RE.sub("", without_fences).strip()
    return cleaned


def summarize_for_bubble(
    text: str,
    *,
    max_chars: int = COPILOT_CHAT_REPLY_MAX_CHARS,
    from_role: str | None = None,
    to_role: str | None = None,
    structured_payload: dict[str, Any] | None = None,
) -> str:
    """Turn agent/full content into a user-facing bubble."""
    raw = (text or "").strip()
    if not raw and not structured_payload:
        return ""
    cap = max(40, int(max_chars))
    payload = structured_payload or extract_structured_payload(raw)
    if payload:
        card = format_structured_payload_summary(payload)
        prose = strip_raw_json_for_bubble(raw)
        if card and prose:
            return f"{prose}\n\n{card}"
        if card:
            return card
    if not raw:
        return ""
    if looks_like_coord_protocol(raw):
        return COPILOT_COORD_JSON_HIDDEN

    if COPILOT_HANDOFF_HEADER in raw or (
        from_role and to_role and ("Goal:" in raw or "From:" in raw)
    ):
        return _handoff_bubble(raw, from_role=from_role, to_role=to_role, max_chars=cap)

    # Compact one-liners only for coverage / critic verdict dumps.
    if _is_compact_agent_result(raw):
        result = _result_core(raw, cap)
        if result:
            return result
        return _clip(raw, cap)

    # Normal chat / explain: keep paragraphs and blank-line separators.
    return format_prose_bubble(
        raw,
        max_chars=max(cap, COPILOT_CHAT_PROSE_MAX_CHARS),
    )


def format_final_report_bubble(
    text: str,
    *,
    max_chars: int = COPILOT_FINAL_REPORT_MAX_CHARS,
) -> str:
    """Keep multi-line final report structure; trim only when over budget."""
    raw = (text or "").strip()
    if not raw:
        return ""
    if looks_like_coord_protocol(raw):
        return COPILOT_COORD_JSON_HIDDEN
    # Prefer the same paragraph-preserving path as chat prose.
    return format_prose_bubble(raw, max_chars=max(80, int(max_chars)))
