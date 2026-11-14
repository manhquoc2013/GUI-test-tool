"""Settings + environment variable resolution."""

from __future__ import annotations

import json
import logging
import uuid
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import (
    APP_STUDIO_DB,
    COMPLEXITY_COMPLEX,
    COMPLEXITY_NORMAL,
    COMPLEXITY_SIMPLE,
    KEYRING_SLOT_LEGACY_API,
    LLM_PROVIDER_OFFLINE,
    LLM_TASK_ANALYZE,
    LLM_TASK_CLARIFY_DRAFT,
    LLM_TASK_COMPOSE,
    LLM_TASK_COORDINATE,
    LLM_TASK_CRITIQUE,
    LLM_TASK_DEBATE,
    LLM_TASK_INTENT,
    LLM_TASK_OPTIMIZE,
    LLM_TASK_PLAN,
    LLM_TASK_REVISE,
    LLM_TASK_SUMMARIZE,
    LLM_TIER_FAST,
    LLM_TIER_MID,
    LLM_TIER_STRONG,
    SETTINGS_FILENAME,
    THEME_PREF_AUTO,
)
from ai_qa_test_studio.core.async_bridge import run_sync
from ai_qa_test_studio.core.models import (
    AiConnection,
    AiModelEntry,
    AiRoutingSettings,
    AiSettings,
    AppSettings,
    CopilotRuntimeSettings,
    EnvironmentProfile,
    McpExternalSettings,
    McpSettings,
    TestDataProfile,
)
from ai_qa_test_studio.core.secret_store import is_keyring_ref, seal_api_key_ref
from ai_qa_test_studio.core.sqlite_repository import SqliteRepository

logger = logging.getLogger(__name__)


def default_task_complexity_matrix() -> dict[str, dict[str, str]]:
    return {
        LLM_TASK_SUMMARIZE: {
            COMPLEXITY_SIMPLE: LLM_TIER_FAST,
            COMPLEXITY_NORMAL: LLM_TIER_FAST,
            COMPLEXITY_COMPLEX: LLM_TIER_MID,
        },
        LLM_TASK_CLARIFY_DRAFT: {
            COMPLEXITY_SIMPLE: LLM_TIER_FAST,
            COMPLEXITY_NORMAL: LLM_TIER_FAST,
            COMPLEXITY_COMPLEX: LLM_TIER_MID,
        },
        LLM_TASK_INTENT: {
            COMPLEXITY_SIMPLE: LLM_TIER_FAST,
            COMPLEXITY_NORMAL: LLM_TIER_FAST,
            COMPLEXITY_COMPLEX: LLM_TIER_FAST,
        },
        LLM_TASK_COORDINATE: {
            COMPLEXITY_SIMPLE: LLM_TIER_FAST,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            COMPLEXITY_COMPLEX: LLM_TIER_MID,
        },
        LLM_TASK_PLAN: {
            COMPLEXITY_SIMPLE: LLM_TIER_FAST,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            # Ruflo/ECC: architecture-like planning → strong when hard.
            COMPLEXITY_COMPLEX: LLM_TIER_STRONG,
        },
        LLM_TASK_COMPOSE: {
            # Ruflo QE: unit-ish → mid; suite/E2E → strong.
            COMPLEXITY_SIMPLE: LLM_TIER_MID,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            COMPLEXITY_COMPLEX: LLM_TIER_STRONG,
        },
        LLM_TASK_REVISE: {
            COMPLEXITY_SIMPLE: LLM_TIER_MID,
            COMPLEXITY_NORMAL: LLM_TIER_STRONG,
            COMPLEXITY_COMPLEX: LLM_TIER_STRONG,
        },
        LLM_TASK_CRITIQUE: {
            COMPLEXITY_SIMPLE: LLM_TIER_MID,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            COMPLEXITY_COMPLEX: LLM_TIER_STRONG,
        },
        LLM_TASK_DEBATE: {
            COMPLEXITY_SIMPLE: LLM_TIER_MID,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            COMPLEXITY_COMPLEX: LLM_TIER_STRONG,
        },
        LLM_TASK_ANALYZE: {
            COMPLEXITY_SIMPLE: LLM_TIER_MID,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            COMPLEXITY_COMPLEX: LLM_TIER_STRONG,
        },
        LLM_TASK_OPTIMIZE: {
            COMPLEXITY_SIMPLE: LLM_TIER_FAST,
            COMPLEXITY_NORMAL: LLM_TIER_MID,
            COMPLEXITY_COMPLEX: LLM_TIER_MID,
        },
    }


