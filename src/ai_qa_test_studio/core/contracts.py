"""Shared type hub — re-exports models/enums + Protocols (no Fingerprint body here)."""

from __future__ import annotations

from collections.abc import Awaitable, Callable, Coroutine
from typing import Any, Protocol, runtime_checkable

from ai_qa_test_studio.core.enums import (
    DeliveryMode,
    IntentType,
    StepStatus,
    ThinkingLevel,
)
from ai_qa_test_studio.core.models import (
    BindingStatus,
    CaseRunResult,
    CaseStatus,
    DistilledNode,
    ElementFingerprint,
    RunReport,
    RunStepResult,
    SharedElement,
    StepAuditEntry,
    StepResult,
    TestCase,
    TestStep,
)

__all__ = [
    "AsyncTaskSupervisorProtocol",
    "BindingStatus",
    "CaseRunResult",
    "CaseStatus",
    "DeliveryMode",
    "DistilledNode",
    "ElementFingerprint",
    "IntentType",
    "MCPToolInterceptorProtocol",
    "RunReport",
    "RunStepResult",
    "SecretMaskerProtocol",
    "SharedElement",
    "StepAuditEntry",
    "StepResult",
    "StepStatus",
    "TestCase",
    "TestStep",
    "ThinkingLevel",
]


@runtime_checkable
class SecretMaskerProtocol(Protocol):
    def mask_text(self, text: str) -> str: ...

    def mask_mapping(self, data: dict[str, Any]) -> dict[str, Any]: ...

    def mask_snapshot(self, snapshot: str) -> str: ...


@runtime_checkable
class MCPToolInterceptorProtocol(Protocol):
    async def before_call(
        self, tool: str, arguments: dict[str, Any]
    ) -> dict[str, Any]: ...

    async def after_error(
        self, tool: str, arguments: dict[str, Any], error: BaseException
    ) -> None: ...

    async def after_success(
        self, tool: str, arguments: dict[str, Any], result: dict[str, Any]
    ) -> None: ...


@runtime_checkable
class AsyncTaskSupervisorProtocol(Protocol):
    def bind_loop(self) -> None: ...

    def register(
        self,
        coro: Coroutine[Any, Any, Any],
        *,
        key: str,
        replace: bool = True,
    ) -> Any: ...

    async def cancel_all(self) -> None: ...

    async def wait(self, key: str | None = None) -> None: ...

    def emit_if_alive(
        self,
        widget: Any,
        signal: Any,
        *args: Any,
    ) -> None: ...


CancelFn = Callable[[], bool]
VisionLocateFn = Callable[[str], Awaitable[dict[str, Any] | None]]
