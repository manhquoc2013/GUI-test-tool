"""Token budget, sliding context, in-process headroom + markitdown."""

from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request
from collections.abc import Callable, MutableMapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import tiktoken

from ai_qa_test_studio.constants import (
    COMPACTOR_CREW_DRAFT_ROLES,
    COMPACTOR_CREW_FINAL_ROLES,
    COMPACTOR_KEEP_LAST_TOOL_PAIRS,
    COMPACTOR_PIN_PREFIX,
    COMPACTOR_PIN_SUFFIX,
    COMPACTOR_SNAPSHOT_MARKER,
    COMPACTOR_SNAPSHOT_STUB_FROM_AGE,
    COMPACTOR_SNAPSHOT_STUB_TEMPLATE,
    COMPACTOR_SYSTEM_ROLES,
    COMPACTOR_TESTCASE_MARKERS,
    COMPACTOR_TOOL_CONTENT_MARKERS,
    COMPACTOR_TOOL_ROLE_NAMES,
    LLM_PROVIDER_GEMINI,
    LLM_PROVIDER_OFFLINE,
    MODEL_TOKEN_DEFAULT_CONTEXT,
    MODEL_TOKEN_DEFAULT_MAX_OUTPUT,
    MODEL_TOKEN_LABEL_API,
    MODEL_TOKEN_LABEL_CACHE,
    MODEL_TOKEN_LABEL_CATALOG,
    MODEL_TOKEN_LABEL_DEFAULT,
    MODEL_TOKEN_PROFILE_API_TIMEOUT_S,
    MODEL_TOKEN_SOURCE_API,
    MODEL_TOKEN_SOURCE_CACHE,
    MODEL_TOKEN_SOURCE_CATALOG,
    MODEL_TOKEN_SOURCE_DEFAULT,
    ROLE_COMPOSER,
    SLIDING_HISTORY_SUMMARY_CHARS,
    TOKEN_CONTEXT_BUDGET,
    TOKEN_DOM_SOFT_CAP,
    TOKEN_ENCODING_FALLBACK,
    TOKEN_FALLBACK_CHARS_PER_TOKEN,
    TOKEN_MAX_ATOMIC_OUTPUT,
    TOKEN_MODEL_DEFAULT,
)

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ModelTokenProfile:
    """Resolved context / max-output budget for one model id."""

    context_window: int
    max_output_tokens: int
    source: str


# Longest-prefix first. Values: (context_window, max_output_tokens).
MODEL_CAPABILITIES: tuple[tuple[str, int, int], ...] = (
    ("deepseek-reasoner", 128_000, 32_768),
    ("deepseek-chat", 128_000, 8_192),
    ("deepseek-flash", 128_000, 8_192),
    ("deepseek", 128_000, 8_192),
    ("gemini", 1_000_000, 65_536),
    ("claude-opus", 200_000, 64_000),
    ("claude-sonnet", 200_000, 64_000),
    ("claude-haiku", 200_000, 16_384),
    ("claude", 200_000, 16_384),
    ("gpt-4.1", 1_000_000, 32_768),
    ("gpt-5", 1_000_000, 32_768),
    ("gpt-4o", 128_000, 16_384),
    ("gpt-4", 128_000, 16_384),
    ("o3", 200_000, 32_768),
)


def token_profile_source_label(profile: ModelTokenProfile) -> str:
    if profile.source == MODEL_TOKEN_SOURCE_API:
        return MODEL_TOKEN_LABEL_API
    if profile.source == MODEL_TOKEN_SOURCE_CATALOG:
        return MODEL_TOKEN_LABEL_CATALOG
    if profile.source == MODEL_TOKEN_SOURCE_CACHE:
        return MODEL_TOKEN_LABEL_CACHE
    return MODEL_TOKEN_LABEL_DEFAULT