def migrate_ai_config(ai_raw: dict[str, Any]) -> AiSettings:
    """Build AiSettings from flat or multi-connection JSON."""
    connections_raw = ai_raw.get("connections")
    if isinstance(connections_raw, list) and connections_raw:
        connections: list[AiConnection] = []
        for item in connections_raw:
            if not isinstance(item, dict):
                continue
            models = [
                AiModelEntry(
                    id=str(m.get("id") or ""),
                    label=str(m.get("label") or m.get("id") or ""),
                    tier=str(m.get("tier") or LLM_TIER_MID),
                    is_default=bool(m.get("isDefault", m.get("is_default", False))),
                )
                for m in (item.get("models") or [])
                if isinstance(m, dict) and str(m.get("id") or "")
            ]
            connections.append(
                AiConnection(
                    id=str(item.get("id") or uuid.uuid4()),
                    label=str(item.get("label") or "API"),
                    provider=str(item.get("provider") or LLM_PROVIDER_OFFLINE),
                    base_url=str(item.get("baseUrl") or item.get("base_url") or ""),
                    api_key_ref=str(item.get("apiKeyRef") or item.get("apiKey") or item.get("api_key") or ""),
                    enabled=bool(item.get("enabled", True)),
                    models=models,
                )
            )
        routing_raw = ai_raw.get("routing") or {}
        matrix = routing_raw.get("taskComplexityModels") or routing_raw.get("task_complexity_models")
        routing = AiRoutingSettings(
            mode=str(routing_raw.get("mode") or "auto"),
            manual_model_ref=str(
                routing_raw.get("manualModelRef") or routing_raw.get("manual_model_ref") or ""
            ),
            complexity_enabled=bool(
                routing_raw.get("complexityEnabled", routing_raw.get("complexity_enabled", True))
            ),
            task_complexity_models=matrix
            if isinstance(matrix, dict) and matrix
            else default_task_complexity_matrix(),
        )
        default_id = str(ai_raw.get("defaultConnectionId") or ai_raw.get("default_connection_id") or "")
        if not default_id and connections:
            default_id = connections[0].id
        primary = next((c for c in connections if c.id == default_id), connections[0])
        default_model = next((m for m in primary.models if m.is_default), None)
        if default_model is None and primary.models:
            default_model = primary.models[0]
        return AiSettings(
            api_key_ref=primary.api_key_ref,
            base_url=primary.base_url,
            model=default_model.id if default_model else "",
            provider=primary.provider,
            temperature=float(ai_raw.get("temperature") or 0.2),
            max_tokens=int(ai_raw.get("maxTokens") or ai_raw.get("max_tokens") or 512),
            connections=connections,
            default_connection_id=default_id,
            routing=routing,
        )

    api_key = str(ai_raw.get("apiKey") or ai_raw.get("api_key") or "")
    base_url = str(ai_raw.get("baseUrl") or ai_raw.get("base_url") or "")
    model = str(ai_raw.get("model") or "offline")
    provider = str(ai_raw.get("provider") or (LLM_PROVIDER_OFFLINE if not api_key else "openai_compatible"))
    conn_id = str(uuid.uuid4())
    connection = AiConnection(
        id=conn_id,
        label="Default",
        provider=provider or LLM_PROVIDER_OFFLINE,
        base_url=base_url,
        api_key_ref=api_key,
        enabled=True,
        models=[
            AiModelEntry(id=model or "offline", label=model or "Offline", tier=LLM_TIER_MID, is_default=True)
        ],
    )
    return AiSettings(
        api_key_ref=api_key,
        base_url=base_url,
        model=model,
        provider=provider,
        temperature=float(ai_raw.get("temperature") or 0.2),
        max_tokens=int(ai_raw.get("maxTokens") or ai_raw.get("max_tokens") or 512),
        connections=[connection],
        default_connection_id=conn_id,
        routing=AiRoutingSettings(task_complexity_models=default_task_complexity_matrix()),
    )


