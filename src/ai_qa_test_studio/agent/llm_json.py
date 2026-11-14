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
_FANCY_QUOTES = str.maketrans(
    {
        "\u201c": '"',
        "\u201d": '"',
        "\u2018": "'",
        "\u2019": "'",
        "\ufeff": "",
    }
)
_CLOSERS = {"{": "}", "[": "]"}
_CONTROL_ESCAPES = {
    "\n": "\\n",
    "\r": "\\r",
    "\t": "\\t",
    "\b": "\\b",
    "\f": "\\f",
}


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


def _normalize_llm_json_text(text: str) -> str:
    return (text or "").translate(_FANCY_QUOTES)


def _scan_frame(text: str) -> tuple[list[str], bool]:
    """Return (open-bracket stack, in_string)."""
    stack: list[str] = []
    in_string = False
    escape = False
    for ch in text:
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
        if ch in "{[":
            stack.append(ch)
        elif ch in "}]":
            if not stack:
                continue
            open_ch = stack[-1]
            if (open_ch == "{" and ch == "}") or (open_ch == "[" and ch == "]"):
                stack.pop()
    return stack, in_string


def _sanitize_json_strings(text: str) -> str:
    """Escape raw controls inside strings; drop a dangling trailing backslash."""
    out: list[str] = []
    in_string = False
    escape = False
    for ch in text:
        if in_string:
            if escape:
                out.append(ch)
                escape = False
                continue
            if ch == "\\":
                out.append(ch)
                escape = True
                continue
            if ch == '"':
                out.append(ch)
                in_string = False
                continue
            if ord(ch) < 32:
                mapped = _CONTROL_ESCAPES.get(ch)
                out.append(mapped if mapped is not None else f"\\u{ord(ch):04x}")
                continue
            out.append(ch)
            continue
        if ch == '"':
            in_string = True
        out.append(ch)
    if escape and out and out[-1] == "\\":
        out.pop()
    return "".join(out)


def _strip_dangling_key(text: str) -> str:
    """Drop a trailing incomplete ``"key":`` (no value after max-token cut)."""
    out = text.rstrip()
    if not out.endswith(":"):
        return out
    out = out[:-1].rstrip()
    if not out.endswith('"'):
        return out
    end = len(out) - 1
    idx = end - 1
    while idx >= 0:
        if out[idx] == '"' and (idx == 0 or out[idx - 1] != "\\"):
            return out[:idx].rstrip().rstrip(",")
        idx -= 1
    return out.rstrip().rstrip(",")


def _close_truncated_json(text: str) -> str | None:
    """Close open strings/brackets after a max-token cut so complete prefix values survive."""
    if not (text or "").strip():
        return None
    out = _sanitize_json_strings(text.rstrip())
    stack, in_string = _scan_frame(out)
    if in_string:
        out += '"'
        stack, in_string = _scan_frame(out)
    out = out.rstrip().rstrip(",")
    out = _strip_dangling_key(out)
    stack, in_string = _scan_frame(out)
    if in_string:
        out += '"'
        stack, _ = _scan_frame(out)
    out = out.rstrip().rstrip(",")
    out += "".join(_CLOSERS[ch] for ch in reversed(stack))
    return out


def _balanced_slice(text: str, start: int, open_ch: str, close_ch: str) -> str | None:
    """Return ``text[start:end]`` for a balanced bracket pair starting at ``start``."""
    if start < 0 or start >= len(text) or text[start] != open_ch:
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


def _extract_complete_objects(text: str) -> list[dict[str, Any]]:
    """Find every balanced ``{...}`` dict (including nested) that json.loads accepts."""
    items: list[dict[str, Any]] = []
    for index, ch in enumerate(text):
        if ch != "{":
            continue
        blob = _balanced_slice(text, index, "{", "}")
        if blob is None:
            continue
        sanitized = _sanitize_json_strings(blob)
        try:
            parsed = json.loads(sanitized)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            items.append(parsed)
    return items


def _recover_from_fragments(text: str) -> Any | None:
    """Last-resort: rebuild scenarios/cases arrays from complete object fragments."""
    objects = _extract_complete_objects(text)
    if not objects:
        return None
    for obj in objects:
        if isinstance(obj.get("scenarios"), list) or isinstance(obj.get("cases"), list):
            return obj
    scenario_like = [
        obj
        for obj in objects
        if any(key in obj for key in ("code", "title", "description", "requirement_ref"))
        and "steps" not in obj
    ]
    case_like = [
        obj for obj in objects if any(key in obj for key in ("code", "title", "steps", "priority"))
    ]
    looks_like_array = text.lstrip().startswith("[")
    has_scenarios_key = '"scenarios"' in text or "'scenarios'" in text
    has_cases_key = '"cases"' in text or "'cases'" in text
    if looks_like_array and not has_scenarios_key and not has_cases_key:
        if case_like and (not scenario_like or any("steps" in obj for obj in case_like)):
            return case_like
        if scenario_like:
            return scenario_like
        return objects
    if has_scenarios_key and scenario_like:
        return {"scenarios": scenario_like}
    if has_cases_key and case_like:
        return {"cases": case_like}
    if scenario_like and not looks_like_array:
        return {"scenarios": scenario_like}
    if case_like and not looks_like_array:
        return {"cases": case_like}
    if looks_like_array:
        return scenario_like or case_like or objects
    return objects[0] if len(objects) == 1 else objects


def _loads_tolerant(candidate: str, *, allow_fragments: bool = False) -> Any | None:
    text = _sanitize_json_strings(_normalize_llm_json_text(candidate).strip())
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    repaired = _TRAILING_COMMA_RE.sub(r"\1", text)
    candidates = [repaired] if repaired != text else []
    closed = _close_truncated_json(text)
    if closed:
        candidates.append(closed)
        candidates.append(_TRAILING_COMMA_RE.sub(r"\1", closed))
    for item in candidates:
        if item == text:
            continue
        try:
            return json.loads(item)
        except json.JSONDecodeError:
            continue
    if allow_fragments:
        return _recover_from_fragments(text)
    return None


def _extract_balanced(text: str, open_ch: str, close_ch: str) -> str | None:
    start = text.find(open_ch)
    if start < 0:
        return None
    return _balanced_slice(text, start, open_ch, close_ch)


def loads_llm_json(raw: str) -> Any | None:
    """Parse dict/list JSON from a model reply; None when nothing usable is found."""
    text = _normalize_llm_json_text(raw or "").strip()
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
        recovered = _loads_tolerant(text.lstrip(), allow_fragments=True)
        if recovered is not None:
            return recovered

    object_blob = _extract_balanced(text, "{", "}")
    if object_blob is not None:
        parsed = _loads_tolerant(object_blob)
        if parsed is not None:
            return parsed

    brace_at = text.find("{")
    if brace_at >= 0:
        return _loads_tolerant(text[brace_at:], allow_fragments=True)
    if looks_like_array:
        return _recover_from_fragments(text.lstrip())
    return None
