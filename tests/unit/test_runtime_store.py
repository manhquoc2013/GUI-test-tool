"""Runtime execution_runs + artifact path helpers."""

from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.core.runtime_store import RuntimeStore


@pytest.mark.asyncio
async def test_runtime_store_records_run_and_artifact_path(tmp_path: Path) -> None:
    store = RuntimeStore(tmp_path)
    shot = store.artifact_path("runs", "r1", "fail.png")
    shot.write_bytes(b"png")
    assert shot.is_file()
    await store.record_run(
        run_id="r1",
        case_id="c1",
        status="FAIL",
        started_at="2026-11-14T00:00:00Z",
        duration_ms=12,
        report_path=str(shot.relative_to(tmp_path)),
    )
    # reopen and count
    from ai_qa_test_studio.core.sqlite_db import init_high_speed_db

    conn = await init_high_speed_db(store.db_path)
    try:
        cursor = await conn.execute("SELECT status FROM execution_runs WHERE run_id = ?", ("r1",))
        row = await cursor.fetchone()
        assert row is not None
        assert row[0] == "FAIL"
    finally:
        await conn.close()