def _seal_ai_secrets(ai: AiSettings) -> None:
    """Replace raw API keys in-memory with keyring refs before JSON persistence."""
    for conn in ai.connections:
        conn.api_key_ref = seal_api_key_ref(conn.id, conn.api_key_ref)
    legacy_slot = ai.default_connection_id or KEYRING_SLOT_LEGACY_API
    ai.api_key_ref = seal_api_key_ref(legacy_slot, ai.api_key_ref)


def ai_settings_to_payload(ai: AiSettings) -> dict[str, Any]:
    _seal_ai_secrets(ai)
    return {
        "apiKey": ai.api_key_ref,
        "baseUrl": ai.base_url,
        "model": ai.model,
        "provider": ai.provider,
        "temperature": ai.temperature,
        "maxTokens": ai.max_tokens,
        "defaultConnectionId": ai.default_connection_id,
        "connections": [
            {
                "id": c.id,
                "label": c.label,
                "provider": c.provider,
                "baseUrl": c.base_url,
                "apiKeyRef": c.api_key_ref,
                "enabled": c.enabled,
                "models": [
                    {
                        "id": m.id,
                        "label": m.label,
                        "tier": m.tier,
                        "isDefault": m.is_default,
                    }
                    for m in c.models
                ],
            }
            for c in ai.connections
        ],
        "routing": {
            "mode": ai.routing.mode,
            "manualModelRef": ai.routing.manual_model_ref,
            "complexityEnabled": ai.routing.complexity_enabled,
            "taskComplexityModels": ai.routing.task_complexity_models
            or default_task_complexity_matrix(),
        },
    }


