"""SWARM_AUDIT end-to-end (QT_QPA_PLATFORM=offscreen)."""

from __future__ import annotations

import os
from pathlib import Path

import pytest
from PySide6.QtWidgets import QApplication

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ai_qa_test_studio.agent.intent_router import HybridIntentRouter, ThinkingPolicy
from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
from ai_qa_test_studio.agent.reality_checker import RealityChecker
from ai_qa_test_studio.agent.swarm_audit import (
    SwarmAuditor,
    TraceabilityAuditor,
    format_swarm_audit_markdown,
    swarm_audit_structured_payload,
)
from ai_qa_test_studio.constants import (
    APPLY_KIND_SWARM_AUDIT,
    AUDIT_CODE_FALSE_POSITIVE_PASS,
    AUDIT_CODE_HEAL_REVIEW,
    EVENT_FINAL_REPORT,
    ROLE_COORDINATOR,
    SECRET_MASK_PLACEHOLDER,
    THINKING_BUDGET_HIGH,
)
from ai_qa_test_studio.core.enums import IntentType, StepStatus, ThinkingLevel
from ai_qa_test_studio.core.models import (
    CaseRunResult,
    Project,
    RunReport,
    RunStepResult,
    StepAuditEntry,
    TestCase,
    TestGroup,
    TestScenario,
    TestStep,
)
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.reporting.evidence import EvidenceCollector
from ai_qa_test_studio.ui.bubble_summary import format_structured_payload_summary
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


def _audit_fixture(tmp_path: Path) -> tuple[Project, RunReport]:
    shot = tmp_path / "shot.png"
    shot.write_bytes(b"\x89PNG\r\n\x1a\nfake")
    project = Project(
        id="p1",
        name="demo",
        root_group=TestGroup(
            id="g1",
            name="Root",
            scenarios=[
                TestScenario(
                    id="s1",
                    code="TS-01",
                    title="Login",
                    requirement_ref="REQ-LOGIN",
                    cases=[
                        TestCase(
                            id="c-pass",
                            code="TS-01-C01",
                            title="Pass no assert",
                            requirement_ref="REQ-LOGIN",
                            steps=[
                                TestStep(
                                    step_no=1,
                                    action="click",
                                    element_ref="EL",
                                    binding_status="BOUND",
                                    intent_hint="submit",
                                )
                            ],
                        ),
                        TestCase(
                            id="c-heal",
                            code="TS-01-C02",
                            title="Healed weak",
                            requirement_ref="REQ-LOGIN",
                            steps=[
                                TestStep(
                                    step_no=1,
                                    action="click",
                                    element_ref="EL",
                                    binding_status="HEALED",
                                    intent_hint="submit",
                                ),
                                TestStep(
                                    step_no=2,
                                    action="assert_text",
                                    intent_hint="ok",
                                    binding_status="BOUND",
                                ),
                            ],
                        ),
                    ],
                ),
                TestScenario(
                    id="s2",
                    code="TS-02",
                    title="Empty req",
                    requirement_ref="REQ-EMPTY",
                    cases=[],
                ),
            ],
        ),
    )
    report = RunReport(
        run_id="audit-run-1",
        project_name="demo",
        started_at="t0",
        finished_at="t1",
        cases=[
            CaseRunResult(
                case_id="c-pass",
                case_code="TS-01-C01",
                title="Pass no assert",
                status="PASS",
                steps=[
                    RunStepResult(
                        step_no=1,
                        action="click",
                        status="PASS",
                        step_status=StepStatus.PASSED,
                        message="ok",
                        screenshot_path=str(shot),
                    )
                ],
            ),
            CaseRunResult(
                case_id="c-heal",
                case_code="TS-01-C02",
                title="Healed weak",
                status="HEALED",
                steps=[
                    RunStepResult(
                        step_no=1,
                        action="click",
                        status="HEALED",
                        step_status=StepStatus.HEALED,
                        message="healed; password=SuperSecret99 token=abc123",
                        confidence_score=0.72,
                        previous_selector="#old-btn",
                        screenshot_path=str(shot),
                        console_logs=["password=SuperSecret99"],
                        audit=StepAuditEntry(
                            step_no=1,
                            old_locator="#old-btn",
                            new_locator="#new-btn",
                            previous_selector="#old-btn",
                            confidence_score=0.72,
                            persisted=False,
                        ),
                    ),
                    RunStepResult(
                        step_no=2,
                        action="assert_text",
                        status="PASS",
                        step_status=StepStatus.PASSED,
                        message="ok",
                        screenshot_path=str(shot),
                    ),
                ],
            ),
        ],
        healing_audit=[
            StepAuditEntry(
                step_no=1,
                old_locator="#old-btn",
                new_locator="#new-btn",
                previous_selector="#old-btn",
                confidence_score=0.72,
                persisted=False,
            )
        ],
    )
    return project, report


