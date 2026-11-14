"""App settings persistence via SQLite repository."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from ai_qa_test_studio.constants import APP_SETTING_THEME, APP_STUDIO_DB, THEME_PREF_AUTO
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.sqlite_repository import SqliteRepository


@pytest.mark.asyncio
async def test_sqlite_repository_settings_round_trip(tmp_path: Path) -> None:
    db_path = tmp_path / APP_STUDIO_DB
    repo = SqliteRepository(db_path)
    await repo.open()
    try:
        await repo.set_setting(APP_SETTING_THEME, THEME_PREF_AUTO)
        assert await repo.get_setting(APP_SETTING_THEME) == THEME_PREF_AUTO
        await repo.set_app_settings_blob({"theme": "light", "browser": "CHROMIUM"})
        blob = await repo.get_app_settings_blob()
        assert blob is not None
        assert blob["theme"] == "light"
        assert blob["browser"] == "CHROMIUM"
    finally:
        await repo.close()


def test_env_manager_persists_to_sqlite_and_migrates_json(tmp_path: Path) -> None:
    legacy = tmp_path / "settings.json"
    legacy.write_text(
        json.dumps({"theme": "light", "browser": "FIREFOX", "headlessDefault": True}),
        encoding="utf-8",
    )
    env = EnvManager(tmp_path)
    settings = env.load_settings()
    assert settings.theme == "light"
    assert settings.browser == "FIREFOX"
    assert settings.headless_default is True

    db_path = tmp_path / APP_STUDIO_DB
    assert db_path.is_file()

    env.settings.theme = THEME_PREF_AUTO
    env.save_settings()

    env2 = EnvManager(tmp_path)
    loaded = env2.load_settings()
    assert loaded.theme == THEME_PREF_AUTO
    assert loaded.browser == "FIREFOX"