class EnvManager:
    def __init__(self, data_root: Path) -> None:
        self.data_root = data_root
        self.settings_path = data_root / SETTINGS_FILENAME
        self.db_path = data_root / APP_STUDIO_DB
        self._repo = SqliteRepository(self.db_path)
        self._settings = AppSettings()
        self._profiles: dict[str, TestDataProfile] = {}

    @property
    def settings(self) -> AppSettings:
        return self._settings

    def load_settings(self) -> AppSettings:
        payload = self._load_settings_payload()
        if payload is None:
            self._settings = AppSettings(ai=migrate_ai_config({}), theme=THEME_PREF_AUTO)
            return self._settings
        self._settings = self._settings_from_payload(payload)
        if self._has_plaintext_api_keys():
            self.save_settings()
        return self._settings

    def _load_settings_payload(self) -> dict[str, Any] | None:
        async def _from_db() -> dict[str, Any] | None:
            await self._repo.open()
            try:
                return await self._repo.get_app_settings_blob()
            finally:
                await self._repo.close()

        blob = run_sync(_from_db())
        if blob is not None:
            return blob
        if not self.settings_path.is_file():
            return None
        try:
            raw = json.loads(self.settings_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            logger.error("settings_json_load_failed path=%s err=%s", self.settings_path, exc)
            return None
        if not isinstance(raw, dict):
            return None
        # One-shot migrate JSON → SQLite (primary store).
        self._settings = self._settings_from_payload(raw)
        self.save_settings()
        return self._settings_to_payload()

    def _settings_from_payload(self, payload: dict[str, Any]) -> AppSettings:
        ai_raw = payload.get("aiConfig") or payload.get("ai") or {}
        copilot_raw = payload.get("copilot") or {}
        mcp_raw = payload.get("mcp") or {}
        ext = mcp_raw.get("external") or {}
        return AppSettings(
            browser=str(payload.get("browser") or "CHROMIUM"),
            headless_default=bool(
                payload.get("headlessDefault", payload.get("headless_default", False))
            ),
            theme=str(payload.get("theme") or THEME_PREF_AUTO),
            ai=migrate_ai_config(ai_raw if isinstance(ai_raw, dict) else {}),
            active_environment=str(payload.get("active_environment") or "DEV"),
            copilot=CopilotRuntimeSettings(
                summary_max_chars=int(copilot_raw.get("summaryMaxChars") or 160),
                debate_max_rounds=int(copilot_raw.get("debateMaxRounds") or 3),
                loop_repeat_limit=int(copilot_raw.get("loopRepeatLimit") or 2),
                clarify_timeout_ms=int(copilot_raw.get("clarifyTimeoutMs") or 60_000),
                lean_tail_max_tokens=int(copilot_raw.get("leanTailMaxTokens") or 2400),
                analyzer_enabled=bool(copilot_raw.get("analyzerEnabled", True)),
                optimizer_enabled=bool(copilot_raw.get("optimizerEnabled", True)),
            ),
            mcp=McpSettings(
                external=McpExternalSettings(
                    playwright=bool(ext.get("playwright", True)),
                    midscene=bool(ext.get("midscene", True)),
                    figma=bool(ext.get("figma", False)),
                    db=bool(ext.get("db", False)),
                )
            ),
        )

    def _settings_to_payload(self) -> dict[str, Any]:
        return {
            "browser": self._settings.browser,
            "headlessDefault": self._settings.headless_default,
            "theme": self._settings.theme,
            "active_environment": self._settings.active_environment,
            "aiConfig": ai_settings_to_payload(self._settings.ai),
            "copilot": {
                "summaryMaxChars": self._settings.copilot.summary_max_chars,
                "debateMaxRounds": self._settings.copilot.debate_max_rounds,
                "loopRepeatLimit": self._settings.copilot.loop_repeat_limit,
                "clarifyTimeoutMs": self._settings.copilot.clarify_timeout_ms,
                "leanTailMaxTokens": self._settings.copilot.lean_tail_max_tokens,
                "analyzerEnabled": self._settings.copilot.analyzer_enabled,
                "optimizerEnabled": self._settings.copilot.optimizer_enabled,
            },
            "mcp": {
                "external": {
                    "playwright": self._settings.mcp.external.playwright,
                    "midscene": self._settings.mcp.external.midscene,
                    "figma": self._settings.mcp.external.figma,
                    "db": self._settings.mcp.external.db,
                }
            },
        }

    def _has_plaintext_api_keys(self) -> bool:
        ai = self._settings.ai
        refs = [ai.api_key_ref, *(c.api_key_ref for c in ai.connections)]
        return any(bool(r) and not is_keyring_ref(r) for r in refs)

    def save_settings(self) -> None:
        payload = self._settings_to_payload()
        try:
            from ai_qa_test_studio.agent.mcp_defaults import sync_mcp_servers_disabled

            sync_mcp_servers_disabled(self._settings.mcp.external)
        except Exception as exc:  # noqa: BLE001
            logger.warning("mcp_servers_sync_failed err=%s", exc)

        async def _persist() -> None:
            await self._repo.open()
            try:
                await self._repo.set_app_settings_blob(payload)
            finally:
                await self._repo.close()

        run_sync(_persist())
        # Keep a sealed JSON mirror for operators / emergency recovery (DB remains primary).
        self.data_root.mkdir(parents=True, exist_ok=True)
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
        return result
