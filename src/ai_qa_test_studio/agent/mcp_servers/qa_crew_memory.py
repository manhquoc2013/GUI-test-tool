"""qa-crew-memory — compressed artifact handoffs between agents."""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.constants import COPILOT_SUMMARY_MAX_CHARS
from ai_qa_test_studio.core.token_budget import ContextCompressor


@dataclass
class _Artifact:
    role: str
    kind: str
    full: str
    summary: str


@dataclass
class QaCrewMemoryServer:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    _items: dict[str, _Artifact] = field(default_factory=dict)

    def put_artifact(self, role: str, kind: str, text: str) -> dict[str, Any]:
        full = text or ""
        compressed = self.compressor.compress_text(full)
        if len(compressed) <= COPILOT_SUMMARY_MAX_CHARS:
            summary = compressed
        else:
            summary = compressed[: COPILOT_SUMMARY_MAX_CHARS - 1].rstrip() + "…"
        ref = str(uuid.uuid4())
        self._items[ref] = _Artifact(role=role, kind=kind, full=full, summary=summary)
        return {"ref": ref, "summary": summary, "role": role, "kind": kind}

    def get_summary(self, ref: str) -> dict[str, Any]:
        item = self._items.get(ref)
        if item is None:
            return {"found": False, "summary": ""}
        return {"found": True, "summary": item.summary, "role": item.role, "kind": item.kind}

    def get_full(self, ref: str) -> dict[str, Any]:
        item = self._items.get(ref)
        if item is None:
            return {"found": False, "content": ""}
        return {"found": True, "content": item.full, "role": item.role, "kind": item.kind}

    def clear(self) -> None:
        self._items.clear()

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool == "put_artifact":
            return self.put_artifact(
                str(args.get("role") or ""),
                str(args.get("kind") or "text"),
                str(args.get("text") or ""),
            )
        if tool == "get_summary":
            return self.get_summary(str(args.get("ref") or ""))
        if tool == "get_full":
            return self.get_full(str(args.get("ref") or ""))
        raise KeyError(f"unknown tool: {tool}")
