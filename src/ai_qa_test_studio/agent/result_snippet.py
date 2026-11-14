"""Shared one-line synthesis of specialist / coverage returns (crew + chat UI)."""

from __future__ import annotations

import re

from ai_qa_test_studio.constants import COPILOT_RESULT_LINE_MAX

COVERAGE_LINE_RE = re.compile(
    r"(?im)^\s*(Coverage(?:\s+Pass)?(?:\s+xong)?[^\n]*)",
)
VERDICT_RE = re.compile(r"(?i)\b(APPROVE|REVISE)\b")
_BULLET_RE = re.compile(r"(?m)^\s*[-*•]\s+(.+)$")
_SECTION_OK_RE = re.compile(r"(?i)^S\d+\b")


def clip_one_line(text: str, *, max_chars: int = COPILOT_RESULT_LINE_MAX) -> str:
    compact = " ".join((text or "").split())
    if len(compact) <= max_chars:
        return compact
    return compact[: max_chars - 1].rstrip() + "…"


def coverage_headline(text: str) -> str | None:
    match = COVERAGE_LINE_RE.search(text or "")
    if not match:
        return None
    return " ".join(match.group(1).split())


def agent_result_line(
    text: str,
    *,
    max_chars: int = COPILOT_RESULT_LINE_MAX,
) -> str:
    """Pick the most user-relevant line from a specialist return."""
    raw = (text or "").strip()
    if not raw:
        return ""
    coverage = coverage_headline(raw)
    if coverage:
        return clip_one_line(coverage, max_chars=max_chars)
    verdict = VERDICT_RE.search(raw)
    if verdict:
        bullets = _BULLET_RE.findall(raw)
        if bullets:
            return clip_one_line(
                f"{verdict.group(1).upper()} · {bullets[0]}",
                max_chars=max_chars,
            )
        after = raw[verdict.end() :].lstrip(" -:·")
        head = after.splitlines()[0].strip() if after else ""
        label = verdict.group(1).upper()
        return clip_one_line(f"{label} · {head}" if head else label, max_chars=max_chars)
    for line in raw.splitlines():
        stripped = line.strip().lstrip("-*• ").strip()
        if not stripped or stripped == "(empty)":
            continue
        if stripped.startswith("→"):
            continue
        if _SECTION_OK_RE.match(stripped):
            continue
        return clip_one_line(stripped, max_chars=max_chars)
    return clip_one_line(raw, max_chars=max_chars)
