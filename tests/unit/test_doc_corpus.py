from __future__ import annotations

import sqlite3
import threading
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

from ai_qa_test_studio.constants import DOC_CORPUS_DB_NAME, DOC_OVERVIEW_CHUNK_COUNT
from ai_qa_test_studio.core import doc_corpus as dc


def test_sanitize_fts_query_handles_special_chars() -> None:
    q = dc.sanitize_fts_query('TC-01 login:fail (auth) AND OR NOT "x"')
    assert "TC-01" in q or "tc-01" in q.lower() or '"tc"' in q.lower() or '"01"' in q
    assert ":" not in q.replace('"tc-01"', "")
    assert "(" not in q and ")" not in q
    # Must be executable MATCH — no bare AND/OR/NOT operators
    assert " AND " not in q
    assert " OR " in q or q.count('"') >= 2


def test_sanitize_empty() -> None:
    assert dc.sanitize_fts_query("") == ""
    assert dc.sanitize_fts_query("   ") == ""


def test_ingest_and_retrieve_middle_section(tmp_path: Path) -> None:
    body = (
        "Intro overview alpha.\n\n"
        + ("pad " * 200)
        + "\n\nUNIQUE_MIDDLE_TOKEN_ZXQ login flow.\n\n"
        + ("tail " * 200)
    )
    doc = tmp_path / "srs.md"
    doc.write_text(body, encoding="utf-8")
    root = tmp_path / "project"
    root.mkdir()
    result = dc.ingest_path(root, doc)
    assert result.total_chunks >= 2
    assert not result.reused
    hits = dc.retrieve(root, "UNIQUE_MIDDLE_TOKEN_ZXQ", doc_ids=[result.doc_id])
    assert hits
    assert any("UNIQUE_MIDDLE_TOKEN_ZXQ" in h.content for h in hits)


def test_ingest_idempotent_same_hash(tmp_path: Path) -> None:
    doc = tmp_path / "a.md"
    doc.write_text("hello world requirement login", encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    first = dc.ingest_path(root, doc)
    second = dc.ingest_path(root, doc)
    assert second.reused is True
    assert second.doc_id == first.doc_id


def test_ingest_replaces_on_hash_change(tmp_path: Path) -> None:
    doc = tmp_path / "a.md"
    doc.write_text("version one content alpha", encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    first = dc.ingest_path(root, doc)
    doc.write_text("version two content beta UNIQUE_V2", encoding="utf-8")
    second = dc.ingest_path(root, doc)
    assert second.reused is False
    assert second.doc_id == first.doc_id
    hits = dc.retrieve(root, "UNIQUE_V2", doc_ids=[second.doc_id])
    assert hits
    assert any("UNIQUE_V2" in h.content for h in hits)


def test_overview_fallback_on_generic_query(tmp_path: Path) -> None:
    doc = tmp_path / "srs.md"
    # Distinct early chunks then unique later content
    parts = [f"SECTION_{i} " + ("word " * 80) for i in range(6)]
    doc.write_text("\n\n".join(parts), encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    result = dc.ingest_path(root, doc)
    hits = dc.retrieve(
        root,
        "Tạo test case cho file SRS vừa gửi",
        doc_ids=[result.doc_id],
    )
    assert hits
    assert all(h.chunk_index < DOC_OVERVIEW_CHUNK_COUNT for h in hits) or any(
        h.chunk_index == 0 for h in hits
    )


def test_attach_scope_excludes_other_docs(tmp_path: Path) -> None:
    root = tmp_path / "proj"
    root.mkdir()
    a = tmp_path / "a.md"
    b = tmp_path / "b.md"
    a.write_text("module alpha UNIQUE_ALPHA_ONLY " + ("x " * 100), encoding="utf-8")
    b.write_text("module beta UNIQUE_BETA_ONLY " + ("y " * 100), encoding="utf-8")
    ra = dc.ingest_path(root, a)
    dc.ingest_path(root, b)
    hits = dc.retrieve(root, "UNIQUE_BETA_ONLY", doc_ids=[ra.doc_id])
    # Scoped to A — should not return B's unique token (overview of A ok)
    assert all("UNIQUE_BETA_ONLY" not in h.content for h in hits)


def test_tmp_corpus_cleanup_stale(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    cache = tmp_path / "cache"
    cache.mkdir()
    monkeypatch.setattr(dc, "corpus_db_path", lambda project_root: cache / "tmp_corpus.db")
    db = cache / "tmp_corpus.db"
    # Force open + insert stale via ingest then backdate
    doc = tmp_path / "old.md"
    doc.write_text("stale document content here", encoding="utf-8")
    result = dc.ingest_path(None, doc)
    conn = sqlite3.connect(str(db))
    old = (datetime.now(UTC) - timedelta(days=10)).isoformat(timespec="seconds")
    conn.execute("UPDATE documents SET updated_at = ? WHERE doc_id = ?", (old, result.doc_id))
    conn.commit()
    conn.close()
    # Re-open triggers cleanup
    fresh = tmp_path / "new.md"
    fresh.write_text("fresh document content here", encoding="utf-8")
    dc.ingest_path(None, fresh)
    conn = sqlite3.connect(str(db))
    ids = {row[0] for row in conn.execute("SELECT doc_id FROM documents").fetchall()}
    conn.close()
    assert result.doc_id not in ids


def test_concurrent_ingest_and_retrieve(tmp_path: Path) -> None:
    root = tmp_path / "proj"
    root.mkdir()
    docs = []
    for i in range(4):
        p = tmp_path / f"d{i}.md"
        p.write_text(f"document {i} concurrent TOKEN{i} " + ("body " * 200), encoding="utf-8")
        docs.append(p)

    errors: list[BaseException] = []

    def ingest_all() -> None:
        try:
            for p in docs:
                dc.ingest_path(root, p)
        except BaseException as exc:  # noqa: BLE001
            errors.append(exc)

    def retrieve_loop() -> None:
        try:
            for _ in range(20):
                dc.retrieve(root, "TOKEN1 concurrent", doc_ids=None)
                time.sleep(0.01)
        except BaseException as exc:  # noqa: BLE001
            errors.append(exc)

    t1 = threading.Thread(target=ingest_all)
    t2 = threading.Thread(target=retrieve_loop)
    t1.start()
    t2.start()
    t1.join()
    t2.join()
    assert not errors, errors


def test_format_retrieved_block() -> None:
    block = dc.format_retrieved_block(
        [dc.ChunkHit(doc_id="d1", chunk_index=0, section_meta="meta", content="hello")]
    )
    assert block.startswith("RETRIEVED:")
    assert "hello" in block


def test_xlsx_ingest(tmp_path: Path) -> None:
    pytest.importorskip("openpyxl")
    from openpyxl import Workbook

    wb = Workbook()
    ws = wb.active
    assert ws is not None
    ws.title = "Test Cases"
    ws.append(["Test Case ID", "Title", "Action"])
    for i in range(60):
        ws.append([f"TC-{i:03d}", f"Case {i}", f"click button {i}"])
    path = tmp_path / "cases.xlsx"
    wb.save(path)
    root = tmp_path / "proj"
    root.mkdir()
    result = dc.ingest_path(root, path)
    assert result.total_chunks >= 2
    hits = dc.retrieve(root, "TC-045", doc_ids=[result.doc_id])
    assert hits
    db = root / ".studio" / DOC_CORPUS_DB_NAME
    assert db.is_file()
