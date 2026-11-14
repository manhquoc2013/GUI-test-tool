"""Remote model catalog listing."""

from __future__ import annotations

import sys
from types import ModuleType

from ai_qa_test_studio.agent import model_catalog as catalog
from ai_qa_test_studio.agent.model_catalog import list_available_models
from ai_qa_test_studio.constants import (
    LLM_PROVIDER_GEMINI,
    LLM_PROVIDER_OFFLINE,
    LLM_PROVIDER_OPENAI_COMPAT,
)


def test_offline_or_empty_key_returns_empty() -> None:
    assert list_available_models(provider=LLM_PROVIDER_OFFLINE, api_key="x") == []
    assert list_available_models(provider=LLM_PROVIDER_OPENAI_COMPAT, api_key="") == []


def test_openai_compat_helper(monkeypatch: object) -> None:
    class _Model:
        def __init__(self, mid: str) -> None:
            self.id = mid

    class _Models:
        def list(self) -> list[_Model]:
            return [_Model("gpt-4o"), _Model("gpt-4o-mini"), _Model("gpt-4o")]

    class _Client:
        def __init__(self, **_kwargs: object) -> None:
            self.models = _Models()

    fake = ModuleType("openai")
    fake.OpenAI = _Client  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "openai", fake)

    ids = catalog._list_openai_compat("sk", base_url="https://api.example.com/v1", timeout_s=5)
    assert ids == ["gpt-4o", "gpt-4o-mini"]


def test_gemini_strips_models_prefix(monkeypatch: object) -> None:
    class _Item:
        def __init__(self, name: str) -> None:
            self.name = name

    class _ModelsApi:
        def list(self) -> list[_Item]:
            return [_Item("models/gemini-2.0-flash"), _Item("models/gemini-1.5-pro")]

    class _Client:
        def __init__(self, **_kwargs: object) -> None:
            self.models = _ModelsApi()

    genai_mod = ModuleType("google.genai")
    genai_mod.Client = _Client  # type: ignore[attr-defined]
    google_mod = ModuleType("google")
    google_mod.genai = genai_mod  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "google", google_mod)
    monkeypatch.setitem(sys.modules, "google.genai", genai_mod)

    ids = catalog._list_gemini("key", timeout_s=5)
    assert ids == ["gemini-1.5-pro", "gemini-2.0-flash"]
    assert list_available_models(provider=LLM_PROVIDER_GEMINI, api_key="key") == ids
