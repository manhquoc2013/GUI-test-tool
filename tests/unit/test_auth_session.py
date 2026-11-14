from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.constants import AUTH_GITIGNORE_ENTRY, STUDIO_AUTH_DIRNAME, STUDIO_DIRNAME
from ai_qa_test_studio.core.auth_session import (
    auth_state_path,
    ensure_auth_gitignore,
    is_valid_storage_state,
    looks_like_login_url,
    resolve_auth_state,
)


def test_looks_like_login_url() -> None:
    assert looks_like_login_url("https://app.example/login")
    assert looks_like_login_url("https://app.example/oauth/sso/start")
    assert looks_like_login_url("https://app.example/signin?next=/home")
    assert not looks_like_login_url("https://app.example/dashboard")
    assert not looks_like_login_url("")


def test_auth_state_roundtrip(tmp_path: Path) -> None:
    path = auth_state_path(tmp_path, "qa")
    assert path.parent.name == STUDIO_AUTH_DIRNAME
    assert STUDIO_DIRNAME in path.parts
    assert resolve_auth_state(tmp_path, "qa") is None

    path.parent.mkdir(parents=True)
    path.write_text(json.dumps({"cookies": [], "origins": []}), encoding="utf-8")
    assert is_valid_storage_state(path)
    assert resolve_auth_state(tmp_path, "qa") == path


def test_ensure_auth_gitignore_adds_entry(tmp_path: Path) -> None:
    gitignore = tmp_path / ".gitignore"
    gitignore.write_text("*.pyc\n", encoding="utf-8")
    ensure_auth_gitignore(tmp_path)
    text = gitignore.read_text(encoding="utf-8")
    assert AUTH_GITIGNORE_ENTRY in text
    ensure_auth_gitignore(tmp_path)
    assert text.count(AUTH_GITIGNORE_ENTRY) == 1
