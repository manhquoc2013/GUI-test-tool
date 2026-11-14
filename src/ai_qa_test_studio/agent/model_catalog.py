"""Fetch available model IDs from OpenAI-compatible or Gemini providers."""

from __future__ import annotations

import logging
from typing import Any

from ai_qa_test_studio.constants import (
    LLM_MODEL_LIST_TIMEOUT_S,
    LLM_PROVIDER_GEMINI,
    LLM_PROVIDER_OFFLINE,
    LLM_PROVIDER_OPENAI_COMPAT,
)

logger = logging.getLogger(__name__)


def list_available_models(
    *,
    provider: str,
    api_key: str,
    base_url: str = "",
    timeout_s: float = LLM_MODEL_LIST_TIMEOUT_S,
) -> list[str]:
    """Return sorted unique model ids. Empty for offline / missing key."""
    kind = (provider or "").strip().lower()
    key = (api_key or "").strip()
    if kind == LLM_PROVIDER_OFFLINE or not key:
        return []
    if kind == LLM_PROVIDER_GEMINI:
        return _list_gemini(key, timeout_s=timeout_s)
    if kind == LLM_PROVIDER_OPENAI_COMPAT:
        return _list_openai_compat(key, base_url=base_url, timeout_s=timeout_s)
    return _list_openai_compat(key, base_url=base_url, timeout_s=timeout_s)


def _list_openai_compat(api_key: str, *, base_url: str, timeout_s: float) -> list[str]:
    try:
        from openai import OpenAI
    except ImportError as exc:
        raise RuntimeError("Thiếu package openai để tải danh sách model") from exc
    kwargs: dict[str, Any] = {"api_key": api_key, "timeout": timeout_s}
    cleaned = (base_url or "").strip().rstrip("/")
    if cleaned:
        kwargs["base_url"] = cleaned
    client = OpenAI(**kwargs)
    ids: list[str] = []
    for page in client.models.list():
        mid = getattr(page, "id", None)
        if isinstance(mid, str) and mid.strip():
            ids.append(mid.strip())
    return sorted(set(ids), key=str.lower)


def _list_gemini(api_key: str, *, timeout_s: float) -> list[str]:
    try:
        from google import genai
    except ImportError as exc:
        raise RuntimeError("Thiếu package google-genai để tải danh sách model") from exc
    # google-genai Client does not expose a simple timeout kw on list; rely on SDK defaults.
    _ = timeout_s
    client = genai.Client(api_key=api_key)
    ids: list[str] = []
    try:
        pager = client.models.list()
    except Exception as exc:
        logger.error("gemini_models_list_failed err=%s", exc)
        raise RuntimeError(f"Không tải được models Gemini: {exc}") from exc
    for item in pager:
        raw = getattr(item, "name", None) or getattr(item, "model", None) or ""
        name = str(raw).strip()
        if not name:
            continue
        if name.startswith("models/"):
            name = name.removeprefix("models/")
        ids.append(name)
    return sorted(set(ids), key=str.lower)
