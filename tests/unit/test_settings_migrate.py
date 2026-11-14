from __future__ import annotations

import json
from pathlib import Path

import pytest

from ai_qa_test_studio.constants import KEYRING_REF_PREFIX
from ai_qa_test_studio.core.env_manager import EnvManager, migrate_ai_config
from ai_qa_test_studio.core.models import AiConnection, AiModelEntry, AiSettings


@pytest.fixture
def fake_keyring(monkeypatch: pytest.MonkeyPatch) -> dict[tuple[str, str], str]:
    vault: dict[tuple[str, str], str] = {}

    def set_password(service: str, username: str, password: str) -> None:
        vault[(service, username)] = password

    def get_password(service: str, username: str) -> str | None:
        return vault.get((service, username))

    monkeypatch.setattr("keyring.set_password", set_password)
    monkeypatch.setattr("keyring.get_password", get_password)
    return vault


def test_flat_ai_config_migrates_to_connection(tmp_path: Path) -> None:
    ai = migrate_ai_config(
        {"apiKey": "sk-test", "baseUrl": "https://api.example", "model": "gpt-4o", "provider": "openai_compatible"}
    )
    assert len(ai.connections) == 1
    assert ai.connections[0].api_key_ref == "sk-test"
    assert ai.connections[0].models[0].id == "gpt-4o"
    assert ai.routing.task_complexity_models


def test_load_settings_seals_legacy_plaintext_keys(
    tmp_path: Path, fake_keyring: dict[tuple[str, str], str]
) -> None:
    path = tmp_path / "settings.json"
    path.write_text(
        json.dumps(
            {
                "aiConfig": {
                    "apiKey": "sk-legacy-plain",
                    "provider": "openai_compatible",
                    "model": "gpt-4o",
                    "connections": [
                        {
                            "id": "c1",
                            "label": "A",
                            "provider": "openai_compatible",
                            "apiKeyRef": "sk-conn-plain",
                            "enabled": True,
                            "models": [
                                {"id": "m1", "label": "M1", "tier": "fast", "isDefault": True}
                            ],
                        }
                    ],
                    "defaultConnectionId": "c1",
                }
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    env = EnvManager(tmp_path)
    env.load_settings()
    raw = json.loads(path.read_text(encoding="utf-8"))
    dumped = json.dumps(raw)
    assert "sk-legacy-plain" not in dumped
    assert "sk-conn-plain" not in dumped
    assert raw["aiConfig"]["connections"][0]["apiKeyRef"].startswith(KEYRING_REF_PREFIX)


def test_round_trip_multi_api(tmp_path: Path, fake_keyring: dict[tuple[str, str], str]) -> None:
    env = EnvManager(tmp_path)
    env.load_settings()
    env.settings.ai = AiSettings(
        connections=[
            AiConnection(
                id="c1",
                label="A",
                provider="openai_compatible",
                api_key_ref="k1",
                models=[AiModelEntry(id="m1", label="M1", tier="fast", is_default=True)],
            ),
            AiConnection(
                id="c2",
                label="B",
                provider="gemini",
                api_key_ref="k2",
                models=[AiModelEntry(id="m2", label="M2", tier="strong", is_default=True)],
            ),
        ],
        default_connection_id="c1",
        provider="openai_compatible",
        api_key_ref="k1",
        model="m1",
    )
    env.save_settings()
    raw = json.loads((tmp_path / "settings.json").read_text(encoding="utf-8"))
    assert len(raw["aiConfig"]["connections"]) == 2
    assert "k1" not in json.dumps(raw)
    assert "k2" not in json.dumps(raw)
    assert raw["aiConfig"]["connections"][0]["apiKeyRef"].startswith(KEYRING_REF_PREFIX)
    env2 = EnvManager(tmp_path)
    env2.load_settings()
    assert len(env2.settings.ai.connections) == 2
    assert env2.settings.ai.default_connection_id == "c1"
    assert env2.settings.ai.connections[0].api_key_ref.startswith(KEYRING_REF_PREFIX)
