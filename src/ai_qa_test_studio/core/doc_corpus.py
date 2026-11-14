"""Large-document corpus: full ingest + FTS5 on-demand retrieve (offline)."""

from __future__ import annotations

import hashlib
import logging
import re
import sqlite3
import time
import uuid
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import (
    DOC_CHUNK_OVERLAP_CHARS,
    DOC_CHUNK_SIZE_CHARS,
    DOC_CORPUS_CACHE_DIRNAME,
    DOC_CORPUS_DB_NAME,
    DOC_CORPUS_SQLITE_TIMEOUT,
    DOC_CORPUS_TMP_DB_NAME,
    DOC_CORPUS_TMP_TTL_DAYS,
    DOC_EXCEL_ROWS_PER_CHUNK,
    DOC_OVERVIEW_CHUNK_COUNT,
    DOC_RETRIEVE_MAX_CHARS,
    DOC_RETRIEVE_TOP_K,
    STUDIO_DIRNAME,
    WIZARD_DOC_MAX_FILE_BYTES,
    WIZARD_DOC_MAX_SHEETS,
    WIZARD_DOC_XLSX_SUFFIXES,
)

logger = logging.getLogger(__name__)

_SCHEMA = """
CREATE TABLE IF NOT EXISTS documents (
    doc_id TEXT PRIMARY KEY,
    rel_path TEXT NOT NULL,
    file_hash TEXT NOT NULL,
    title TEXT NOT NULL,
    total_chunks INT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
    doc_id UNINDEXED,
    chunk_index UNINDEXED,
    section_meta,
    content,
    tokenize='unicode61 remove_diacritics 2'
);
"""

_FTS_SAFE_TOKEN = re.compile(r"[^\w\-]+", re.UNICODE)
_FTS_STOP = frozenset({"and", "or", "not", "near"})


@dataclass(frozen=True)
class ChunkHit:
    doc_id: str
    chunk_index: int
    section_meta: str
    content: str
    score: float = 0.0


@dataclass(frozen=True)
class IngestResult:
    doc_id: str
    total_chunks: int
    reused: bool
    title: str


def corpus_db_path(project_root: Path | str | None) -> Path:
    if project_root:
        studio = Path(project_root).expanduser().resolve() / STUDIO_DIRNAME
        studio.mkdir(parents=True, exist_ok=True)
        return studio / DOC_CORPUS_DB_NAME
    cache = Path.home() / ".cache" / DOC_CORPUS_CACHE_DIRNAME
    cache.mkdir(parents=True, exist_ok=True)
    return cache / DOC_CORPUS_TMP_DB_NAME


