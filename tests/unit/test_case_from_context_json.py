"""Invariant #13 — case in Copilot context may be model_dump_json string."""

from __future__ import annotations

from ai_qa_test_studio.agent.orchestrator import (
    _case_from_context,
    _fingerprints_overlay_for_case,
)
from ai_qa_test_studio.core.models import (
    ElementFingerprint,
    Project,
    SharedElement,
    TestCase,
    TestGroup,
    TestStep,
)


def test_case_from_context_parses_json_string() -> None:
    case = TestCase(
        id="c1",
        code="TC1",
        title="Login",
        steps=[
            TestStep(
                step_no=1,
                action="click",
                element_ref="EL_A",
                intent_hint="go",
                binding_status="BOUND",
            )
        ],
    )
    parsed = _case_from_context({"case": case.model_dump_json()})
    assert parsed is not None
    assert parsed.code == "TC1"
    assert parsed.steps[0].element_ref == "EL_A"


def test_fingerprints_overlay_for_partial_script() -> None:
    case = TestCase(
        id="c1",
        code="TC1",
        title="Login",
        steps=[
            TestStep(
                step_no=1,
                action="click",
                element_ref="EL_A",
                intent_hint="go",
                binding_status="BOUND",
            ),
            TestStep(
                step_no=2,
                action="fill",
                element_ref="EL_B",
                intent_hint="email",
                binding_status="UNBOUND",
            ),
        ],
    )
    project = Project(
        id="p1",
        name="Demo",
        root_group=TestGroup(id="g1", name="root"),
        shared_elements=[
            SharedElement(
                element_id="EL_A",
                page_name="P",
                friendly_name="A",
                css="#a",
                fingerprint=ElementFingerprint(
                    tag="button",
                    role="button",
                    accessible_name="Go",
                    css="#a",
                    test_id="go-btn",
                ),
            )
        ],
    )
    overlay = _fingerprints_overlay_for_case(case, {"project": project})
    assert "Known ElementFingerprints" in overlay
    assert "EL_A" in overlay
    assert "go-btn" in overlay
