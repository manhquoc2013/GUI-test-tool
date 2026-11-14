"""Lenient JSON extraction for LLM chat replies (fences, prose wrappers, trailing commas)."""

from __future__ import annotations

import json
import re
from typing import Any

from ai_qa_test_studio.constants import LLM_JSON_LOG_PREVIEW_CHARS

_FENCE_RE = re.compile(
    r"```(?:json|JSON|Json)?\s*\n?(.*?)```",
    re.DOTALL,
)
_OPEN_FENCE_RE = re.compile(
    r"^```(?:json|JSON|Json)?\s*\n?(.*)$",
    re.DOTALL,
)
_TRAILING_COMMA_RE = re.compile(r",(\s*[}\]])")


def preview_llm_text(raw: str, *, limit: int = LLM_JSON_LOG_PREVIEW_CHARS) -> str:
    compact = " ".join((raw or "").split())
    if len(compact) <= limit:
        return compact
    return compact[: max(0, limit - 1)].rstrip() + "…"


def strip_markdown_fence(text: str) -> str:
    """Return inner body of a ``` / ```json fence (closed or unclosed), else original text."""
    stripped = (text or "").strip()
    if not stripped:
        return ""
    match = _FENCE_RE.search(stripped)
    if match:
        return match.group(1).strip()
    open_match = _OPEN_FENCE_RE.match(stripped)
    if open_match:
        return open_match.group(1).strip()
    return stripped


def _loads_tolerant(candidate: str) -> Any | None:
    text = (candidate or "").strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    repaired = _TRAILING_COMMA_RE.sub(r"\1", text)
    if repaired == text:
        return None
    try:
        return json.loads(repaired)
    except json.JSONDecodeError:
        return None


def _extract_balanced(text: str, open_ch: str, close_ch: str) -> str | None:
    start = text.find(open_ch)
    if start < 0:
        return None
    depth = 0
    in_string = False
    escape = False
    for index in range(start, len(text)):
        ch = text[index]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
            continue
        if ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return text[start : index + 1]
    return None


def loads_llm_json(raw: str) -> Any | None:
    """Parse dict/list JSON from a model reply; None when nothing usable is found."""
    text = (raw or "").strip()
    if not text:
        return None

    direct = _loads_tolerant(text)
    if direct is not None:
        return direct

    unfenced = strip_markdown_fence(text)
    if unfenced != text:
        parsed = _loads_tolerant(unfenced)
        if parsed is not None:
            return parsed
        text = unfenced

    looks_like_array = text.lstrip().startswith("[")
    array_blob = _extract_balanced(text, "[", "]")
    if array_blob is not None:
        parsed = _loads_tolerant(array_blob)
        if parsed is not None:
            return parsed
    if looks_like_array:
        # Incomplete array — do not silently promote the first nested object.
        return None

    object_blob = _extract_balanced(text, "{", "}")
    if object_blob is not None:
        parsed = _loads_tolerant(object_blob)
        if parsed is not None:
            return parsed

    return None