def match_catalog_token_profile(model_id: str) -> ModelTokenProfile | None:
    key = (model_id or "").strip().lower()
    if not key:
        return None
    for prefix, context_window, max_output in MODEL_CAPABILITIES:
        if key.startswith(prefix):
            return ModelTokenProfile(
                context_window=context_window,
                max_output_tokens=max_output,
                source=MODEL_TOKEN_SOURCE_CATALOG,
            )
    return None


def _cache_lookup(
    cache: MutableMapping[str, Any] | None, model_id: str
) -> ModelTokenProfile | None:
    if not cache:
        return None
    entry = cache.get(model_id) or cache.get(model_id.strip())
    if not isinstance(entry, dict):
        return None
    try:
        ctx = int(entry.get("context_window") or entry.get("contextWindow") or 0)
        out = int(entry.get("max_output_tokens") or entry.get("maxOutputTokens") or 0)
    except (TypeError, ValueError):
        return None
    if ctx <= 0 or out <= 0:
        return None
    return ModelTokenProfile(
        context_window=ctx,
        max_output_tokens=out,
        source=MODEL_TOKEN_SOURCE_CACHE,
    )


def _store_cache(
    cache: MutableMapping[str, Any] | None,
    model_id: str,
    profile: ModelTokenProfile,
) -> None:
    if cache is None or not model_id.strip():
        return
    cache[model_id.strip()] = {
        "context_window": int(profile.context_window),
        "max_output_tokens": int(profile.max_output_tokens),
    }


def _coerce_positive_int(value: Any) -> int | None:
    try:
        n = int(value)
    except (TypeError, ValueError):
        return None
    return n if n > 0 else None


def _extract_limits_from_mapping(data: Any) -> tuple[int, int] | None:
    """Pull (context_window, max_output) from provider JSON / SDK objects."""
    if data is None:
        return None
    payload: dict[str, Any]
    if hasattr(data, "model_dump"):
        try:
            dumped = data.model_dump()
            payload = dumped if isinstance(dumped, dict) else {}
        except Exception:  # noqa: BLE001
            raw = getattr(data, "__dict__", None)
            payload = dict(raw) if isinstance(raw, dict) else {}
    elif isinstance(data, dict):
        payload = data
    else:
        raw = getattr(data, "__dict__", None)
        payload = dict(raw) if isinstance(raw, dict) else {}
    nested = payload.get("model_info") or payload.get("modelInfo") or payload.get("details") or {}
    if not isinstance(nested, dict):
        nested = {}
    ctx_keys = (
        "context_window",
        "contextWindow",
        "context_length",
        "contextLength",
        "max_model_len",
        "maxModelLen",
        "max_input_tokens",
        "maxInputTokens",
        "input_token_limit",
        "inputTokenLimit",
    )
    out_keys = (
        "max_output_tokens",
        "maxOutputTokens",
        "max_tokens",
        "maxTokens",
        "output_token_limit",
        "outputTokenLimit",
        "max_completion_tokens",
        "maxCompletionTokens",
    )
    ctx: int | None = None
    out: int | None = None
    for key in ctx_keys:
        ctx = _coerce_positive_int(payload.get(key)) or _coerce_positive_int(nested.get(key))
        if ctx is not None:
            break
    for key in out_keys:
        out = _coerce_positive_int(payload.get(key)) or _coerce_positive_int(nested.get(key))
        if out is not None:
            break
    if ctx is None and out is None:
        return None
    return (
        ctx or MODEL_TOKEN_DEFAULT_CONTEXT,
        out or MODEL_TOKEN_DEFAULT_MAX_OUTPUT,
    )


