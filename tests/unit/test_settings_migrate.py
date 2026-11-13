from __future__ import annotations

import json
from pathlib import Path

from ai_qa_test_studio.core.env_manager import EnvManager, migrate_ai_config
from ai_qa_test_studio.core.models import AiConnection, AiModelEntry, AiSettings


def test_flat_ai_config_migrates_to_connection(tmp_path: Path) -> None:
    ai = migrate_ai_config(
        {"apiKey": "sk-test", "baseUrl": "https://api.example", "model": "gpt-4o", "provider": "openai_compatible"}
    )
    assert len(ai.connections) == 1
    assert ai.connections[0].api_key_ref == "sk-test"
    assert ai.connections[0].models[0].id == "gpt-4o"
    assert ai.routing.task_complexity_models


def test_round_trip_multi_api(tmp_path: Path) -> None:
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
    env2 = EnvManager(tmp_path)
    env2.load_settings()
    assert len(env2.settings.ai.connections) == 2
    assert env2.settings.ai.default_connection_id == "c1"
