"""qa-ingest — MarkItDown document → markdown (bundled MCP)."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.core.token_budget import ContextCompressor


@dataclass
class QaIngestServer:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)

    def document_to_markdown(self, path: str) -> dict[str, Any]:
        target = Path(path)
        if not target.is_file():
            return {"ok": False, "markdown": "", "error": f"file not found: {path}"}
        try:
            markdown = self.compressor.document_to_markdown(target)
        except Exception as exc:  # noqa: BLE001
            return {"ok": False, "markdown": "", "error": str(exc)}
        return {
            "ok": True,
            "markdown": markdown,
            "path": str(target.resolve()),
            "chars": len(markdown),
        }

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool in {"document_to_markdown", "ingest"}:
            return self.document_to_markdown(str(args.get("path") or ""))
        raise KeyError(f"unknown tool: {tool}")
