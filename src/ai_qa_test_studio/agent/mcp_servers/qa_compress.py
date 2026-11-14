"""qa-compress — Headroom-style compress / retrieve / stats."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.core.token_budget import ContextCompressor


@dataclass
class QaCompressServer:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    _store: dict[str, str] = field(default_factory=dict)
    _stats: dict[str, int] = field(
        default_factory=lambda: {"compress_calls": 0, "retrieve_calls": 0, "tokens_saved": 0}
    )

    def compress(self, content: str) -> dict[str, Any]:
        original = content or ""
        budget = self.compressor.budget
        before = budget.count(original)
        compressed = self.compressor.compress_text(original)
        after = budget.count(compressed)
        digest = hashlib.sha256(original.encode("utf-8")).hexdigest()[:16]
        self._store[digest] = original
        self._stats["compress_calls"] += 1
        self._stats["tokens_saved"] += max(0, before - after)
        return {
            "compressed": compressed,
            "hash": digest,
            "original_tokens": before,
            "compressed_tokens": after,
            "savings_percent": round(100.0 * (before - after) / before, 1) if before else 0.0,
            "engine": self.compressor.last_engine,
            "bundled_mcp": True,
        }

    def retrieve(self, content_hash: str) -> dict[str, Any]:
        self._stats["retrieve_calls"] += 1
        original = self._store.get(content_hash)
        if original is None:
            return {"found": False, "content": ""}
        return {"found": True, "content": original}

    def stats(self) -> dict[str, Any]:
        return dict(self._stats)

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool == "compress":
            return self.compress(str(args.get("content") or ""))
        if tool == "retrieve":
            return self.retrieve(str(args.get("hash") or ""))
        if tool == "stats":
            return self.stats()
        raise KeyError(f"unknown tool: {tool}")
