"""qa-ingest — MarkItDown document → markdown (bundled MCP, project-sandboxed)."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.core.token_budget import ContextCompressor


@dataclass
class QaIngestServer:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    project_root: Path | None = None

    def bind_project(self, project_root: Path | str | None) -> None:
        self.project_root = Path(project_root).resolve() if project_root else None

    def document_to_markdown(self, path: str) -> dict[str, Any]:
        if self.project_root is None:
            return {"ok": False, "markdown": "", "error": "project root unbound"}
        try:
            target = Path(path).expanduser().resolve()
        except OSError as exc:
            return {"ok": False, "markdown": "", "error": str(exc)}
        try:
            target.relative_to(self.project_root)
        except ValueError:
            return {
                "ok": False,
                "markdown": "",
                "error": f"path outside project root: {path}",
            }
        if not target.is_file():
            return {"ok": False, "markdown": "", "error": f"file not found: {path}"}
        if target.is_symlink():
            try:
                target.resolve().relative_to(self.project_root)
            except ValueError:
                return {"ok": False, "markdown": "", "error": "symlink escapes project root"}
        try:
            markdown = self.compressor.document_to_markdown(target)
        except Exception as exc:  # noqa: BLE001
            return {"ok": False, "markdown": "", "error": str(exc)}
        return {
            "ok": True,
            "markdown": markdown,
            "path": str(target),
            "chars": len(markdown),
        }

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool in {"document_to_markdown", "ingest"}:
            return self.document_to_markdown(str(args.get("path") or ""))
        raise KeyError(f"unknown tool: {tool}")
