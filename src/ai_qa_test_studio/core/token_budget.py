"""Token budget, sliding context, in-process headroom + markitdown."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

import tiktoken

from ai_qa_test_studio.constants import (
    SLIDING_HISTORY_SUMMARY_CHARS,
    TOKEN_DOM_SOFT_CAP,
    TOKEN_MAX_ATOMIC_OUTPUT,
)

logger = logging.getLogger(__name__)


class TokenBudget:
    def __init__(self, model: str = "gpt-4o") -> None:
        try:
            self._enc = tiktoken.encoding_for_model(model)
        except KeyError:
            self._enc = tiktoken.get_encoding("cl100k_base")
        self.max_atomic_output = TOKEN_MAX_ATOMIC_OUTPUT
        self.dom_soft_cap = TOKEN_DOM_SOFT_CAP

    def count(self, text: str) -> int:
        return len(self._enc.encode(text or ""))

    def truncate_to_cap(self, text: str, cap: int | None = None) -> str:
        limit = cap or self.dom_soft_cap
        tokens = self._enc.encode(text or "")
        if len(tokens) <= limit:
            return text
        return self._enc.decode(tokens[:limit])

    def summarize_history_line(self, text: str) -> str:
        compact = " ".join((text or "").split())
        if len(compact) <= SLIDING_HISTORY_SUMMARY_CHARS:
            return compact
        return compact[: SLIDING_HISTORY_SUMMARY_CHARS - 1] + "…"


class ContextCompressor:
    """In-process headroom + markitdown wrappers (no MCP subprocess)."""

    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()

    def compress_text(self, text: str) -> str:
        try:
            from headroom import crush
        except Exception:
            try:
                import headroom_ai

                crush = getattr(headroom_ai, "crush", None)
            except Exception:
                crush = None
        if callable(crush):
            try:
                result = crush(text)
                if isinstance(result, str):
                    return self.budget.truncate_to_cap(result)
            except Exception as exc:  # noqa: BLE001 — fallback to local truncate
                logger.warning("headroom_compress_failed err=%s", exc)
        return self.budget.truncate_to_cap(text)

    def document_to_markdown(self, path: Path) -> str:
        try:
            from markitdown import MarkItDown
        except Exception as exc:  # noqa: BLE001
            logger.error("markitdown_unavailable err=%s", exc)
            raise RuntimeError("markitdown is required for document conversion") from exc
        md = MarkItDown()
        result = md.convert(str(path))
        text = getattr(result, "text_content", None) or str(result)
        return self.budget.truncate_to_cap(text, cap=8000)

    def sliding_messages(self, messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if not messages:
            return []
        *history, current = messages
        compacted: list[dict[str, Any]] = []
        for item in history:
            content = str(item.get("content") or "")
            compacted.append(
                {
                    "role": item.get("role", "user"),
                    "content": self.budget.summarize_history_line(content),
                }
            )
        compacted.append(current)
        return compacted
