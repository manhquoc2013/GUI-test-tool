"""High-speed SQLite init + schema smoke (Hybrid Local-First)."""

from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.core.sqlite_db import init_high_speed_db


@pytest.mark.asyncio
async def test_init_high_speed_db_wal_and_tables(tmp_path: Path) -> None:
    db_path = tmp_path / ".studio" / "runtime.db"
    conn = await init_high_speed_db(db_path)
    try:
        assert db_path.is_file()
        cursor = await conn.execute("PRAGMA journal_mode")
        row = await cursor.fetchone()
        assert row is not None
        assert str(row[0]).lower() == "wal"

        cursor = await conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'")
        names = {r[0] for r in await cursor.fetchall()}
        assert "app_settings" in names
        assert "test_cases_index" in names
        assert "execution_runs" in names
        assert "search_fts" in names

        await conn.execute(
            "INSERT INTO app_settings(key, value) VALUES (?, ?)",
            ("theme", "auto"),
        )
        await conn.commit()
        cursor = await conn.execute("SELECT value FROM app_settings WHERE key = ?", ("theme",))
        got = await cursor.fetchone()
        assert got is not None
        assert got[0] == "auto"
    finally:
        await conn.close()


@pytest.mark.asyncio
async def test_init_high_speed_db_idempotent(tmp_path: Path) -> None:
    db_path = tmp_path / "app.db"
    conn1 = await init_high_speed_db(db_path)
    await conn1.close()
    conn2 = await init_high_speed_db(db_path)
    try:
        cursor = await conn2.execute("SELECT COUNT(*) FROM app_settings")
        row = await cursor.fetchone()
        assert row is not None
        assert row[0] == 0
    finally:
        await conn2.close()
