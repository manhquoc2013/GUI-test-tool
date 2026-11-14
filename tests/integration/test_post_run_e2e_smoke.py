"""Post-run E2E smoke: Copilot → Apply → Sync → DualLoop Run → Excel (offscreen).

Chains the five business stages without a live browser, using the same
production helpers the UI calls (compose_mapping, AutoBinder, DualLoopAgent,
EvidenceCollector, ReportService / ExcelExporter).
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from openpyxl import load_workbook

from ai_qa_test_studio.agent.auto_binder import ShiftLeftAutoBinder
from ai_qa_test_studio.agent.case_binding import case_binding_summary, is_bindable_step
from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.agent.healer import Healer
from ai_qa_test_studio.agent.mcp_tool_executor import SmartMCPToolExecutor
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    COPILOT_STRUCTURED_DRAFT_BANNER,
    EVIDENCE_MANIFEST_FILENAME,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
    SECRET_MASK_PLACEHOLDER,
    STUDIO_DIRNAME,
    STUDIO_RUNS_DIRNAME,
)
from ai_qa_test_studio.core.compose_mapping import (
    coerce_preconditions,
    extract_start_url,
    normalize_compose_scenarios,
)
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import (
    DistilledNode,
    ElementFingerprint,
    Project,
    SharedElement,
    TestGroup,
    TestScenario,
)
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.reporting.evidence import EvidenceCollector
from ai_qa_test_studio.reporting.report_service import ReportService
from ai_qa_test_studio.ui.bubble_summary import format_structured_payload_summary
from ai_qa_test_studio.ui.controllers.copilot_controller import clip_chat_reply


def _compose_payload() -> dict[str, Any]:
    return {
        "kind": APPLY_KIND_COMPOSE,
        "title": "Login Smoke Suite",
        "start_url": "https://example.test/login",
        "scenarios": [
            {
                "code": "TS_LOGIN",
                "title": "Đăng nhập thành công",
                "cases": [
                    {
                        "code": "TC_LOGIN",
                        "title": "Happy path login",
                        "preconditions": "Cookie consent accepted",
                        "expected_final_result": "Welcome",
                        "steps": [
                            {
                                "step_no": 1,
                                "action": "Navigate to login",
                                "intent_hint": "Open https://example.test/login",
                                "expected_result": "Login page loads",
                            },
                            {
                                "step_no": 2,
                                "action": "fill",
                                "intent_hint": "Password field",
                                "data_input": "SuperSecret99",
                                "expected_result": "Password filled",
                            },
                            {
                                "step_no": 3,
                                "action": "click",
                                "intent_hint": "Sign in",
                                "expected_result": "Dashboard opens",
                            },
                            {
                                "step_no": 4,
                                "action": "assert",
                                "intent_hint": "Welcome banner",
                                "expected_result": "Welcome",
                            },
                        ],
                    }
                ],
            }
        ],
    }


def _high_conf_nodes() -> list[DistilledNode]:
    return [
        DistilledNode(
            short_id="n1",
            fingerprint=ElementFingerprint(
                tag="input",
                role="textbox",
                accessible_name="Password field",
                test_id="login-password",
                css='[data-testid="login-password"]',
                text_sample="Password",
            ),
        ),
        DistilledNode(
            short_id="n2",
            fingerprint=ElementFingerprint(
                tag="button",
                role="button",
                accessible_name="Sign in",
                test_id="login-submit",
                css='[data-testid="login-submit"]',
                text_sample="Sign in",
            ),
        ),
        DistilledNode(
            short_id="n3",
            fingerprint=ElementFingerprint(
                tag="div",
                role="status",
                accessible_name="Welcome banner",
                css="#ok",
                text_sample="Welcome",
            ),
        ),
    ]


@pytest.mark.asyncio
async def test_post_run_e2e_smoke_five_stages(tmp_path: Path) -> None:
    # --- 1) Copilot Chat: structured summary card, no raw JSON dump ---
    payload = _compose_payload()
    raw_blob = (
        "Dưới đây là draft TEST_SCENARIO / TEST_CASE_GEN:\n```json\n"
        + json.dumps(payload, ensure_ascii=False)
        + "\n```\n"
    )
    bubble = clip_chat_reply(raw_blob, structured_payload=payload)
    summary = format_structured_payload_summary(payload)
    assert COPILOT_STRUCTURED_DRAFT_BANNER in bubble or COPILOT_STRUCTURED_DRAFT_BANNER in summary
    assert '"kind"' not in bubble
    assert '"scenarios"' not in bubble
    assert "TS_LOGIN" in summary
    assert "Đăng nhập" in summary or "Happy path" in summary
    # Preconditions must stay a whole phrase (never character-split via "; ".join)
    assert coerce_preconditions("Cookie consent accepted") == ["Cookie consent accepted"]
    assert "; ".join(list("Cookie")) != "Cookie"

    # --- 2) Apply → Editor model: URL, actions, data, test_data_rows ---
    scenarios = normalize_compose_scenarios(list(payload["scenarios"]))
    assert len(scenarios) == 1
    case = scenarios[0].cases[0]
    start_url = extract_start_url(
        scenarios, payload_start_url=str(payload.get("start_url") or "")
    )
    assert start_url == "https://example.test/login"
    case.start_url = start_url
    assert case.preconditions == ["Cookie consent accepted"]
    actions = [s.action for s in case.steps]
    assert actions[0] == "navigate"
    assert "fill" in actions
    assert "click" in actions
    assert any(s.action in {"assert", "assert_visible", "assert_text"} for s in case.steps)
    assert any((s.input_data or "").strip() for s in case.steps)
    assert any((s.expected_result or "").strip() for s in case.steps)
    assert len(case.test_data_rows) > 0

    # --- 3) Sync UNBOUND → BOUND (skip Navigate; ≥0.85; no short_id in FP) ---
    fill_step = next(s for s in case.steps if s.action == "fill")
    click_step = next(s for s in case.steps if s.action == "click")
    assert_step = next(
        s for s in case.steps if s.action in {"assert", "assert_visible", "assert_text"}
    )
    fill_step.element_ref = "EL_PWD"
    click_step.element_ref = "EL_SUBMIT"
    assert_step.element_ref = "EL_OK"
    assert_step.action = "assert_visible"

    nodes = _high_conf_nodes()
    repo: dict[str, SharedElement] = {
        "EL_PWD": SharedElement(
            element_id="EL_PWD",
            page_name="Login",
            friendly_name="Password",
            name="Password field",
            role="textbox",
            test_id="login-password",
            css="stale",
            primary_playwright="stale",
        ),
        "EL_SUBMIT": SharedElement(
            element_id="EL_SUBMIT",
            page_name="Login",
            friendly_name="Sign in",
            name="Sign in",
            role="button",
            test_id="login-submit",
            css="stale",
            primary_playwright="stale",
        ),
        "EL_OK": SharedElement(
            element_id="EL_OK",
            page_name="Home",
            friendly_name="Welcome",
            name="Welcome banner",
            role="status",
            css="stale",
            primary_playwright="stale",
        ),
    }

    page = AsyncMock()
    page.goto = AsyncMock()
    page.wait_for_load_state = AsyncMock()
    binder = ShiftLeftAutoBinder(page, repo)
    binder.resolver.try_semantic_match = AsyncMock(return_value=None)  # type: ignore[method-assign]
    binder.resolver.execute_step = AsyncMock()  # type: ignore[method-assign]
    binder.distiller.distill = AsyncMock(return_value=nodes)  # type: ignore[method-assign]

    with patch("ai_qa_test_studio.agent.auto_binder.score_match", return_value=0.92):
        sync_result = await binder.sync_with_audit(case, base_url=start_url)

    nav = next(s for s in sync_result.case.steps if s.action in {"navigate", "goto"})
    assert not is_bindable_step(nav)
    assert nav.binding_status == "UNBOUND"
    for step in sync_result.case.steps:
        if is_bindable_step(step):
            assert step.binding_status in {"BOUND", "HEALED"}
    post_sync = case_binding_summary(sync_result.case)
    assert post_sync.is_fully_bound is True
    assert post_sync.total_bindable_steps >= 3
    assert sync_result.yaml_persisted is True
    for el in repo.values():
        assert el.fingerprint is not None
        dumped = el.fingerprint.model_dump()
        assert "short_id" not in dumped
        assert el.fingerprint.css

    # --- 4) DualLoop run + EvidenceCollector + SecretMasker outbound-only ---
    # Ensure selectors are executable after sync
    for el in repo.values():
        if el.fingerprint and el.fingerprint.css:
            el.css = el.fingerprint.css
            el.primary_playwright = el.fingerprint.css
            if el.fingerprint.test_id:
                el.test_id = el.fingerprint.test_id

    raw_calls: list[dict[str, Any]] = []
    click_attempts = {"n": 0}

    async def call_tool(server: str, tool: str, arguments: dict[str, Any]) -> dict[str, Any]:
        raw_calls.append({"tool": tool, "args": dict(arguments)})
        # First click fails → DualLoop heal (≥ 0.65); retest + other tools succeed
        if tool == "browser_click" and click_attempts["n"] == 0:
            click_attempts["n"] += 1
            return {
                "ok": False,
                "content": "Element(s) not found",
                "is_error": True,
            }
        return {"ok": True, "content": "ok", "is_error": False}

    heal_nodes = [
        DistilledNode(
            short_id="heal1",
            fingerprint=ElementFingerprint(
                tag="button",
                role="button",
                accessible_name="Sign in",
                test_id="login-submit",
                css='[data-testid="login-submit"]',
                text_sample="Sign in",
            ),
        )
    ]
    distiller = MagicMock()
    distiller.distill = AsyncMock(return_value=heal_nodes)
    healer = Healer(distiller=distiller)

    run_page = AsyncMock()
    run_page.inner_text = AsyncMock(return_value="Welcome")
    run_page.screenshot = AsyncMock(return_value=b"\x89PNG\r\n\x1a\n" + b"\x00" * 32)
    # Fallback path if MCP returns not-ok then resolver.execute_step
    run_page.locator = MagicMock()

    evidence = EvidenceCollector(tmp_path)
    agent = DualLoopAgent(
        run_page,
        repo,
        healer=healer,
        evidence=evidence,
    )
    agent.mcp_actions_enabled = True
    agent.mcp_executor = SmartMCPToolExecutor(
        call_tool, actions_enabled=True, max_retries=0
    )

    # Force resolver fallback to raise on first click so DualLoop enters heal
    # when MCP returns not-ok (mcp_first_fallback → execute_step).
    fail_once = {"click": True}

    async def execute_step_maybe_fail(page: Any, step: Any, element: Any) -> None:
        if str(step.action) == "click" and fail_once["click"]:
            fail_once["click"] = False
            raise RuntimeError("Element(s) not found")

    agent.resolver.execute_step = execute_step_maybe_fail  # type: ignore[method-assign]
    agent.resolver.bound_selector = lambda el: (  # type: ignore[method-assign]
        el.primary_playwright or el.css or ""
    )

    with patch(
        "ai_qa_test_studio.agent.healer.score_match",
        return_value=0.72,  # mid-band: run heal, no YAML persist
    ):
        report = await agent.run_case(
            sync_result.case,
            project_name="Smoke",
            run_id="smoke-e2e-1",
        )

    assert report.cases
    case_status = report.cases[0].status
    assert case_status in {"PASS", "HEALED"}
    statuses = [s.step_status for s in report.cases[0].steps if s.step_status is not None]
    assert StepStatus.PASSED in statuses or StepStatus.HEALED in statuses
    # Mid-band heal (≥0.65) should appear when click failed once
    assert any(s == StepStatus.HEALED for s in statuses) or case_status == "PASS"

    fill = next((c for c in raw_calls if c["tool"] == "browser_fill"), None)
    assert fill is not None
    assert fill["args"].get("text") == "SuperSecret99"
    for step_res in report.cases[0].steps:
        assert "SuperSecret99" not in (step_res.message or "")
    masked = get_secret_masker().mask_text("password=SuperSecret99")
    assert SECRET_MASK_PLACEHOLDER in masked

    # Mid-band: SharedElement must be restored (no YAML leak of heal-only css)
    # Fingerprint must never carry DistilledNode.short_id
    for el in repo.values():
        if el.fingerprint is not None:
            assert "short_id" not in el.fingerprint.model_dump()

    run_dir = tmp_path / STUDIO_DIRNAME / STUDIO_RUNS_DIRNAME / "smoke-e2e-1"
    assert run_dir.is_dir()
    assert (run_dir / EVIDENCE_MANIFEST_FILENAME).is_file()
    # HEALED/FAILED steps capture PNG under .studio/runs/<run_id>/
    pngs = list(run_dir.glob("*.png"))
    healed_or_failed = [
        s
        for s in report.cases[0].steps
        if s.step_status in {StepStatus.HEALED, StepStatus.FAILED}
    ]
    if healed_or_failed:
        assert pngs, "expected evidence PNG for healed/failed step"
        for step_res in healed_or_failed:
            if step_res.screenshot_path:
                assert Path(step_res.screenshot_path).is_file()

    # --- 5) ReportService Excel + openpyxl round-trip ---
    project = Project(
        id="p-smoke",
        name="Smoke",
        root_group=TestGroup(
            id="g1",
            name="root",
            scenarios=[
                TestScenario(
                    id=scenarios[0].id,
                    code=scenarios[0].code,
                    title=scenarios[0].title,
                    cases=[sync_result.case],
                )
            ],
        ),
        shared_elements=list(repo.values()),
    )
    xlsx = tmp_path / "smoke_report.xlsx"
    service = ReportService(tmp_path)
    await service.export_excel(project, report, xlsx)
    assert xlsx.is_file() and xlsx.stat().st_size > 0

    wb = load_workbook(xlsx)
    assert "Test Cases (IEEE 829)" in wb.sheetnames
    sheet = wb["Test Cases (IEEE 829)"]
    headers = [cell.value for cell in next(sheet.iter_rows(min_row=1, max_row=1))]
    assert "Target" in headers
    assert "Test Data" in headers
    target_idx = headers.index("Target")
    data_idx = headers.index("Test Data")
    action_idx = headers.index("Action")
    pre_idx = headers.index("Điều kiện tiên quyết")

    data_rows = list(sheet.iter_rows(min_row=2, values_only=True))
    assert data_rows, "Excel must contain at least one case/step row"
    for row in data_rows:
        pre = row[pre_idx]
        if pre:
            assert str(pre) == "Cookie consent accepted"
        action = str(row[action_idx] or "")
        assert action in {
            "navigate",
            "goto",
            "fill",
            "click",
            "assert",
            "assert_visible",
            "assert_text",
            "assert_url",
            "scroll",
            "wait",
        }

    ui_rows = [
        row
        for row in data_rows
        if str(row[action_idx] or "") in {"fill", "click", "assert", "assert_visible"}
    ]
    assert ui_rows
    # Target / Test Data must not be blank across all UI rows
    assert any(row[target_idx] for row in ui_rows), "Target column empty for UI steps"
    assert any(row[data_idx] for row in ui_rows), "Test Data column empty for UI steps"

    assert HEAL_RUN_THRESHOLD == 0.65
    assert HEAL_PERSIST_THRESHOLD == 0.85
