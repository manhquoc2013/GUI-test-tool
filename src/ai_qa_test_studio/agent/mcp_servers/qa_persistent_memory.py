"""qa-persistent-memory — local notes under project .studio/agent_memory.db."""

from __future__ import annotations

import sqlite3
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import STUDIO_DIRNAME

_MEMORY_DB_NAME = "agent_memory.db"
_SCHEMA = """
CREATE TABLE IF NOT EXISTS notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domain TEXT NOT NULL,
    note TEXT NOT NULL,
    created_at REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notes_domain ON notes(domain);
"""


@dataclass
class QaPersistentMemoryServer:
    project_root: Path | None = None
    _conn: sqlite3.Connection | None = field(default=None, init=False, repr=False)

    def bind_project(self, project_root: Path | str | None) -> None:
        if self._conn is not None:
            self._conn.close()
            self._conn = None
        self.project_root = Path(project_root).resolve() if project_root else None

    def _db_path(self) -> Path:
        if self.project_root is None:
            raise RuntimeError("project root unbound")
        studio = self.project_root / STUDIO_DIRNAME
        studio.mkdir(parents=True, exist_ok=True)
        return studio / _MEMORY_DB_NAME

    def _ensure(self) -> sqlite3.Connection:
        if self.project_root is None:
            raise RuntimeError("project root unbound")
        if self._conn is None:
            path = self._db_path()
            self._conn = sqlite3.connect(str(path))
            self._conn.row_factory = sqlite3.Row
            self._conn.executescript(_SCHEMA)
            self._conn.commit()
        return self._conn

    @staticmethod
    def _coerce_limit(limit: object, default: int = 20) -> int:
        value = default
        if isinstance(limit, bool):
            value = default
        elif isinstance(limit, int):
            value = limit
        elif isinstance(limit, str):
            try:
                value = int(limit.strip())
            except ValueError:
                value = default
        return max(1, min(value if value else default, 100))

    def put_note(self, domain: str, note: str) -> dict[str, Any]:
        if self.project_root is None:
            return {"ok": False, "error": "project root unbound"}
        domain_key = (domain or "default").strip() or "default"
        text = (note or "").strip()
        if not text:
            return {"ok": False, "error": "empty note"}
        conn = self._ensure()
        cur = conn.execute(
            "INSERT INTO notes(domain, note, created_at) VALUES (?, ?, ?)",
            (domain_key, text, time.time()),
        )
        conn.commit()
        row_id = cur.lastrowid
        return {"ok": True, "id": int(row_id or 0), "domain": domain_key}

    def get_notes(self, domain: str = "", limit: int = 20) -> dict[str, Any]:
        if self.project_root is None:
            return {"ok": False, "notes": [], "count": 0, "error": "project root unbound"}
        conn = self._ensure()
        lim = self._coerce_limit(limit)
        if domain:
            rows = conn.execute(
                "SELECT id, domain, note, created_at FROM notes WHERE domain = ? "
                "ORDER BY id DESC LIMIT ?",
                (domain, lim),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT id, domain, note, created_at FROM notes ORDER BY id DESC LIMIT ?",
                (lim,),
            ).fetchall()
        notes = [
            {
                "id": int(row["id"]),
                "domain": str(row["domain"]),
                "note": str(row["note"]),
                "created_at": float(row["created_at"]),
            }
            for row in rows
        ]
        return {"ok": True, "notes": notes, "count": len(notes)}

    def search_notes(self, query: str, limit: int = 20) -> dict[str, Any]:
        if self.project_root is None:
            return {"ok": False, "notes": [], "count": 0, "error": "project root unbound"}
        needle = (query or "").strip()
        if not needle:
            return {"ok": True, "notes": [], "count": 0}
        conn = self._ensure()
        lim = self._coerce_limit(limit)
        rows = conn.execute(
            "SELECT id, domain, note, created_at FROM notes WHERE note LIKE ? "
            "ORDER BY id DESC LIMIT ?",
            (f"%{needle}%", lim),
        ).fetchall()
        notes = [
            {
                "id": int(row["id"]),
                "domain": str(row["domain"]),
                "note": str(row["note"]),
                "created_at": float(row["created_at"]),
            }
            for row in rows
        ]
        return {"ok": True, "notes": notes, "count": len(notes)}

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool == "put_note":
            return self.put_note(str(args.get("domain") or ""), str(args.get("note") or ""))
        if tool == "get_notes":
            return self.get_notes(
                str(args.get("domain") or ""), self._coerce_limit(args.get("limit"), 20)
            )
        if tool == "search_notes":
            return self.search_notes(
                str(args.get("query") or ""), self._coerce_limit(args.get("limit"), 20)
            )
        raise KeyError(f"unknown tool: {tool}")

    def close(self) -> None:
        if self._conn is not None:
            self._conn.close()
            self._conn = None
