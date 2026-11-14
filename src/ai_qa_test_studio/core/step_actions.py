"""Copilot / Excel step-action inference and target labeling."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.core.models import StepAction, TestStep

_STEP_ACTION_SET: frozenset[str] = frozenset(
    {
        "goto",
        "navigate",
        "click",
        "fill",
        "select",
        "press_enter",
        "hover",
        "scroll",
        "wait",
        "assert",
        "assert_visible",
        "assert_text",
        "assert_url",
        "verify_db",
        "api_request",
    }
)

_ACTION_ALIASES: dict[str, StepAction] = {
    "goto": "goto",
    "navigate": "navigate",
    "nav": "navigate",
    "open": "navigate",
    "open_url": "navigate",
    "go_to": "navigate",
    "click": "click",
    "tap": "click",
    "fill": "fill",
    "type": "fill",
    "input": "fill",
    "enter_text": "fill",
    "select": "select",
    "choose": "select",
    "press_enter": "press_enter",
    "enter": "press_enter",
    "hover": "hover",
    "scroll": "scroll",
    "scroll_to": "scroll",
    "wait": "wait",
    "delay": "wait",
    "sleep": "wait",
    "assert": "assert",
    "assert_visible": "assert_visible",
    "assert_text": "assert_text",
    "assert_url": "assert_url",
    "check": "assert",
    "verify": "assert",
    "validate": "assert",
    "read": "assert",
    "inspect": "assert",
    "compare": "assert",
    "verify_db": "verify_db",
    "api_request": "api_request",
    "api": "api_request",
    "send_request": "api_request",
    "call_api": "api_request",
    "http_request": "api_request",
}

_NAVIGATE_HINTS = (
    "navigate",
    "go to",
    "goto",
    "open url",
    "open page",
    "mở trang",
    "đi tới",
    "truy cập",
    "điều hướng",
)
_SCROLL_HINTS = ("scroll", "cuộn", "kéo xuống", "kéo lên")
_WAIT_HINTS = ("wait", "chờ", "đợi", "pause", "delay")
_API_HINTS = (
    "api_request",
    "api request",
    "send request",
    "call api",
    "http request",
    "gửi request",
    "gọi api",
)
_ASSERT_HINTS = (
    "assert",
    "check",
    "verify",
    "validate",
    "inspect",
    "compare",
    "read ",
    "kiểm tra",
    "xác minh",
    "đối chiếu",
    "so sánh",
    "đọc ",
    "quan sát",
)
_FILL_HINTS = ("fill", "type ", "enter text", "nhập", "điền")
_SELECT_HINTS = ("select", "choose", "chọn")
_HOVER_HINTS = ("hover", "di chuột", "rê chuột")
_CLICK_HINTS = ("click", "tap", "bấm", "nhấn", "ấn ")


def _normalize_token(raw: object) -> str:
    return str(raw or "").strip().lower().replace("-", "_").replace(" ", "_")


def infer_step_action_from_text(text: str) -> StepAction | None:
    """Infer action from intent_hint / description; ``None`` when inconclusive."""
    lowered = " ".join(str(text or "").lower().split())
    if not lowered:
        return None
    if any(token in lowered for token in _NAVIGATE_HINTS):
        return "navigate"
    if any(token in lowered for token in _SCROLL_HINTS):
        return "scroll"
    if any(token in lowered for token in _API_HINTS):
        return "api_request"
    if any(token in lowered for token in _WAIT_HINTS):
        return "wait"
    if any(token in lowered for token in _FILL_HINTS):
        return "fill"
    if any(token in lowered for token in _SELECT_HINTS):
        return "select"
    if any(token in lowered for token in _HOVER_HINTS):
        return "hover"
    if any(token in lowered for token in _ASSERT_HINTS):
        return "assert"
    if any(token in lowered for token in _CLICK_HINTS):
        return "click"
    return None


def coerce_step_action(
    raw: object,
    *,
    intent_hint: str = "",
    description: str = "",
) -> StepAction:
    """Map Copilot/LLM action tokens onto ``StepAction``; infer before defaulting to click."""
    token = _normalize_token(raw)
    if token in _ACTION_ALIASES:
        return _ACTION_ALIASES[token]
    if token in _STEP_ACTION_SET:
        return token  # type: ignore[return-value]

    # Raw may itself be a prose label ("Navigate to pricing").
    inferred = infer_step_action_from_text(str(raw or ""))
    if inferred is not None:
        return inferred

    for candidate in (intent_hint, description):
        inferred = infer_step_action_from_text(candidate)
        if inferred is not None:
            return inferred
    return "click"


def step_target_label(step: TestStep) -> str:
    """Excel Target/Element: prefer bound ref, else semantic UNBOUND description."""
    ref = (step.element_ref or "").strip()
    if ref:
        return ref
    hint = (step.intent_hint or "").strip()
    if hint:
        return hint
    return ""


def step_test_data(step: TestStep) -> str:
    """Excel Test Data cell from step input."""
    return (step.input_data or "").strip()


def resolve_element_ref(raw_step: dict[str, Any]) -> str | None:
    """Prefer explicit element/target fields from Copilot JSON (allowed while UNBOUND)."""
    for key in ("element_ref", "target", "element", "target_element", "object"):
        value = raw_step.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def resolve_input_data(raw_step: dict[str, Any]) -> str | None:
    for key in ("input_data", "data_input", "test_data", "data", "value", "url"):
        value = raw_step.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None
