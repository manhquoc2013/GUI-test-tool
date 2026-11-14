"""Incremental project file → SQLite index + FTS5 (Hybrid Local-First)."""

from __future__ import annotations

import hashlib
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

from ai_qa_test_studio.constants import (
    PROJECT_ELEMENTS_DIRNAME,
    PROJECT_SUITES_DIRNAME,
    STUDIO_DIRNAME,
    STUDIO_RUNTIME_DB,
)
from ai_qa_test_studio.core.sqlite_db import init_high_speed_db

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class SyncStats:
    updated: int
    skipped: int
    removed: int = 0


@dataclass(frozen=True)
class SearchHit:
    entity_id: str
    entity_type: str
    code: str
    title: str


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


class ProjectIndexSync:
    def __init__(self, project_root: Path) -> None:
        self.project_root = project_root
        self.db_path = project_root / STUDIO_DIRNAME / STUDIO_RUNTIME_DB

    async def sync(self) -> SyncStats:
        conn = await init_high_speed_db(self.db_path)
        updated = 0
        skipped = 0
        seen_ids: set[str] = set()
        try:
            for path in self._iter_indexable_files():
                rel = str(path.relative_to(self.project_root))
                digest = file_sha256(path)
                records = self._parse_file(path, rel)
                for record in records:
                    seen_ids.add(record["id"])
                    cursor = await conn.execute(
                        "SELECT file_hash FROM test_cases_index WHERE id = ?",
                        (record["id"],),
                    )
                    row = await cursor.fetchone()
                    if row is not None and str(row[0]) == digest:
                        skipped += 1
                        continue
                    await conn.execute(
                        """
                        INSERT INTO test_cases_index(
                            id, suite_id, code, title, priority, binding_status,
                            unbound_count, file_path, file_hash
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            suite_id=excluded.suite_id,
                            code=excluded.code,
                            title=excluded.title,
                            priority=excluded.priority,
                            binding_status=excluded.binding_status,
                            unbound_count=excluded.unbound_count,
                            file_path=excluded.file_path,
                            file_hash=excluded.file_hash
                        """,
                        (
                            record["id"],
                            record.get("suite_id") or record["entity_type"],
                            record["code"],
                            record["title"],
                            record.get("priority"),
                            record.get("binding_status"),
                            record.get("unbound_count", 0),
                            rel,
                            digest,
                        ),
                    )
                    await conn.execute(
                        "DELETE FROM search_fts WHERE entity_id = ?",
                        (record["id"],),
                    )
                    await conn.execute(
                        """
                        INSERT INTO search_fts(entity_id, entity_type, code, title, content)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        (
                            record["id"],
                            record["entity_type"],
                            record["code"],
                            record["title"],
                            record.get("content") or "",
                        ),
                    )
                    updated += 1
            removed = await self._prune_missing(conn, seen_ids)
            await conn.commit()
            return SyncStats(updated=updated, skipped=skipped, removed=removed)
        finally:
            await conn.close()

    async def search(self, query: str, *, limit: int = 50) -> list[SearchHit]:
        text = (query or "").strip()
        if not text:
            return []
        conn = await init_high_speed_db(self.db_path)
        try:
            cursor = await conn.execute(
                """
                SELECT entity_id, entity_type, code, title
                FROM search_fts
                WHERE search_fts MATCH ?
                LIMIT ?
                """,
                (text, limit),
            )
            rows = await cursor.fetchall()
            return [
                SearchHit(
                    entity_id=str(row[0]),
                    entity_type=str(row[1]),
                    code=str(row[2] or ""),
                    title=str(row[3] or ""),
                )
                for row in rows
            ]
        finally:
            await conn.close()

    def _iter_indexable_files(self) -> list[Path]:
        files: list[Path] = []
        suites = self.project_root / PROJECT_SUITES_DIRNAME
        elements = self.project_root / PROJECT_ELEMENTS_DIRNAME
        if suites.is_dir():
            files.extend(sorted(suites.rglob("*.yaml")))
        if elements.is_dir():
            files.extend(sorted(elements.rglob("*.yaml")))
        return files

    def _parse_file(self, path: Path, rel: str) -> list[dict[str, Any]]:
        try:
            payload = yaml.safe_load(path.read_text(encoding="utf-8"))
        except (OSError, yaml.YAMLError) as exc:
            logger.warning("index_parse_failed path=%s err=%s", path, exc)
            return []
        if not isinstance(payload, dict):
            return []
        name = path.name
        if name.startswith("scenario_") or name.startswith("suite_"):
            return self._parse_case_container(payload, rel)
        if PROJECT_ELEMENTS_DIRNAME in path.parts:
            return self._parse_element(payload)
        return []

    def _parse_case_container(self, payload: dict[str, Any], rel: str) -> list[dict[str, Any]]:
        suite_id = str(payload.get("id") or rel)
        cases = payload.get("cases") or []
        records: list[dict[str, Any]] = []
        if not isinstance(cases, list):
            return records
        for case in cases:
            if not isinstance(case, dict):
                continue
            case_id = str(case.get("id") or "")
            if not case_id:
                continue
            steps = case.get("steps") or []
            unbound = 0
            if isinstance(steps, list):
                unbound = sum(
                    1
                    for step in steps
                    if isinstance(step, dict)
                    and str(step.get("binding_status") or "").upper() == "UNBOUND"
                )
            title = str(case.get("title") or "")
            code = str(case.get("code") or "")
            records.append(
                {
                    "id": case_id,
                    "entity_type": "case",
                    "suite_id": suite_id,
                    "code": code,
                    "title": title,
                    "priority": case.get("priority"),
                    "binding_status": "UNBOUND" if unbound else "BOUND",
                    "unbound_count": unbound,
                    "content": f"{code} {title}",
                }
            )
        return records

    def _parse_element(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        element_id = str(payload.get("element_id") or "")
        if not element_id:
            return []
        title = str(payload.get("friendly_name") or element_id)
        return [
            {
                "id": element_id,
                "entity_type": "element",
                "suite_id": "element",
                "code": element_id,
                "title": title,
                "binding_status": "BOUND",
                "unbound_count": 0,
                "content": f"{element_id} {title} {payload.get('page_name') or ''}",
            }
        ]

    async def _prune_missing(self, conn: Any, seen_ids: set[str]) -> int:
        cursor = await conn.execute("SELECT id FROM test_cases_index")
        existing = {str(row[0]) for row in await cursor.fetchall()}
        stale = existing - seen_ids
        for entity_id in stale:
            await conn.execute("DELETE FROM test_cases_index WHERE id = ?", (entity_id,))
            await conn.execute("DELETE FROM search_fts WHERE entity_id = ?", (entity_id,))
        # Also prune orphan FTS element rows not in seen_ids
        cursor = await conn.execute("SELECT entity_id FROM search_fts")
        fts_ids = {str(row[0]) for row in await cursor.fetchall()}
        for entity_id in fts_ids - seen_ids:
            await conn.execute("DELETE FROM search_fts WHERE entity_id = ?", (entity_id,))
        return len(stale)
