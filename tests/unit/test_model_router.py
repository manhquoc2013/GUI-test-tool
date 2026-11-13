from __future__ import annotations

from ai_qa_test_studio.agent.model_router import ModelRouter
from ai_qa_test_studio.constants import (
    COMPLEXITY_COMPLEX,
    COMPLEXITY_SIMPLE,
    COPILOT_MODEL_AUTO,
    LLM_PROVIDER_OFFLINE,
    LLM_PROVIDER_OPENAI_COMPAT,
    LLM_TASK_COMPOSE,
    LLM_TASK_PLAN,
    LLM_TIER_FAST,
    LLM_TIER_STRONG,
)
from ai_qa_test_studio.core.env_manager import default_task_complexity_matrix
from ai_qa_test_studio.core.models import AiConnection, AiModelEntry, AiRoutingSettings, AiSettings


def _settings_two_apis() -> AiSettings:
    a = AiConnection(
        id="api-a",
        label="OpenAI",
        provider=LLM_PROVIDER_OPENAI_COMPAT,
        api_key_ref="sk-a",
        models=[
            AiModelEntry(id="gpt-4o-mini", label="mini", tier=LLM_TIER_FAST, is_default=True),
            AiModelEntry(id="gpt-4o", label="4o", tier=LLM_TIER_STRONG),
        ],
    )
    b = AiConnection(
        id="api-b",
        label="Other",
        provider=LLM_PROVIDER_OPENAI_COMPAT,
        api_key_ref="sk-b",
        models=[AiModelEntry(id="deepseek", label="DS", tier=LLM_TIER_STRONG, is_default=True)],
    )
    return AiSettings(
        connections=[a, b],
        default_connection_id="api-a",
        api_key_ref="sk-a",
        provider=LLM_PROVIDER_OPENAI_COMPAT,
        routing=AiRoutingSettings(task_complexity_models=default_task_complexity_matrix()),
    )


def test_auto_plan_simple_uses_fast() -> None:
    router = ModelRouter(_settings_two_apis())
    route = router.resolve(LLM_TASK_PLAN, COMPLEXITY_SIMPLE)
    assert route.model == "gpt-4o-mini"
    assert route.tier == LLM_TIER_FAST


def test_compose_complex_uses_strong() -> None:
    router = ModelRouter(_settings_two_apis())
    route = router.resolve(LLM_TASK_COMPOSE, COMPLEXITY_COMPLEX)
    assert route.model == "gpt-4o"
    assert route.tier == LLM_TIER_STRONG


def test_pin_model_on_other_api() -> None:
    router = ModelRouter(_settings_two_apis(), pin_ref="api-b::deepseek")
    route = router.resolve(LLM_TASK_COMPOSE, COMPLEXITY_SIMPLE)
    assert route.connection_id == "api-b"
    assert route.model == "deepseek"


def test_no_key_falls_back_offline() -> None:
    settings = _settings_two_apis()
    for conn in settings.connections:
        conn.api_key_ref = ""
    router = ModelRouter(settings)
    route = router.resolve(LLM_TASK_PLAN, COMPLEXITY_SIMPLE)
    assert route.provider == LLM_PROVIDER_OFFLINE


def test_catalog_includes_auto() -> None:
    labels = ModelRouter(_settings_two_apis()).catalog_labels()
    assert labels[0][0] == COPILOT_MODEL_AUTO
    assert any(ref.endswith("deepseek") for ref, _ in labels)
