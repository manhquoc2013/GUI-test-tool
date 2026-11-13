"""Environment profiles, test-data profiles, and settings (no plaintext key logging)."""

from __future__ import annotations

import json
import logging
from pathlib import Path

from ai_qa_test_studio.constants import SETTINGS_FILENAME
from ai_qa_test_studio.core.models import (
    AiSettings,
    AppSettings,
    EnvironmentProfile,
    TestDataProfile,
)

logger = logging.getLogger(__name__)


class EnvManager:
    def __init__(self, config_dir: Path) -> None:
        self.config_dir = config_dir
        self.config_dir.mkdir(parents=True, exist_ok=True)
        self.settings_path = self.config_dir / SETTINGS_FILENAME
        self._settings = AppSettings()
        self._profiles: dict[str, TestDataProfile] = {}

    @property
    def settings(self) -> AppSettings:
        return self._settings

    def load_settings(self) -> AppSettings:
        if not self.settings_path.is_file():
            return self._settings
        try:
            payload = json.loads(self.settings_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("settings_load_failed path=%s err=%s", self.settings_path, exc)
            return self._settings
        ai_raw = payload.get("aiConfig") or payload.get("ai") or {}
        api_key = ai_raw.get("apiKey") or ai_raw.get("api_key") or ""
        # Keep ciphertext/ref only — never decode into logs.
        self._settings = AppSettings(
            browser=str(payload.get("browser") or "CHROMIUM"),
            headless_default=bool(
                payload.get("headlessDefault", payload.get("headless_default", False))
            ),
            theme=str(payload.get("theme") or "dark"),
            ai=AiSettings(
                api_key_ref=str(api_key),
                base_url=str(ai_raw.get("baseUrl") or ai_raw.get("base_url") or ""),
                model=str(ai_raw.get("model") or ""),
                temperature=float(ai_raw.get("temperature") or 0.2),
                max_tokens=int(ai_raw.get("maxTokens") or ai_raw.get("max_tokens") or 512),
            ),
            active_environment=str(payload.get("active_environment") or "DEV"),
        )
        return self._settings

    def save_settings(self) -> None:
        payload = {
            "browser": self._settings.browser,
            "headlessDefault": self._settings.headless_default,
            "theme": self._settings.theme,
            "active_environment": self._settings.active_environment,
            "aiConfig": {
                "apiKey": self._settings.ai.api_key_ref,
                "baseUrl": self._settings.ai.base_url,
                "model": self._settings.ai.model,
                "temperature": self._settings.ai.temperature,
                "maxTokens": self._settings.ai.max_tokens,
            },
        }
        tmp = self.settings_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(self.settings_path)

    def upsert_profile(self, profile: TestDataProfile) -> None:
        self._profiles[profile.id] = profile

    def get_profile(self, profile_id: str) -> TestDataProfile | None:
        return self._profiles.get(profile_id)

    def list_profiles(self) -> list[TestDataProfile]:
        return list(self._profiles.values())

    def resolve_variables(
        self, text: str, env: EnvironmentProfile | None = None, row: dict[str, str] | None = None
    ) -> str:
        merged: dict[str, str] = {}
        if env:
            merged.update(env.variables)
        if row:
            merged.update({str(k): str(v) for k, v in row.items()})
        result = text
        for key, value in merged.items():
            result = result.replace(f"{{{{{key}}}}}", value)
            result = result.replace(f"{{{{ {key} }}}}", value)
        return result
