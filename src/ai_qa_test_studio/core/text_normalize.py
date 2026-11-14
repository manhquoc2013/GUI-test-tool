"""Safe text-list normalization for Excel/CSV/YAML exporters and LLM parsers."""

from __future__ import annotations

from typing import Any


def coerce_str_list(value: Any) -> list[str]:
    """Coerce LLM/Excel values into a list of non-empty strings.

    A bare ``str`` must become a one-element list — never iterate characters.
    """
    if value is None:
        return []
    if isinstance(value, str):
        text = value.strip()
        return [text] if text else []
    if isinstance(value, (list, tuple)):
        out: list[str] = []
        for item in value:
            text = str(item).strip() if item is not None else ""
            if text:
                out.append(text)
        return out
    text = str(value).strip()
    return [text] if text else []


def normalize_text_list(value: str | list[str] | tuple[str, ...] | None) -> str:
    """Format list-like fields for export cells.

    - ``str`` → stripped as-is (never ``"; ".join`` over characters)
    - ``list`` / ``tuple`` → ``"; ".join`` of stripped non-empty items
    - ``None`` / empty → ``""``
    """
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, (list, tuple)):
        parts = [str(item).strip() for item in value if item is not None and str(item).strip()]
        return "; ".join(parts)
    return str(value).strip()
