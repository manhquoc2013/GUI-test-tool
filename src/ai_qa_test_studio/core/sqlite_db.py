"""High-speed SQLite bootstrap (WAL + FTS5) for Hybrid Local-First storage."""

from __future__ import annotations

from pathlib import Path

import aiosqlite

from ai_qa_test_studio.constants import SQLITE_CACHE_SIZE_KIB, SQLITE_MMAP_SIZE_BYTES

_PRAGMA_SCRIPT = f"""
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA cache_size = {SQLITE_CACHE_SIZE_KIB};
PRAGMA temp_store = MEMORY;
PRAGMA mmap_size = {SQLITE_MMAP_SIZE_BYTES};
PRAGMA foreign_keys = ON;
"""

_SCHEMA_SCRIPT = """
CREATE TABLE IF NOT EXISTS app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS test_cases_index (
    id TEXT PRIMARY KEY,
    suite_id TEXT NOT NULL,
    code TEXT NOT NULL,
    title TEXT NOT NULL,
    priority TEXT,
    binding_status TEXT,
    unbound_count INTEGER DEFAULT 0,
    file_path TEXT NOT NULL,
    file_hash TEXT NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS search_fts USING fts5(
    entity_id,
    entity_type,
    code,
    title,
    content,
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TABLE IF NOT EXISTS execution_runs (
    run_id TEXT PRIMARY KEY,
    case_id TEXT NOT NULL,
    status TEXT NOT NULL,
    duration_ms INTEGER,
    tokens_used INTEGER DEFAULT 0,
    healed_count INTEGER DEFAULT 0,
    started_at TEXT NOT NULL,
    report_path TEXT
);

CREATE INDEX IF NOT EXISTS idx_runs_case_time ON execution_runs(case_id, started_at DESC);
"""


async def init_high_speed_db(db_path: Path) -> aiosqlite.Connection:
    """Open (or create) a SQLite DB with desktop-optimized PRAGMAs and core schema."""
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = await aiosqlite.connect(db_path)
    await conn.executescript(_PRAGMA_SCRIPT)
    await conn.executescript(_SCHEMA_SCRIPT)
    await conn.commit()
    return conn
