from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from ai_qa_test_studio.constants import KEYRING_REF_PREFIX, KEYRING_SERVICE
from ai_qa_test_studio.core.env_manager import EnvManager, ai_settings_to_payload
from ai_qa_test_studio.core.models import AiConnection, AiModelEntry, AiSettings
from ai_qa_test_studio.core.secret_store import is_keyring_ref, load_secret, store_secret


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


def test_store_returns_keyring_ref_and_load_roundtrips(fake_keyring: dict[tuple[str, str], str]) -> None:
    raw = "sk-live-secret-abc"
    ref = store_secret("conn-a", raw)
    assert ref == f"{KEYRING_REF_PREFIX}conn-a"
    assert is_keyring_ref(ref)
    assert not is_keyring_ref(raw)
    assert fake_keyring[(KEYRING_SERVICE, "conn-a")] == raw
    assert load_secret(ref) == raw


def test_load_secret_empty_or_missing_returns_empty(fake_keyring: dict[tuple[str, str], str]) -> None:
    assert load_secret("") == ""
    assert load_secret("plain-not-a-ref") == ""
    assert load_secret(f"{KEYRING_REF_PREFIX}missing-slot") == ""


def test_ai_settings_payload_never_contains_raw_key(
    fake_keyring: dict[tuple[str, str], str],
) -> None:
    raw_key = "sk-must-not-appear-in-json"
    ai = AiSettings(
        api_key_ref=raw_key,
        provider="openai_compatible",
        model="m1",
        default_connection_id="c1",
        connections=[
            AiConnection(
                id="c1",
                label="Primary",
                provider="openai_compatible",
                api_key_ref=raw_key,
                models=[AiModelEntry(id="m1", label="M1", tier="fast", is_default=True)],
            )
        ],
    )
    payload = ai_settings_to_payload(ai)
    dumped = json.dumps(payload)
    assert raw_key not in dumped
    assert payload["apiKey"].startswith(KEYRING_REF_PREFIX)
    assert payload["connections"][0]["apiKeyRef"].startswith(KEYRING_REF_PREFIX)
    assert load_secret(str(payload["connections"][0]["apiKeyRef"])) == raw_key


def test_save_settings_persists_refs_only(
    tmp_path: Path,
    fake_keyring: dict[tuple[str, str], str],
) -> None:
    raw_key = "sk-disk-secret-xyz"
    env = EnvManager(tmp_path)
    env.load_settings()
    env.settings.ai = AiSettings(
        api_key_ref=raw_key,
        provider="openai_compatible",
        model="m1",
        default_connection_id="c1",
        connections=[
            AiConnection(
                id="c1",
                label="A",
                provider="openai_compatible",
                api_key_ref=raw_key,
                models=[AiModelEntry(id="m1", label="M1", tier="fast", is_default=True)],
            )
        ],
    )
    env.save_settings()
    disk: dict[str, Any] = json.loads((tmp_path / "settings.json").read_text(encoding="utf-8"))
    assert raw_key not in json.dumps(disk)
    ref = disk["aiConfig"]["connections"][0]["apiKeyRef"]
    assert is_keyring_ref(ref)
    assert load_secret(ref) == raw_key
