"""Coverage pass: section-map a large corpus doc and process every section."""

from __future__ import annotations

import re
import sqlite3
from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from pathlib import Path

from ai_qa_test_studio.constants import (
    DOC_COVERAGE_CHUNKS_PER_SECTION,
    DOC_COVERAGE_MAX_SECTIONS,
    DOC_COVERAGE_MIN_CHUNKS,
    DOC_COVERAGE_SECTION_MAX_CHARS,
    INTENT_COMPOSE,
)
from ai_qa_test_studio.core.doc_corpus import corpus_db_path

_HEADING_RE = re.compile(r"^(#{1,3})\s+(.+?)\s*$", re.MULTILINE)
_SHEET_RE = re.compile(r"sheet=([^;]+)", re.IGNORECASE)

ChunkKey = tuple[str, int]


@dataclass(frozen=True)
class DocSection:
    section_id: str
    title: str
    doc_id: str
    chunk_indexes: tuple[int, ...]


@dataclass(frozen=True)
class ChunkRow:
    doc_id: str
    chunk_index: int
    section_meta: str
    content: str


@dataclass(frozen=True)
class SectionWindow:
    """One LLM-sized slice of a section — never drops later chunks."""

    section_id: str
    title: str
    doc_id: str
    window_index: int
    window_count: int
    chunk_indexes: tuple[int, ...]
    body: str


@dataclass(frozen=True)
class CoveragePlan:
    """Precomputed section windows from a single corpus load."""

    sections: tuple[DocSection, ...]
    section_windows: tuple[tuple[DocSection, tuple[SectionWindow, ...]], ...]
    total_chunks: int
    covered_chunks: int
    total_windows: int


def should_run_coverage(*, intent: str, total_chunks: int) -> bool:
    return intent == INTENT_COMPOSE and total_chunks >= DOC_COVERAGE_MIN_CHUNKS


def sum_document_chunks(
    project_root: Path | str | None, doc_ids: Sequence[str]
) -> int:
    ids = [d for d in doc_ids if d]
    if not ids:
        return 0
    db_path = corpus_db_path(project_root)
    if not db_path.is_file():
        return 0
    conn = sqlite3.connect(str(db_path), timeout=5.0)
    try:
        placeholders = ",".join("?" for _ in ids)
        row = conn.execute(
            f"SELECT COALESCE(SUM(total_chunks), 0) FROM documents "
            f"WHERE doc_id IN ({placeholders})",
            list(ids),
        ).fetchone()
        return int(row[0] if row else 0)
    finally:
        conn.close()


def load_chunks(
    project_root: Path | str | None, doc_ids: Sequence[str]
) -> list[ChunkRow]:
    ids = [d for d in doc_ids if d]
    if not ids:
        return []
    db_path = corpus_db_path(project_root)
    if not db_path.is_file():
        return []
    conn = sqlite3.connect(str(db_path), timeout=5.0)
    conn.row_factory = sqlite3.Row
    try:
        placeholders = ",".join("?" for _ in ids)
        rows = conn.execute(
            f"SELECT doc_id, chunk_index, section_meta, content FROM chunks_fts "
            f"WHERE doc_id IN ({placeholders}) ORDER BY doc_id, chunk_index ASC",
            list(ids),
        ).fetchall()
        return [
            ChunkRow(
                doc_id=str(r["doc_id"]),
                chunk_index=int(r["chunk_index"]),
                section_meta=str(r["section_meta"] or ""),
                content=str(r["content"] or ""),
            )
            for r in rows
        ]
    finally:
        conn.close()


def _title_for_chunk(chunk: ChunkRow) -> str | None:
    meta = chunk.section_meta or ""
    sheet = _SHEET_RE.search(meta)
    if sheet:
        return f"Sheet: {sheet.group(1).strip()}"
    match = _HEADING_RE.search(chunk.content or "")
    if match:
        return match.group(2).strip()[:120]
    if meta.startswith("prose#"):
        return None
    if meta.strip():
        return meta.strip()[:120]
    return None


