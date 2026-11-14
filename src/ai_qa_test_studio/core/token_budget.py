"""Token budget, sliding context, in-process headroom + markitdown."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

import tiktoken

from ai_qa_test_studio.constants import (
    COMPACTOR_CREW_DRAFT_ROLES,
    COMPACTOR_CREW_FINAL_ROLES,
    COMPACTOR_KEEP_LAST_TOOL_PAIRS,
    COMPACTOR_PIN_PREFIX,
    COMPACTOR_PIN_SUFFIX,
    COMPACTOR_SNAPSHOT_MARKER,
    COMPACTOR_SNAPSHOT_STUB_FROM_AGE,
    COMPACTOR_SNAPSHOT_STUB_TEMPLATE,
    COMPACTOR_SYSTEM_ROLES,
    COMPACTOR_TESTCASE_MARKERS,
    COMPACTOR_TOOL_CONTENT_MARKERS,
    COMPACTOR_TOOL_ROLE_NAMES,
    ROLE_COMPOSER,
    SLIDING_HISTORY_SUMMARY_CHARS,
    TOKEN_CONTEXT_BUDGET,
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
    """In-process headroom + markitdown used by bundled MCP tools."""

    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()
        self.last_engine = "truncate"

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
                    self.last_engine = "headroom"
                    return self.budget.truncate_to_cap(result)
            except Exception as exc:  # noqa: BLE001 — fallback to local truncate
                logger.warning("headroom_compress_failed err=%s", exc)
        self.last_engine = "truncate"
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
        return self.budget.truncate_to_cap(text, cap=TOKEN_CONTEXT_BUDGET)

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


class ContextCompactor:
    """Pin System+TestCase, latest full browser_snapshot, and last N tool pairs.

    Older ``browser_snapshot`` payloads (from t-2 backward) are stubbed; other
    history is summarized / compressed under budget.
    """

    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()
        self._inner = ContextCompressor(self.budget)

    @staticmethod
    def pin(text: str, label: str = "ctx") -> str:
        safe = (label or "ctx").strip() or "ctx"
        return f"{COMPACTOR_PIN_PREFIX}{safe}{COMPACTOR_PIN_SUFFIX}\n{text}"

    @staticmethod
    def is_pinned(content: str) -> bool:
        return (content or "").lstrip().startswith(COMPACTOR_PIN_PREFIX)

    @staticmethod
    def _is_system(role: str, content: str) -> bool:
        if role.lower() in COMPACTOR_SYSTEM_ROLES:
            return True
        return ContextCompactor.is_pinned(content) and "system" in content.lstrip()[
            : len(COMPACTOR_PIN_PREFIX) + 16
        ].lower()

    @staticmethod
    def _is_testcase(content: str) -> bool:
        head = (content or "").lstrip().lower()
        if ContextCompactor.is_pinned(content):
            return any(m in head[:80] for m in ("testcase", "test_case", "case"))
        return any(m in head[:120] for m in COMPACTOR_TESTCASE_MARKERS)

    @staticmethod
    def _is_snapshot(content: str) -> bool:
        return COMPACTOR_SNAPSHOT_MARKER in (content or "").lower()

    @staticmethod
    def _is_tool_message(role: str, content: str) -> bool:
        if role.lower() in COMPACTOR_TOOL_ROLE_NAMES:
            return True
        lowered = (content or "").lower()
        return any(marker in lowered for marker in COMPACTOR_TOOL_CONTENT_MARKERS)

    @staticmethod
    def _snapshot_stub(age: int) -> str:
        return COMPACTOR_SNAPSHOT_STUB_TEMPLATE.format(
            marker=COMPACTOR_SNAPSHOT_MARKER,
            age=age,
        )

    def drop_intermediate_crew_drafts(
        self,
        messages: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        """Keep user + final Composer result; drop Planner/Critic/mid-draft bubbles."""
        if not messages:
            return []
        last_final_idx: int | None = None
        for idx in range(len(messages) - 1, -1, -1):
            role = str(messages[idx].get("role") or "").lower()
            content = str(messages[idx].get("content") or "")
            if role == ROLE_COMPOSER or (
                role in COMPACTOR_CREW_FINAL_ROLES and '"scenarios"' in content
            ):
                last_final_idx = idx
                break
        if last_final_idx is None:
            for idx in range(len(messages) - 1, -1, -1):
                role = str(messages[idx].get("role") or "").lower()
                if role in COMPACTOR_CREW_FINAL_ROLES or role == ROLE_COMPOSER:
                    last_final_idx = idx
                    break
        if last_final_idx is None:
            return list(messages)
        out: list[dict[str, Any]] = []
        for idx, item in enumerate(messages):
            role = str(item.get("role") or "").lower()
            if role == "user" or self._is_system(role, str(item.get("content") or "")):
                out.append(item)
                continue
            if role in COMPACTOR_CREW_DRAFT_ROLES and idx != last_final_idx:
                continue
            if idx == last_final_idx or role not in COMPACTOR_CREW_DRAFT_ROLES:
                out.append(item)
        return out

    def compact_messages(
        self,
        messages: list[dict[str, Any]],
        *,
        soft_cap: int | None = None,
    ) -> list[dict[str, Any]]:
        if not messages:
            return []
        messages = self.drop_intermediate_crew_drafts(messages)
        cap = soft_cap or TOKEN_CONTEXT_BUDGET
        *history, current = messages

        snapshot_idxs = [
            i for i, item in enumerate(history) if self._is_snapshot(str(item.get("content") or ""))
        ]
        # age 0 = latest; stub when age >= COMPACTOR_SNAPSHOT_STUB_FROM_AGE (t-2+)
        snapshot_age: dict[int, int] = {
            snap_i: len(snapshot_idxs) - 1 - pos for pos, snap_i in enumerate(snapshot_idxs)
        }
        keep_snapshot = {
            snap_i
            for snap_i, age in snapshot_age.items()
            if age < COMPACTOR_SNAPSHOT_STUB_FROM_AGE
        }

        tool_idxs = [
            i
            for i, item in enumerate(history)
            if self._is_tool_message(
                str(item.get("role") or "user"),
                str(item.get("content") or ""),
            )
            and not self._is_snapshot(str(item.get("content") or ""))
        ]
        keep_tool = set(tool_idxs[-COMPACTOR_KEEP_LAST_TOOL_PAIRS :])

        out: list[dict[str, Any]] = []
        for idx, item in enumerate(history):
            role = str(item.get("role") or "user")
            content = str(item.get("content") or "")
            if self._is_snapshot(content) and idx not in keep_snapshot:
                age = snapshot_age.get(idx, COMPACTOR_SNAPSHOT_STUB_FROM_AGE)
                out.append({"role": role, "content": self._snapshot_stub(age)})
                continue
            keep_full = (
                self.is_pinned(content)
                or self._is_system(role, content)
                or self._is_testcase(content)
                or idx in keep_snapshot
                or idx in keep_tool
            )
            if keep_full:
                out.append({"role": role, "content": content})
                continue
            out.append(
                {
                    "role": role,
                    "content": self.budget.summarize_history_line(content),
                }
            )
        out.append(current)

        blob = "\n".join(str(m.get("content") or "") for m in out)
        if self.budget.count(blob) <= cap:
            return out

        trimmed: list[dict[str, Any]] = []
        for idx, item in enumerate(out):
            content = str(item.get("content") or "")
            role = str(item.get("role") or "user")
            if (
                idx == len(out) - 1
                or self.is_pinned(content)
                or self._is_system(role, content)
                or self._is_testcase(content)
                or self._is_snapshot(content)
                or self._is_tool_message(role, content)
            ):
                trimmed.append(item)
            else:
                trimmed.append(
                    {
                        "role": role,
                        "content": self._inner.compress_text(content),
                    }
                )
        return trimmed
