from __future__ import annotations

from ai_qa_test_studio.core.enums import (
    DeliveryMode,
    IntentType,
    StepStatus,
    ThinkingLevel,
)
from ai_qa_test_studio.core.models import (
    DistilledNode,
    ElementFingerprint,
    RunStepResult,
    SharedElement,
    StepResult,
)


def test_step_status_and_intent_enums() -> None:
    assert StepStatus.HEALED.value == "Healed"
    assert IntentType.CHAT_FAST.value == "chat_fast"
    assert DeliveryMode.DRAFT.value == "draft"
    assert ThinkingLevel.OFF.value == "off"


def test_fingerprint_persistent_fields_only() -> None:
    fp = ElementFingerprint(tag="button", role="button", test_id="login", css="#btn")
    data = fp.model_dump()
    assert "short_id" not in data
    assert data["test_id"] == "login"


def test_distilled_node_holds_ephemeral_ids() -> None:
    node = DistilledNode(
        short_id="e1",
        parent_short_id=None,
        sibling_index=0,
        fingerprint=ElementFingerprint(tag="input", css="#email"),
    )
    assert node.short_id == "e1"
    assert node.fingerprint.css == "#email"


def test_shared_element_optional_fingerprint() -> None:
    el = SharedElement(
        element_id="EL_1",
        page_name="Login",
        friendly_name="Email",
        fingerprint=ElementFingerprint(tag="input", test_id="email"),
    )
    assert el.fingerprint is not None
    assert el.fingerprint.test_id == "email"


def test_run_step_result_alias_and_new_fields() -> None:
    result = RunStepResult(
        step_no=1,
        action="click",
        status="PASS",
        step_status=StepStatus.PASSED,
        confidence_score=0.9,
        screenshot_path="/tmp/shot.png",
    )
    assert isinstance(result, StepResult)
    assert result.step_status == StepStatus.PASSED
    assert result.screenshot_path == "/tmp/shot.png"