def build_section_map_from_chunks(
    chunks: Sequence[ChunkRow],
    *,
    max_sections: int = DOC_COVERAGE_MAX_SECTIONS,
    chunks_per_section: int = DOC_COVERAGE_CHUNKS_PER_SECTION,
) -> list[DocSection]:
    if not chunks:
        return []

    max_sections = max(1, int(max_sections))
    pack = max(1, chunks_per_section)
    raw: list[DocSection] = []
    current_title = "Mở đầu"
    current_doc = chunks[0].doc_id
    current_indexes: list[int] = []
    section_no = 0

    def flush() -> None:
        nonlocal section_no, current_indexes, current_title
        if not current_indexes:
            return
        section_no += 1
        raw.append(
            DocSection(
                section_id=f"S{section_no:02d}",
                title=current_title or f"Mục {section_no}",
                doc_id=current_doc,
                chunk_indexes=tuple(current_indexes),
            )
        )
        current_indexes = []

    for chunk in chunks:
        boundary = False
        title = _title_for_chunk(chunk)
        if chunk.doc_id != current_doc:
            flush()
            current_doc = chunk.doc_id
            current_title = title or f"Tài liệu {chunk.doc_id[:8]}"
            boundary = True
        elif title and (
            not current_indexes
            or title != current_title
            or (chunk.section_meta.startswith("sheet=") and title != current_title)
        ):
            if current_indexes:
                flush()
            current_title = title
            boundary = True
        elif len(current_indexes) >= pack:
            flush()
            current_title = title or f"Tiếp · chunk {chunk.chunk_index}"
            boundary = True

        if not current_indexes and not boundary and title:
            current_title = title
        if not current_indexes and not title:
            current_title = current_title or f"Mục chunk {chunk.chunk_index}"
        current_indexes.append(chunk.chunk_index)

    flush()

    if len(raw) <= max_sections:
        return raw
    merged: list[DocSection] = []
    group = max(1, (len(raw) + max_sections - 1) // max_sections)
    for i in range(0, len(raw), group):
        batch = raw[i : i + group]
        indexes: list[int] = []
        for item in batch:
            indexes.extend(item.chunk_indexes)
        merged.append(
            DocSection(
                section_id=f"S{len(merged) + 1:02d}",
                title=(
                    batch[0].title
                    if len(batch) == 1
                    else f"{batch[0].title} (+{len(batch) - 1})"
                ),
                doc_id=batch[0].doc_id,
                chunk_indexes=tuple(indexes),
            )
        )
    # Never silently drop sections (former merged[:max_sections] trap).
    if len(merged) > max_sections:
        raise RuntimeError(
            f"section merge produced {len(merged)} > max_sections={max_sections}"
        )
    return merged


def build_section_map(
    project_root: Path | str | None,
    doc_ids: Sequence[str],
    *,
    max_sections: int = DOC_COVERAGE_MAX_SECTIONS,
    chunks_per_section: int = DOC_COVERAGE_CHUNKS_PER_SECTION,
) -> list[DocSection]:
    return build_section_map_from_chunks(
        load_chunks(project_root, doc_ids),
        max_sections=max_sections,
        chunks_per_section=chunks_per_section,
    )


def _section_chunks_from_index(
    section: DocSection,
    by_key: dict[ChunkKey, ChunkRow],
) -> list[ChunkRow]:
    rows: list[ChunkRow] = []
    for index in section.chunk_indexes:
        chunk = by_key.get((section.doc_id, index))
        if chunk is None:
            continue
        if not (chunk.content or "").strip():
            continue
        rows.append(chunk)
    return rows


def _pack_windows(
    section: DocSection,
    chunks: Sequence[ChunkRow],
    *,
    max_chars: int,
) -> list[SectionWindow]:
    if not chunks:
        return []
    max_chars = max(500, int(max_chars))
    packs: list[list[ChunkRow]] = []
    current: list[ChunkRow] = []
    used = 0
    for chunk in chunks:
        piece = chunk.content.strip()
        extra = len(piece) + (2 if current else 0)
        if current and used + extra > max_chars:
            packs.append(current)
            current = []
            used = 0
            extra = len(piece)
        current.append(chunk)
        used += extra
        if len(current) == 1 and used > max_chars:
            packs.append(current)
            current = []
            used = 0
    if current:
        packs.append(current)

    total = len(packs)
    windows: list[SectionWindow] = []
    for index, pack in enumerate(packs, start=1):
        indexes = tuple(c.chunk_index for c in pack)
        body_parts = [c.content.strip() for c in pack]
        header = (
            f"## Coverage section {section.section_id}: {section.title} "
            f"(window {index}/{total})\n"
        )
        windows.append(
            SectionWindow(
                section_id=section.section_id,
                title=section.title,
                doc_id=section.doc_id,
                window_index=index,
                window_count=total,
                chunk_indexes=indexes,
                body=header + "\n\n".join(body_parts),
            )
        )
    return windows


def iter_section_windows(
    project_root: Path | str | None,
    section: DocSection,
    *,
    max_chars: int = DOC_COVERAGE_SECTION_MAX_CHARS,
    chunks: Sequence[ChunkRow] | None = None,
) -> Iterator[SectionWindow]:
    """Yield LLM windows that together cover every non-empty chunk in the section."""
    if chunks is None:
        rows = load_chunks(project_root, [section.doc_id])
    else:
        rows = list(chunks)
    by_key = {(c.doc_id, c.chunk_index): c for c in rows}
    packed = _pack_windows(
        section,
        _section_chunks_from_index(section, by_key),
        max_chars=max_chars,
    )
    yield from packed


def plan_coverage_windows(
    project_root: Path | str | None,
    doc_ids: Sequence[str],
    *,
    max_chars: int = DOC_COVERAGE_SECTION_MAX_CHARS,
    max_sections: int = DOC_COVERAGE_MAX_SECTIONS,
    chunks_per_section: int = DOC_COVERAGE_CHUNKS_PER_SECTION,
    min_chunks: int = DOC_COVERAGE_MIN_CHUNKS,
) -> CoveragePlan | None:
    """One corpus load → gate + sections + windows (no second SQLite round-trip)."""
    chunks = load_chunks(project_root, doc_ids)
    if len(chunks) < max(1, int(min_chunks)):
        return None
    sections = build_section_map_from_chunks(
        chunks,
        max_sections=max_sections,
        chunks_per_section=chunks_per_section,
    )
    if not sections:
        return None
    by_key = {(c.doc_id, c.chunk_index): c for c in chunks}
    section_windows: list[tuple[DocSection, tuple[SectionWindow, ...]]] = []
    covered = 0
    for section in sections:
        windows = tuple(
            _pack_windows(
                section,
                _section_chunks_from_index(section, by_key),
                max_chars=max_chars,
            )
        )
        covered += sum(len(window.chunk_indexes) for window in windows)
        section_windows.append((section, windows))
    total_windows = sum(len(wins) for _, wins in section_windows)
    if total_windows < 1:
        return None
    return CoveragePlan(
        sections=tuple(sections),
        section_windows=tuple(section_windows),
        total_chunks=len(chunks),
        covered_chunks=covered,
        total_windows=total_windows,
    )


def section_body(
    project_root: Path | str | None,
    section: DocSection,
    *,
    max_chars: int = DOC_COVERAGE_SECTION_MAX_CHARS,
) -> str:
    """Full section text via concatenated windows (no dropped chunks)."""
    bodies = [
        window.body
        for window in iter_section_windows(
            project_root, section, max_chars=max_chars
        )
    ]
    return "\n\n".join(bodies)


def covered_chunk_keys(
    project_root: Path | str | None,
    sections: Sequence[DocSection],
    *,
    max_chars: int = DOC_COVERAGE_SECTION_MAX_CHARS,
    chunks: Sequence[ChunkRow] | None = None,
) -> set[ChunkKey]:
    """`(doc_id, chunk_index)` keys that windows feed to the LLM."""
    covered: set[ChunkKey] = set()
    for section in sections:
        for window in iter_section_windows(
            project_root, section, max_chars=max_chars, chunks=chunks
        ):
            for index in window.chunk_indexes:
                covered.add((window.doc_id, index))
    return covered


def expected_chunk_keys(chunks: Sequence[ChunkRow]) -> set[ChunkKey]:
    return {
        (chunk.doc_id, chunk.chunk_index)
        for chunk in chunks
        if (chunk.content or "").strip()
    }