def sanitize_fts_query(raw: str) -> str:
    """Build a safe FTS5 MATCH expression: quoted tokens joined by OR."""
    text = (raw or "").strip().lower()
    if not text:
        return ""
    parts = _FTS_SAFE_TOKEN.sub(" ", text).split()
    tokens: list[str] = []
    for part in parts:
        token = part.strip("-_")
        if len(token) < 2:
            continue
        if token in _FTS_STOP:
            continue
        escaped = token.replace('"', "")
        if not escaped:
            continue
        tokens.append(f'"{escaped}"')
    return " OR ".join(tokens)


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            block = handle.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def chunk_prose(
    text: str,
    *,
    size: int = DOC_CHUNK_SIZE_CHARS,
    overlap: int = DOC_CHUNK_OVERLAP_CHARS,
) -> list[tuple[str, str]]:
    body = (text or "").strip()
    if not body:
        return []
    size = max(200, size)
    overlap = max(0, min(overlap, size // 2))
    chunks: list[tuple[str, str]] = []
    start = 0
    index = 0
    while start < len(body):
        end = min(len(body), start + size)
        piece = body[start:end].strip()
        if piece:
            chunks.append((f"prose#{index}", piece))
            index += 1
        if end >= len(body):
            break
        start = max(end - overlap, start + 1)
    return chunks


def _iso_now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds")


def _connect(db_path: Path, *, is_tmp: bool) -> sqlite3.Connection:
    conn = sqlite3.connect(str(db_path), timeout=DOC_CORPUS_SQLITE_TIMEOUT)
    conn.row_factory = sqlite3.Row
    conn.execute(f"PRAGMA busy_timeout={int(DOC_CORPUS_SQLITE_TIMEOUT * 1000)}")
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.executescript(_SCHEMA)
    if is_tmp:
        _cleanup_tmp(conn)
    return conn


def _cleanup_tmp(conn: sqlite3.Connection) -> None:
    cutoff = time.time() - (DOC_CORPUS_TMP_TTL_DAYS * 24 * 3600)
    rows = conn.execute("SELECT doc_id, updated_at FROM documents").fetchall()
    stale: list[str] = []
    for row in rows:
        try:
            stamp = datetime.fromisoformat(str(row["updated_at"]))
            if stamp.tzinfo is None:
                stamp = stamp.replace(tzinfo=UTC)
            if stamp.timestamp() < cutoff:
                stale.append(str(row["doc_id"]))
        except ValueError:
            stale.append(str(row["doc_id"]))
    for doc_id in stale:
        conn.execute("DELETE FROM chunks_fts WHERE doc_id = ?", (doc_id,))
        conn.execute("DELETE FROM documents WHERE doc_id = ?", (doc_id,))
    if stale:
        conn.commit()


def _xlsx_chunks(path: Path) -> list[tuple[str, str]]:
    from openpyxl import load_workbook

    wb = load_workbook(path, read_only=True, data_only=True)
    out: list[tuple[str, str]] = []
    try:
        for sheet_name in list(wb.sheetnames)[:WIZARD_DOC_MAX_SHEETS]:
            ws = wb[sheet_name]
            rows_iter = ws.iter_rows(values_only=True)
            try:
                header = next(rows_iter)
            except StopIteration:
                continue
            header_cells = [
                "" if cell is None else str(cell).strip() for cell in header
            ]
            header_line = " | ".join(h for h in header_cells if h)
            batch: list[str] = []
            batch_start = 1
            row_no = 0
            for values in rows_iter:
                row_no += 1
                if values is None or all(v is None or str(v).strip() == "" for v in values):
                    continue
                cells = ["" if v is None else str(v).strip() for v in values]
                batch.append(" | ".join(cells))
                if len(batch) >= DOC_EXCEL_ROWS_PER_CHUNK:
                    meta = f"sheet={sheet_name};rows={batch_start}-{row_no};header={header_line}"
                    out.append((meta, "\n".join(batch)))
                    batch = []
                    batch_start = row_no + 1
            if batch:
                meta = f"sheet={sheet_name};rows={batch_start}-{row_no};header={header_line}"
                out.append((meta, "\n".join(batch)))
    finally:
        wb.close()
    return out


def _load_text_chunks(path: Path) -> list[tuple[str, str]]:
    suffix = path.suffix.lower()
    if suffix in WIZARD_DOC_XLSX_SUFFIXES:
        return _xlsx_chunks(path)
    if suffix in {".md", ".markdown", ".txt", ".csv", ".tsv", ".html", ".htm"}:
        text = path.read_text(encoding="utf-8", errors="replace")
        return chunk_prose(text)
    try:
        from markitdown import MarkItDown

        result = MarkItDown().convert(str(path))
        md = getattr(result, "text_content", None) or str(result)
    except Exception as exc:  # noqa: BLE001
        logger.warning("doc_corpus_convert_failed path=%s err=%s", path, exc)
        raise RuntimeError(f"cannot convert document: {path.name}: {exc}") from exc
    return chunk_prose(md)


def ingest_path(project_root: Path | str | None, path: Path | str) -> IngestResult:
    target = Path(path).expanduser().resolve()
    if not target.is_file():
        raise FileNotFoundError(str(target))
    size = target.stat().st_size
    if size > WIZARD_DOC_MAX_FILE_BYTES:
        raise ValueError(f"file exceeds {WIZARD_DOC_MAX_FILE_BYTES} bytes")

    db_path = corpus_db_path(project_root)
    is_tmp = project_root is None
    digest = file_sha256(target)
    title = target.name
    rel = str(target)

    conn = _connect(db_path, is_tmp=is_tmp)
    try:
        existing = conn.execute(
            "SELECT doc_id, file_hash, total_chunks FROM documents WHERE rel_path = ?",
            (rel,),
        ).fetchone()
        if existing and str(existing["file_hash"]) == digest:
            return IngestResult(
                doc_id=str(existing["doc_id"]),
                total_chunks=int(existing["total_chunks"]),
                reused=True,
                title=title,
            )

        pieces = _load_text_chunks(target)
        if not pieces:
            pieces = [("empty", title)]

        doc_id = str(existing["doc_id"]) if existing else str(uuid.uuid4())
        if existing:
            conn.execute("DELETE FROM chunks_fts WHERE doc_id = ?", (doc_id,))
            conn.execute("DELETE FROM documents WHERE doc_id = ?", (doc_id,))

        now = _iso_now()
        conn.execute(
            "INSERT INTO documents(doc_id, rel_path, file_hash, title, total_chunks, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (doc_id, rel, digest, title, len(pieces), now),
        )
        for index, (meta, content) in enumerate(pieces):
            conn.execute(
                "INSERT INTO chunks_fts(doc_id, chunk_index, section_meta, content) "
                "VALUES (?, ?, ?, ?)",
                (doc_id, index, meta, content),
            )
        conn.commit()
        return IngestResult(
            doc_id=doc_id,
            total_chunks=len(pieces),
            reused=False,
            title=title,
        )
    finally:
        conn.close()


def _overview_hits(
    conn: sqlite3.Connection,
    doc_ids: Sequence[str],
    *,
    max_chars: int,
) -> list[ChunkHit]:
    hits: list[ChunkHit] = []
    used = 0
    for doc_id in doc_ids:
        rows = conn.execute(
            "SELECT doc_id, chunk_index, section_meta, content FROM chunks_fts "
            "WHERE doc_id = ? AND chunk_index < ? ORDER BY chunk_index ASC",
            (doc_id, DOC_OVERVIEW_CHUNK_COUNT),
        ).fetchall()
        for row in rows:
            content = str(row["content"] or "")
            if used + len(content) > max_chars and hits:
                return hits
            hits.append(
                ChunkHit(
                    doc_id=str(row["doc_id"]),
                    chunk_index=int(row["chunk_index"]),
                    section_meta=str(row["section_meta"] or ""),
                    content=content,
                    score=0.0,
                )
            )
            used += len(content)
    return hits


def retrieve(
    project_root: Path | str | None,
    query: str,
    *,
    doc_ids: Sequence[str] | None = None,
    limit: int = DOC_RETRIEVE_TOP_K,
    max_chars: int = DOC_RETRIEVE_MAX_CHARS,
) -> list[ChunkHit]:
    db_path = corpus_db_path(project_root)
    if not db_path.is_file():
        return []
    is_tmp = project_root is None
    conn = _connect(db_path, is_tmp=is_tmp)
    try:
        scoped = [d for d in (doc_ids or []) if d]
        match = sanitize_fts_query(query)
        lim = max(1, min(int(limit), 50))
        max_chars = max(500, int(max_chars))

        hits: list[ChunkHit] = []
        if match:
            sql = (
                "SELECT doc_id, chunk_index, section_meta, content, "
                "bm25(chunks_fts) AS score FROM chunks_fts WHERE chunks_fts MATCH ?"
            )
            params: list[Any] = [match]
            if scoped:
                placeholders = ",".join("?" for _ in scoped)
                sql += f" AND doc_id IN ({placeholders})"
                params.extend(scoped)
            sql += " ORDER BY score LIMIT ?"
            params.append(lim)
            try:
                rows = conn.execute(sql, params).fetchall()
            except sqlite3.OperationalError as exc:
                logger.warning("fts_match_failed query=%r err=%s", match, exc)
                rows = []
            used = 0
            for row in rows:
                content = str(row["content"] or "")
                if used + len(content) > max_chars and hits:
                    break
                hits.append(
                    ChunkHit(
                        doc_id=str(row["doc_id"]),
                        chunk_index=int(row["chunk_index"]),
                        section_meta=str(row["section_meta"] or ""),
                        content=content,
                        score=float(row["score"] or 0.0),
                    )
                )
                used += len(content)

        if not hits and scoped:
            return _overview_hits(conn, scoped, max_chars=max_chars)
        return hits
    finally:
        conn.close()


def format_retrieved_block(hits: list[ChunkHit]) -> str:
    if not hits:
        return ""
    parts: list[str] = []
    for hit in hits:
        meta = hit.section_meta or f"chunk#{hit.chunk_index}"
        parts.append(f"### doc={hit.doc_id} | {meta}\n{hit.content}")
    return "RETRIEVED:\n" + "\n\n".join(parts)