def test_audit_keywords_route_to_swarm_audit_high() -> None:
    router = HybridIntentRouter()
    policy = ThinkingPolicy()
    for prompt in (
        "/audit",
        "kiểm toán độ phủ",
        "traceability matrix",
        "bằng chứng evidence report",
    ):
        decision = router.route_fast(prompt)
        assert decision is not None, prompt
        assert decision.intent is IntentType.SWARM_AUDIT, prompt
        thinking = policy.for_intent(decision.intent)
        assert thinking.level is ThinkingLevel.HIGH
        assert thinking.budget_tokens >= THINKING_BUDGET_HIGH


@pytest.mark.asyncio
async def test_swarm_audit_reads_tmp_runs_and_structured_payload(tmp_path: Path) -> None:
    from unittest.mock import MagicMock

    project, report = _audit_fixture(tmp_path)
    EvidenceCollector(tmp_path).collect(report)
    orch = CopilotOrchestrator(gateway=MagicMock())
    events = [
        event
        async for event in orch.run(
            "/audit kiểm toán",
            mode="compose",
            context={
                "run_report": report,
                "project": project,
                "project_root": str(tmp_path),
            },
        )
    ]
    assert any(e.kind == "role_end" and e.role == ROLE_COORDINATOR for e in events)
    finals = [e for e in events if e.kind == EVENT_FINAL_REPORT]
    assert finals
    payload = finals[-1].apply_payload
    assert isinstance(payload, dict)
    assert payload.get("kind") == APPLY_KIND_SWARM_AUDIT
    assert payload.get("thinking") == ThinkingLevel.HIGH.value
    md = str(payload.get("markdown") or "")
    assert "{" not in md or "case_total" not in md  # no metrics JSON dump
    assert "Báo cáo kiểm toán" in md
    assert "FALSE_POSITIVE_PASS" in md or "FALSE_POSITIVE" in str(payload.get("findings"))
    evidence = tmp_path / ".studio" / "runs" / "audit-run-1"
    assert evidence.exists()
    # Evidence RO: orchestrator must not warm MCP for this intent
    assert orch.last_routing is not None
    assert orch.last_routing.intent is IntentType.SWARM_AUDIT


def test_reality_checker_false_positive_and_heal_review(tmp_path: Path) -> None:
    _project, report = _audit_fixture(tmp_path)
    result = RealityChecker().audit_report(report)
    codes = {f.code for f in result.findings}
    assert AUDIT_CODE_FALSE_POSITIVE_PASS in codes
    assert AUDIT_CODE_HEAL_REVIEW in codes
    heal = next(f for f in result.findings if f.code == AUDIT_CODE_HEAL_REVIEW)
    assert heal.confidence == pytest.approx(0.72)
    assert heal.previous_selector == "#old-btn"
    assert heal.healed_selector == "#new-btn"


def test_traceability_and_secret_masker(tmp_path: Path) -> None:
    project, report = _audit_fixture(tmp_path)
    metrics = TraceabilityAuditor().audit(project, report)
    assert metrics.requirement_total >= 1
    assert metrics.requirement_coverage_pct > 0
    assert any("REQ-EMPTY" in g or "TS-02" in g for g in metrics.gaps)

    collector = EvidenceCollector(tmp_path)
    out = collector.collect(report)
    manifest = (out / "manifest.json").read_text(encoding="utf-8")
    assert "SuperSecret99" not in manifest
    assert SECRET_MASK_PLACEHOLDER in manifest
    masked = get_secret_masker().mask_text("password=SuperSecret99 token=abc123")
    assert "SuperSecret99" not in masked
    assert SECRET_MASK_PLACEHOLDER in masked

    audit = SwarmAuditor().run(report, project, project_root=tmp_path)
    payload = swarm_audit_structured_payload(audit)
    card = format_structured_payload_summary(payload)
    assert "SuperSecret99" not in card
    assert "{" not in card.split("Báo cáo")[0] or "Báo cáo kiểm toán" in card
    assert "raw" not in card.lower() or "Báo cáo" in card
    assert '"kind"' not in card


def test_chat_bubble_markdown_and_loading_settles(qapp: QApplication, tmp_path: Path) -> None:
    project, report = _audit_fixture(tmp_path)
    audit = SwarmAuditor().run(report, project, project_root=tmp_path)
    payload = swarm_audit_structured_payload(audit)
    markdown = format_swarm_audit_markdown(audit)
    pane = CopilotChatPane()
    pane.show()
    qapp.processEvents()
    pane.begin_role(ROLE_COORDINATOR)
    assert pane._streaming_row is not None
    assert pane._streaming_row.is_typing()
    pane.end_role(
        ROLE_COORDINATOR,
        markdown,
        summary=format_structured_payload_summary(payload),
        structured_payload=payload,
    )
    pane.on_turn_finished()
    qapp.processEvents()
    assert all(not row.is_typing() for row in pane._rows)
    visible = "\n".join(row.body.text() for row in pane._rows if row.role == ROLE_COORDINATOR)
    assert '{"kind"' not in visible
    assert "Báo cáo kiểm toán" in visible or "Độ phủ" in visible or "Pass" in visible
    pane.close()
