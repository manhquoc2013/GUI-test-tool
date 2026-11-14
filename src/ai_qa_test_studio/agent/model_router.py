"""Resolve (task, complexity) → connection + model for UnifiedLLMGateway."""

from __future__ import annotations

from dataclasses import dataclass

from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
from ai_qa_test_studio.constants import (
    COMPLEXITY_NORMAL,
    COPILOT_MODEL_AUTO,
    COPILOT_MODEL_LABEL_AUTO,
    LLM_PROVIDER_OFFLINE,
    LLM_TIER_FAST,
    LLM_TIER_MID,
    LLM_TIER_STRONG,
    ROUTING_MODE_AUTO,
    ROUTING_MODE_MANUAL,
)
from ai_qa_test_studio.core.env_manager import default_task_complexity_matrix
from ai_qa_test_studio.core.models import AiConnection, AiModelEntry, AiSettings
from ai_qa_test_studio.core.secret_store import resolve_api_key


@dataclass(frozen=True)
class ModelRoute:
    connection_id: str
    provider: str
    model: str
    base_url: str
    api_key: str
    tier: str
    complexity: str
    task: str

    def gateway(self) -> UnifiedLLMGateway:
        return UnifiedLLMGateway(
            provider=self.provider,
            api_key=self.api_key,
            model=self.model,
            base_url=self.base_url or None,
        )

    @property
    def ref(self) -> str:
        return f"{self.connection_id}::{self.model}"


@dataclass
class ModelRouter:
    settings: AiSettings
    pin_ref: str = ""

    def catalog_labels(self) -> list[tuple[str, str]]:
        """Return (ref, display) for Copilot combo."""
        items: list[tuple[str, str]] = [(COPILOT_MODEL_AUTO, COPILOT_MODEL_LABEL_AUTO)]
        for conn in self.settings.connections:
            if not conn.enabled:
                continue
            for model in conn.models:
                ref = f"{conn.id}::{model.id}"
                label = f"{conn.label or conn.id} / {model.label or model.id}"
                items.append((ref, label))
        return items

    def resolve(self, task: str, complexity: str = "normal") -> ModelRoute:
        pin = (self.pin_ref or "").strip()
        if pin and pin != COPILOT_MODEL_AUTO and "::" in pin:
            return self._from_ref(pin, task=task, complexity=complexity, tier="manual")
        mode = (self.settings.routing.mode or ROUTING_MODE_AUTO).lower()
        manual = (self.settings.routing.manual_model_ref or "").strip()
        if mode == ROUTING_MODE_MANUAL and manual and "::" in manual:
            return self._from_ref(manual, task=task, complexity=complexity, tier="manual")

        # Settings "Bật chọn model theo độ phức tạp" off → always treat as normal.
        effective = (
            complexity
            if self.settings.routing.complexity_enabled
            else COMPLEXITY_NORMAL
        )
        tier = self._tier_for(task, effective)
        complexity = effective
        conn, model = self._pick_by_tier(tier)
        if conn is None or model is None:
            return self._offline_route(task, complexity, tier)
        provider = conn.provider or LLM_PROVIDER_OFFLINE
        api_key = resolve_api_key(conn.api_key_ref)
        if provider != LLM_PROVIDER_OFFLINE and not api_key:
            return self._offline_route(task, complexity, tier)
        return ModelRoute(
            connection_id=conn.id,
            provider=provider,
            model=model.id,
            base_url=conn.base_url,
            api_key=api_key,
            tier=tier,
            complexity=complexity,
            task=task,
        )

    def _tier_for(self, task: str, complexity: str) -> str:
        matrix = self.settings.routing.task_complexity_models or default_task_complexity_matrix()
        row = matrix.get(task) or {}
        value = row.get(complexity) or row.get("normal") or LLM_TIER_MID
        if value in {LLM_TIER_FAST, LLM_TIER_MID, LLM_TIER_STRONG}:
            return value
        if "::" in value:
            # Direct model override stored in matrix cell
            return value
        return LLM_TIER_MID

    def _pick_by_tier(self, tier_or_ref: str) -> tuple[AiConnection | None, AiModelEntry | None]:
        if "::" in tier_or_ref:
            route = self._from_ref(tier_or_ref, task="", complexity="", tier="override")
            conn = self._connection(route.connection_id)
            model = next((m for m in (conn.models if conn else []) if m.id == route.model), None)
            return conn, model
        default_id = self.settings.default_connection_id
        ordered = sorted(
            [c for c in self.settings.connections if c.enabled],
            key=lambda c: 0 if c.id == default_id else 1,
        )
        # Prefer default connection matching tier
        for conn in ordered:
            match = next((m for m in conn.models if m.tier == tier_or_ref), None)
            if match:
                return conn, match
        # Fallback default model on default connection
        for conn in ordered:
            default_model = next((m for m in conn.models if m.is_default), None)
            if default_model:
                return conn, default_model
            if conn.models:
                return conn, conn.models[0]
        return None, None

    def _from_ref(self, ref: str, *, task: str, complexity: str, tier: str) -> ModelRoute:
        conn_id, model_id = ref.split("::", 1)
        conn = self._connection(conn_id)
        if conn is None:
            return self._offline_route(task, complexity, tier)
        model = next((m for m in conn.models if m.id == model_id), None)
        if model is None:
            return self._offline_route(task, complexity, tier)
        provider = conn.provider or LLM_PROVIDER_OFFLINE
        api_key = resolve_api_key(conn.api_key_ref)
        if provider != LLM_PROVIDER_OFFLINE and not api_key:
            return self._offline_route(task, complexity, tier)
        return ModelRoute(
            connection_id=conn.id,
            provider=provider,
            model=model.id,
            base_url=conn.base_url,
            api_key=api_key,
            tier=tier,
            complexity=complexity,
            task=task,
        )

    def _connection(self, conn_id: str) -> AiConnection | None:
        return next((c for c in self.settings.connections if c.id == conn_id), None)

    def _offline_route(self, task: str, complexity: str, tier: str) -> ModelRoute:
        return ModelRoute(
            connection_id="offline",
            provider=LLM_PROVIDER_OFFLINE,
            model="offline",
            base_url="",
            api_key="",
            tier=tier,
            complexity=complexity,
            task=task,
        )
