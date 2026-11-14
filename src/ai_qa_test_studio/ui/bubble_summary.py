"""User-facing chat bubble text — synthesize from agent results, not protocol."""

from __future__ import annotations

import re

from ai_qa_test_studio.constants import (
    CHAT_ROLE_LABELS,
    COPILOT_CHAT_REPLY_MAX_CHARS,
    COPILOT_FINAL_REPORT_MAX_CHARS,
    COPILOT_HANDOFF_HEADER,
    ROLE_COORDINATOR,
)

_HANDOFF_GOAL_RE = re.compile(r"(?im)^\s*Goal:\s*(.+?)\s*$")
_HANDOFF_BODY_RE = re.compile(r"(?is)^.*?---\s*(.*)\s*$")
_COVERAGE_LINE_RE = re.compile(
    r"(?im)^\s*(Coverage(?:\s+Pass)?(?:\s+xong)?[^\n]*)",
)
_VERDICT_RE = re.compile(r"(?i)\b(APPROVE|REVISE)\b")
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


def _coverage_headline(text: str) -> str | None:
    match = _COVERAGE_LINE_RE.search(text or "")
    if not match:
        return None
    return " ".join(match.group(1).split())


def _verdict_summary(text: str, max_chars: int) -> str | None:
    verdict_match = _VERDICT_RE.search(text or "")
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

    coverage = _coverage_headline(raw)
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
            _coverage_headline(body) is not None or _VERDICT_RE.search(body) is not None
        ):
            return _clip(f"→ {to}: {result}", max_chars)
        return _clip(f"→ Giao {to}", max_chars)

    if result:
        prefix = f"{frm}: " if frm else ""
        return _clip(prefix + result, max_chars)
    if to:
        return _clip(f"→ {to}", max_chars)
    return _clip(frm or "Đã chuyển việc", max_chars)


def summarize_for_bubble(
    text: str,
    *,
    max_chars: int = COPILOT_CHAT_REPLY_MAX_CHARS,
    from_role: str | None = None,
    to_role: str | None = None,
) -> str:
    """Turn agent/full content into a short bubble grounded in returned results."""
    raw = (text or "").strip()
    if not raw:
        return ""
    cap = max(40, int(max_chars))

    if COPILOT_HANDOFF_HEADER in raw or (
        from_role and to_role and ("Goal:" in raw or "From:" in raw)
    ):
        return _handoff_bubble(raw, from_role=from_role, to_role=to_role, max_chars=cap)

    result = _result_core(raw, cap)
    if result:
        return result
    return _clip(raw, cap)


def format_final_report_bubble(
    text: str,
    *,
    max_chars: int = COPILOT_FINAL_REPORT_MAX_CHARS,
) -> str:
    """Keep multi-line final report structure; trim only when over budget."""
    raw = (text or "").strip()
    if not raw:
        return ""
    cap = max(80, int(max_chars))
    if len(raw) <= cap:
        return raw
    kept: list[str] = []
    used = 0
    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        extra = len(stripped) + (1 if kept else 0)
        if used + extra > cap:
            break
        kept.append(stripped)
        used += extra
    if kept:
        out = "\n".join(kept)
        return out if out == raw or len(raw) <= cap else out.rstrip() + "…"
    return _clip(raw, cap)
