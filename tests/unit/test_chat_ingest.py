"""Ingest cache + path-aligned doc_id map for Coverage Pass after chip clear."""

from __future__ import annotations

import asyncio

import pytest

pytest.importorskip("PySide6")

from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.core.doc_corpus import IngestResult
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


@pytest.fixture
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def _result(doc_id: str) -> IngestResult:
    return IngestResult(doc_id=doc_id, total_chunks=2, reused=False, title=doc_id)


@pytest.mark.asyncio
async def test_await_skips_failed_path_without_shifting_peers(qapp: QApplication) -> None:
    pane = CopilotChatPane()
    path_bad = "/tmp/bad.docx"
    path_ok = "/tmp/ok.docx"

    async def _fail() -> IngestResult:
        raise RuntimeError("ingest failed")

    async def _ok() -> IngestResult:
        return _result("doc-ok")

    pane._ingest_tasks[path_bad] = asyncio.create_task(_fail())
    pane._ingest_tasks[path_ok] = asyncio.create_task(_ok())
    mapping = await pane.await_attachment_ingests([path_bad, path_ok])
    assert mapping == {path_ok: "doc-ok"}
    assert path_bad not in mapping
    pane.close()


@pytest.mark.asyncio
async def test_await_uses_cache_after_chip_clear(qapp: QApplication) -> None:
    pane = CopilotChatPane()
    path = "/tmp/srs.docx"
    pane._ingest_results[path] = _result("doc-cached")
    pane._clear_attachments()
    assert pane._attachments == []
    mapping = await pane.await_attachment_ingests([path])
    assert mapping == {path: "doc-cached"}
    pane.close()


@pytest.mark.asyncio
async def test_new_session_resets_ingest_cache(qapp: QApplication) -> None:
    pane = CopilotChatPane()
    path = "/tmp/old.docx"
    pane._ingest_results[path] = _result("doc-old")

    async def _pending() -> IngestResult:
        await asyncio.sleep(30)
        return _result("never")

    task = asyncio.create_task(_pending())
    pane._ingest_tasks[path] = task
    pane.new_session()
    assert pane._ingest_results == {}
    assert pane._ingest_tasks == {}
    with pytest.raises(asyncio.CancelledError):
        await task
    assert task.cancelled()
    pane.close()
