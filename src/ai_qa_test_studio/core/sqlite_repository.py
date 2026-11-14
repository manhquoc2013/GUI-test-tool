"""SQLite-backed StorageRepository (app DB or per-project runtime.db)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import aiosqlite

from ai_qa_test_studio.constants import APP_SETTING_THEME
from ai_qa_test_studio.core.sqlite_db import init_high_speed_db

_APP_SETTINGS_BLOB_KEY = "app_settings_json"


class SqliteRepository:
    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self._conn: aiosqlite.Connection | None = None

    async def open(self) -> None:
        if self._conn is not None:
            return
        self._conn = await init_high_speed_db(self.db_path)

    async def close(self) -> None:
        if self._conn is None:
            return
        await self._conn.close()
        self._conn = None

    def _require_conn(self) -> aiosqlite.Connection:
        if self._conn is None:
            raise RuntimeError("SqliteRepository is closed; call open() first")
        return self._conn

    async def get_setting(self, key: str) -> str | None:
        conn = self._require_conn()
        cursor = await conn.execute("SELECT value FROM app_settings WHERE key = ?", (key,))
        row = await cursor.fetchone()
        return None if row is None else str(row[0])

    async def set_setting(self, key: str, value: str) -> None:
        conn = self._require_conn()
        await conn.execute(
            "INSERT INTO app_settings(key, value) VALUES(?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )
        await conn.commit()

    async def get_app_settings_blob(self) -> dict[str, Any] | None:
        raw = await self.get_setting(_APP_SETTINGS_BLOB_KEY)
        if raw is None:
            return None
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            return None
        return payload if isinstance(payload, dict) else None

    async def set_app_settings_blob(self, payload: dict[str, Any]) -> None:
        await self.set_setting(_APP_SETTINGS_BLOB_KEY, json.dumps(payload, ensure_ascii=False))
        theme = payload.get("theme")
        if isinstance(theme, str) and theme:
            await self.set_setting(APP_SETTING_THEME, theme)