def fetch_provider_token_profile(
    model_id: str,
    *,
    provider: str = "",
    api_key: str = "",
    base_url: str = "",
    timeout_s: float = MODEL_TOKEN_PROFILE_API_TIMEOUT_S,
) -> ModelTokenProfile | None:
    """Tier-1: silent provider metadata probe. Never raises."""
    mid = (model_id or "").strip()
    if not mid:
        return None
    kind = (provider or "").strip().lower()
    if kind == LLM_PROVIDER_OFFLINE:
        return None
    try:
        if kind == LLM_PROVIDER_GEMINI:
            return _fetch_gemini_token_profile(mid, api_key=api_key, timeout_s=timeout_s)
        ollama = _fetch_ollama_show(mid, base_url=base_url, timeout_s=timeout_s)
        if ollama is not None:
            return ollama
        return _fetch_openai_compat_token_profile(
            mid, api_key=api_key, base_url=base_url, timeout_s=timeout_s
        )
    except Exception as exc:  # noqa: BLE001 — silent tier-1 failure
        logger.debug("model_token_api_probe_failed model=%s err=%s", mid, exc)
        return None


def _fetch_ollama_show(
    model_id: str, *, base_url: str, timeout_s: float
) -> ModelTokenProfile | None:
    cleaned = (base_url or "").strip().rstrip("/")
    if not cleaned:
        return None
    # Ollama OpenAI-compat base is often http://host:11434/v1 → strip /v1 for /api/show.
    root = cleaned[:-3] if cleaned.endswith("/v1") else cleaned
    url = f"{root}/api/show"
    payload = json.dumps({"name": model_id}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:  # noqa: S310 — local provider
            body = resp.read()
        data = json.loads(body.decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError, ValueError):
        return None
    limits = _extract_limits_from_mapping(data)
    if limits is None:
        return None
    ctx, out = limits
    return ModelTokenProfile(
        context_window=ctx, max_output_tokens=out, source=MODEL_TOKEN_SOURCE_API
    )


def _fetch_openai_compat_token_profile(
    model_id: str,
    *,
    api_key: str,
    base_url: str,
    timeout_s: float,
) -> ModelTokenProfile | None:
    key = (api_key or "").strip()
    if not key:
        return None
    try:
        from openai import OpenAI
    except ImportError:
        return None
    kwargs: dict[str, Any] = {"api_key": key, "timeout": timeout_s}
    cleaned = (base_url or "").strip().rstrip("/")
    if cleaned:
        kwargs["base_url"] = cleaned
    client = OpenAI(**kwargs)
    try:
        model_obj = client.models.retrieve(model_id)
    except Exception:  # noqa: BLE001
        try:
            for page in client.models.list():
                mid = getattr(page, "id", None)
                if isinstance(mid, str) and mid.strip() == model_id:
                    model_obj = page
                    break
            else:
                return None
        except Exception:  # noqa: BLE001
            return None
    limits = _extract_limits_from_mapping(model_obj)
    if limits is None:
        return None
    ctx, out = limits
    return ModelTokenProfile(
        context_window=ctx, max_output_tokens=out, source=MODEL_TOKEN_SOURCE_API
    )


def _fetch_gemini_token_profile(
    model_id: str, *, api_key: str, timeout_s: float
) -> ModelTokenProfile | None:
    key = (api_key or "").strip()
    if not key:
        return None
    _ = timeout_s
    try:
        from google import genai
    except ImportError:
        return None
    client = genai.Client(api_key=key)
    name = model_id if model_id.startswith("models/") else f"models/{model_id}"
    try:
        info = client.models.get(model=name)
    except Exception:  # noqa: BLE001
        return None
    limits = _extract_limits_from_mapping(info)
    if limits is None:
        return None
    ctx, out = limits
    return ModelTokenProfile(
        context_window=ctx, max_output_tokens=out, source=MODEL_TOKEN_SOURCE_API
    )


def resolve_model_token_profile(
    model_id: str,
    *,
    provider: str = "",
    api_key: str = "",
    base_url: str = "",
    cache: MutableMapping[str, Any] | None = None,
    skip_api: bool = False,
    timeout_s: float = MODEL_TOKEN_PROFILE_API_TIMEOUT_S,
    fetch_fn: Callable[..., ModelTokenProfile | None] | None = None,
) -> ModelTokenProfile:
    """Four-tier resolve: API → catalog → cache → safe default. Never None/0."""
    mid = (model_id or "").strip()
    safe = ModelTokenProfile(
        context_window=MODEL_TOKEN_DEFAULT_CONTEXT,
        max_output_tokens=MODEL_TOKEN_DEFAULT_MAX_OUTPUT,
        source=MODEL_TOKEN_SOURCE_DEFAULT,
    )
    if not mid:
        return safe

    # Tier 1 — Provider API (silent failures).
    if not skip_api:
        probe = fetch_fn or fetch_provider_token_profile
        try:
            api_profile = probe(
                mid,
                provider=provider,
                api_key=api_key,
                base_url=base_url,
                timeout_s=timeout_s,
            )
        except Exception as exc:  # noqa: BLE001
            logger.debug("model_token_resolve_api_err model=%s err=%s", mid, exc)
            api_profile = None
        if (
            api_profile is not None
            and api_profile.context_window > 0
            and api_profile.max_output_tokens > 0
        ):
            resolved = ModelTokenProfile(
                context_window=int(api_profile.context_window),
                max_output_tokens=int(api_profile.max_output_tokens),
                source=MODEL_TOKEN_SOURCE_API,
            )
            _store_cache(cache, mid, resolved)
            return resolved

    # Tier 2 — Internal catalog by prefix.
    catalog = match_catalog_token_profile(mid)
    if catalog is not None:
        _store_cache(cache, mid, catalog)
        return catalog

    # Tier 3 — settings.json model_profiles_cache.
    cached = _cache_lookup(cache, mid)
    if cached is not None:
        return cached

    # Tier 4 — Safe default (never 0 / None).
    return safe


def _resolve_token_encoding(model: str) -> tiktoken.Encoding | None:
    """Resolve a tiktoken encoding, degrading to ``None`` when unavailable.

    ``encoding_for_model`` / ``get_encoding`` raise ``ValueError`` (not only
    ``KeyError``) when the ``tiktoken_ext`` plugin modules or the BPE rank data
    cannot be loaded — e.g. a frozen build that did not bundle the namespace
    plugin, or a first launch without network access to download the ranks.
    """
    resolvers: tuple[Callable[[], tiktoken.Encoding], ...] = (
        lambda: tiktoken.encoding_for_model(model),
        lambda: tiktoken.get_encoding(TOKEN_ENCODING_FALLBACK),
    )
    for resolve in resolvers:
        try:
            return resolve()
        except Exception as exc:  # noqa: BLE001 — any tiktoken failure must not block UI
            logger.warning(
                "token_encoding_unavailable model=%s err=%s", model, str(exc).replace("\n", " ")
            )
    logger.warning("token_encoding_heuristic_fallback model=%s", model)
    return None


def _estimate_tokens(text: str) -> int:
    if not text:
        return 0
    return (len(text) + TOKEN_FALLBACK_CHARS_PER_TOKEN - 1) // TOKEN_FALLBACK_CHARS_PER_TOKEN


class TokenBudget:
    def __init__(self, model: str = TOKEN_MODEL_DEFAULT) -> None:
        self._enc: tiktoken.Encoding | None = _resolve_token_encoding(model)
        self.max_atomic_output = TOKEN_MAX_ATOMIC_OUTPUT
        self.dom_soft_cap = TOKEN_DOM_SOFT_CAP

    def count(self, text: str) -> int:
        if self._enc is None:
            return _estimate_tokens(text or "")
        return len(self._enc.encode(text or ""))

    def truncate_to_cap(self, text: str, cap: int | None = None) -> str:
        limit = cap or self.dom_soft_cap
        if limit <= 0:
            return ""
        if self._enc is None:
            return (text or "")[: limit * TOKEN_FALLBACK_CHARS_PER_TOKEN]
        tokens = self._enc.encode(text or "")
        if len(tokens) <= limit:
            return text
        return self._enc.decode(tokens[:limit])

    def summarize_history_line(self, text: str) -> str:
        compact = " ".join((text or "").split())
        if len(compact) <= SLIDING_HISTORY_SUMMARY_CHARS:
            return compact
        return compact[: SLIDING_HISTORY_SUMMARY_CHARS - 1] + "…"


class ContextCompressor:
    """In-process headroom + markitdown used by bundled MCP tools."""

    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()
        self.last_engine = "truncate"

    def compress_text(self, text: str) -> str:
        try:
            from headroom import crush
        except Exception:
            try:
                import headroom_ai

                crush = getattr(headroom_ai, "crush", None)
            except Exception:
                crush = None
        if callable(crush):
            try:
                result = crush(text)
                if isinstance(result, str):
                    self.last_engine = "headroom"
                    return self.budget.truncate_to_cap(result)
            except Exception as exc:  # noqa: BLE001 — fallback to local truncate
                logger.warning("headroom_compress_failed err=%s", exc)
        self.last_engine = "truncate"
        return self.budget.truncate_to_cap(text)

    def document_to_markdown(self, path: Path) -> str:
        try:
            from markitdown import MarkItDown
        except Exception as exc:  # noqa: BLE001
            logger.error("markitdown_unavailable err=%s", exc)
            raise RuntimeError("markitdown is required for document conversion") from exc
        md = MarkItDown()
        result = md.convert(str(path))
        text = getattr(result, "text_content", None) or str(result)
        return self.budget.truncate_to_cap(text, cap=TOKEN_CONTEXT_BUDGET)

    def sliding_messages(self, messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if not messages:
            return []
        *history, current = messages
        compacted: list[dict[str, Any]] = []
        for item in history:
            content = str(item.get("content") or "")
            compacted.append(
                {
                    "role": item.get("role", "user"),
                    "content": self.budget.summarize_history_line(content),
                }
            )
        compacted.append(current)
        return compacted


class ContextCompactor:
    """Pin System+TestCase, latest full browser_snapshot, and last N tool pairs.

    Older ``browser_snapshot`` payloads (from t-2 backward) are stubbed; other
    history is summarized / compressed under budget.
    """

    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()
        self._inner = ContextCompressor(self.budget)

    @staticmethod
    def pin(text: str, label: str = "ctx") -> str:
        safe = (label or "ctx").strip() or "ctx"
        return f"{COMPACTOR_PIN_PREFIX}{safe}{COMPACTOR_PIN_SUFFIX}\n{text}"

    @staticmethod
    def is_pinned(content: str) -> bool:
        return (content or "").lstrip().startswith(COMPACTOR_PIN_PREFIX)

    @staticmethod
    def _is_system(role: str, content: str) -> bool:
        if role.lower() in COMPACTOR_SYSTEM_ROLES:
            return True
        return ContextCompactor.is_pinned(content) and "system" in content.lstrip()[
            : len(COMPACTOR_PIN_PREFIX) + 16
        ].lower()

    @staticmethod
    def _is_testcase(content: str) -> bool:
        head = (content or "").lstrip().lower()
        if ContextCompactor.is_pinned(content):
            return any(m in head[:80] for m in ("testcase", "test_case", "case"))
        return any(m in head[:120] for m in COMPACTOR_TESTCASE_MARKERS)

    @staticmethod
    def _is_snapshot(content: str) -> bool:
        return COMPACTOR_SNAPSHOT_MARKER in (content or "").lower()

    @staticmethod
    def _is_tool_message(role: str, content: str) -> bool:
        if role.lower() in COMPACTOR_TOOL_ROLE_NAMES:
            return True
        lowered = (content or "").lower()
        return any(marker in lowered for marker in COMPACTOR_TOOL_CONTENT_MARKERS)

    @staticmethod
    def _snapshot_stub(age: int) -> str:
        return COMPACTOR_SNAPSHOT_STUB_TEMPLATE.format(
            marker=COMPACTOR_SNAPSHOT_MARKER,
            age=age,
        )

    def drop_intermediate_crew_drafts(
        self,
        messages: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        """Keep user + final Composer result; drop Planner/Critic/mid-draft bubbles."""
        if not messages:
            return []
        last_final_idx: int | None = None
        for idx in range(len(messages) - 1, -1, -1):
            role = str(messages[idx].get("role") or "").lower()
            content = str(messages[idx].get("content") or "")
            if role == ROLE_COMPOSER or (
                role in COMPACTOR_CREW_FINAL_ROLES and '"scenarios"' in content
            ):
                last_final_idx = idx
                break
        if last_final_idx is None:
            for idx in range(len(messages) - 1, -1, -1):
                role = str(messages[idx].get("role") or "").lower()
                if role in COMPACTOR_CREW_FINAL_ROLES or role == ROLE_COMPOSER:
                    last_final_idx = idx
                    break
        if last_final_idx is None:
            return list(messages)
        out: list[dict[str, Any]] = []
        for idx, item in enumerate(messages):
            role = str(item.get("role") or "").lower()
            if role == "user" or self._is_system(role, str(item.get("content") or "")):
                out.append(item)
                continue
            if role in COMPACTOR_CREW_DRAFT_ROLES and idx != last_final_idx:
                continue
            if idx == last_final_idx or role not in COMPACTOR_CREW_DRAFT_ROLES:
                out.append(item)
        return out

    def compact_messages(
        self,
        messages: list[dict[str, Any]],
        *,
        soft_cap: int | None = None,
    ) -> list[dict[str, Any]]:
        if not messages:
            return []
        messages = self.drop_intermediate_crew_drafts(messages)
        cap = soft_cap or TOKEN_CONTEXT_BUDGET
        *history, current = messages

        snapshot_idxs = [
            i for i, item in enumerate(history) if self._is_snapshot(str(item.get("content") or ""))
        ]
        # age 0 = latest; stub when age >= COMPACTOR_SNAPSHOT_STUB_FROM_AGE (t-2+)
        snapshot_age: dict[int, int] = {
            snap_i: len(snapshot_idxs) - 1 - pos for pos, snap_i in enumerate(snapshot_idxs)
        }
        keep_snapshot = {
            snap_i
            for snap_i, age in snapshot_age.items()
            if age < COMPACTOR_SNAPSHOT_STUB_FROM_AGE
        }

        tool_idxs = [
            i
            for i, item in enumerate(history)
            if self._is_tool_message(
                str(item.get("role") or "user"),
                str(item.get("content") or ""),
            )
            and not self._is_snapshot(str(item.get("content") or ""))
        ]
        keep_tool = set(tool_idxs[-COMPACTOR_KEEP_LAST_TOOL_PAIRS :])

        out: list[dict[str, Any]] = []
        for idx, item in enumerate(history):
            role = str(item.get("role") or "user")
            content = str(item.get("content") or "")
            if self._is_snapshot(content) and idx not in keep_snapshot:
                age = snapshot_age.get(idx, COMPACTOR_SNAPSHOT_STUB_FROM_AGE)
                out.append({"role": role, "content": self._snapshot_stub(age)})
                continue
            keep_full = (
                self.is_pinned(content)
                or self._is_system(role, content)
                or self._is_testcase(content)
                or idx in keep_snapshot
                or idx in keep_tool
            )
            if keep_full:
                out.append({"role": role, "content": content})
                continue
            out.append(
                {
                    "role": role,
                    "content": self.budget.summarize_history_line(content),
                }
            )
        out.append(current)

        blob = "\n".join(str(m.get("content") or "") for m in out)
        if self.budget.count(blob) <= cap:
            return out

        trimmed: list[dict[str, Any]] = []
        for idx, item in enumerate(out):
            content = str(item.get("content") or "")
            role = str(item.get("role") or "user")
            if (
                idx == len(out) - 1
                or self.is_pinned(content)
                or self._is_system(role, content)
                or self._is_testcase(content)
                or self._is_snapshot(content)
                or self._is_tool_message(role, content)
            ):
                trimmed.append(item)
            else:
                trimmed.append(
                    {
                        "role": role,
                        "content": self._inner.compress_text(content),
                    }
                )
        return trimmed
