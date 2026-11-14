"""Project-scoped Playwright storageState under ``.studio/auth/``."""

from __future__ import annotations

import json
from pathlib import Path
from urllib.parse import urlparse

from ai_qa_test_studio.constants import (
    AUTH_GITIGNORE_ENTRY,
    DEFAULT_AUTH_PROFILE,
    LOGIN_PATH_MARKERS,
    STUDIO_AUTH_DIRNAME,
    STUDIO_DIRNAME,
)


def auth_dir(project_root: Path) -> Path:
    return Path(project_root).expanduser().resolve() / STUDIO_DIRNAME / STUDIO_AUTH_DIRNAME


def auth_state_path(project_root: Path, profile_name: str = DEFAULT_AUTH_PROFILE) -> Path:
    safe = "".join(ch if ch.isalnum() or ch in "-_" else "_" for ch in profile_name.strip()) or (
        DEFAULT_AUTH_PROFILE
    )
    return auth_dir(project_root) / f"{safe}.json"


def ensure_auth_gitignore(project_root: Path) -> None:
    """Ensure ``.studio/auth/`` is listed in ``.gitignore`` (defense in depth)."""
    gitignore = Path(project_root).expanduser().resolve() / ".gitignore"
    entry = AUTH_GITIGNORE_ENTRY
    if gitignore.is_file():
        text = gitignore.read_text(encoding="utf-8")
        if entry in text or "**/.studio/" in text or ".studio/" in text:
            return
        suffix = "" if text.endswith("\n") or not text else "\n"
        gitignore.write_text(f"{text}{suffix}{entry}\n", encoding="utf-8")
        return
    gitignore.write_text(f"{entry}\n", encoding="utf-8")


def is_valid_storage_state(path: Path) -> bool:
    if not path.is_file():
        return False
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return False
    if not isinstance(raw, dict):
        return False
    cookies = raw.get("cookies")
    origins = raw.get("origins")
    return isinstance(cookies, list) or isinstance(origins, list)


def resolve_auth_state(
    project_root: Path | None,
    profile_name: str = DEFAULT_AUTH_PROFILE,
) -> Path | None:
    if project_root is None:
        return None
    path = auth_state_path(project_root, profile_name)
    if is_valid_storage_state(path):
        return path
    return None


def looks_like_login_url(url: str) -> bool:
    """True when the current URL path looks like a login / SSO redirect target."""
    raw = (url or "").strip()
    if not raw:
        return False
    parsed = urlparse(raw)
    path = (parsed.path or "").lower()
    if not path:
        return False
    return any(marker in path for marker in LOGIN_PATH_MARKERS)
