"""Four-tier resolve_model_token_profile fallback."""

from __future__ import annotations

import pytest

from ai_qa_test_studio.constants import (
    MODEL_TOKEN_DEFAULT_CONTEXT,
    MODEL_TOKEN_DEFAULT_MAX_OUTPUT,
    MODEL_TOKEN_LABEL_API,
    MODEL_TOKEN_LABEL_CATALOG,
    MODEL_TOKEN_LABEL_DEFAULT,
    MODEL_TOKEN_SOURCE_API,
    MODEL_TOKEN_SOURCE_CATALOG,
    MODEL_TOKEN_SOURCE_DEFAULT,
)
from ai_qa_test_studio.core.token_budget import (
    ModelTokenProfile,
    match_catalog_token_profile,
    resolve_model_token_profile,
    token_profile_source_label,
)


def test_catalog_deepseek_reasoner() -> None:
    profile = match_catalog_token_profile("deepseek-reasoner")
    assert profile is not None
    assert profile.context_window == 128_000
    assert profile.max_output_tokens == 32_768
    assert profile.source == MODEL_TOKEN_SOURCE_CATALOG


def test_catalog_gemini_prefix() -> None:
    profile = resolve_model_token_profile("gemini-2.0-flash", skip_api=True)
    assert profile.source == MODEL_TOKEN_SOURCE_CATALOG
    assert profile.context_window == 1_000_000
    assert profile.max_output_tokens == 65_536
    assert token_profile_source_label(profile) == MODEL_TOKEN_LABEL_CATALOG


def test_cache_tier_when_unknown_and_cached() -> None:
    cache = {
        "my-custom-local-model-v1": {
            "context_window": 32_000,
            "max_output_tokens": 4_096,
        }
    }
    profile = resolve_model_token_profile(
        "my-custom-local-model-v1",
        cache=cache,
        skip_api=True,
    )
    assert profile.source == "cache"
    assert profile.context_window == 32_000
    assert profile.max_output_tokens == 4_096


def test_api_timeout_unknown_model_falls_to_safe_default() -> None:
    def _timeout_fetch(
        model_id: str,
        *,
        provider: str = "",
        api_key: str = "",
        base_url: str = "",
        timeout_s: float = 3.0,
    ) -> ModelTokenProfile | None:
        _ = (model_id, provider, api_key, base_url, timeout_s)
        raise TimeoutError("simulated provider /models timeout")

    profile = resolve_model_token_profile(
        "my-custom-local-model-v1",
        provider="openai_compatible",
        api_key="sk-test",
        base_url="http://127.0.0.1:9",
        cache={},
        skip_api=False,
        fetch_fn=_timeout_fetch,
    )
    assert profile == ModelTokenProfile(
        context_window=MODEL_TOKEN_DEFAULT_CONTEXT,
        max_output_tokens=MODEL_TOKEN_DEFAULT_MAX_OUTPUT,
        source=MODEL_TOKEN_SOURCE_DEFAULT,
    )
    assert profile.context_window > 0
    assert profile.max_output_tokens > 0
    assert token_profile_source_label(profile) == MODEL_TOKEN_LABEL_DEFAULT


def test_api_success_prefers_provider_over_catalog() -> None:
    def _api_fetch(
        model_id: str,
        *,
        provider: str = "",
        api_key: str = "",
        base_url: str = "",
        timeout_s: float = 3.0,
    ) -> ModelTokenProfile | None:
        _ = (provider, api_key, base_url, timeout_s)
        return ModelTokenProfile(
            context_window=200_000,
            max_output_tokens=9_000,
            source=MODEL_TOKEN_SOURCE_API,
        )

    cache: dict[str, dict[str, int]] = {}
    profile = resolve_model_token_profile(
        "deepseek-chat",
        provider="openai_compatible",
        api_key="sk-test",
        cache=cache,
        fetch_fn=_api_fetch,
    )
    assert profile.source == MODEL_TOKEN_SOURCE_API
    assert profile.max_output_tokens == 9_000
    assert token_profile_source_label(profile) == MODEL_TOKEN_LABEL_API
    assert cache["deepseek-chat"]["max_output_tokens"] == 9_000


@pytest.mark.parametrize(
    ("model_id", "max_out"),
    [
        ("gpt-4o-mini", 16_384),
        ("claude-haiku-4", 16_384),
        ("o3-mini", 32_768),
    ],
)
def test_catalog_common_prefixes(model_id: str, max_out: int) -> None:
    profile = resolve_model_token_profile(model_id, skip_api=True)
    assert profile.source == MODEL_TOKEN_SOURCE_CATALOG
    assert profile.max_output_tokens == max_out
