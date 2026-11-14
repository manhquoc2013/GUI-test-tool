"""Toolbar Run flow — BOUND fast-path, dual heal thresholds, evidence, Stop cancel."""

from __future__ import annotations

import asyncio
import os
from pathlib import Path
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.agent.healer import Healer
from ai_qa_test_studio.agent.mcp_tool_executor import SmartMCPToolExecutor
from ai_qa_test_studio.constants import (
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
    RUN_TASK_KEY_PREFIX,
    SECRET_MASK_PLACEHOLDER,
    STUDIO_DIRNAME,
    STUDIO_RUNS_DIRNAME,
)
from ai_qa_test_studio.core.async_task_supervisor import AsyncTaskSupervisor
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import (
    DistilledNode,
    ElementFingerprint,
    SharedElement,
    TestCase,
    TestStep,
)
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.core.url_validation import is_valid_target_url
from ai_qa_test_studio.reporting.evidence import EvidenceCollector


def _bound_case() -> tuple[TestCase, dict[str, SharedElement]]:
    pwd = SharedElement(
        element_id="EL_PWD",
        page_name="Login",
        friendly_name="Password",
        test_id="login-password",
        css="#password",
        primary_playwright='[data-testid="login-password"]',
        fingerprint=ElementFingerprint(
            tag="input",
            test_id="login-password",
            css="#password",
            role="textbox",
            accessible_name="Password",
        ),
    )
    submit = SharedElement(
        element_id="EL_SUBMIT",
        page_name="Login",
        friendly_name="Submit",
        test_id="login-submit",
        css="#submit",
        primary_playwright='[data-testid="login-submit"]',
        fingerprint=ElementFingerprint(
            tag="button",
            test_id="login-submit",
            css="#submit",
            role="button",
            accessible_name="Sign in",
        ),
    )
    ok = SharedElement(
        element_id="EL_OK",
        page_name="Home",
        friendly_name="Banner",
        css="#ok",
        primary_playwright="#ok",
        fingerprint=ElementFingerprint(tag="div", css="#ok", accessible_name="Welcome"),
    )
    case = TestCase(
        id="case-bound-1",
        code="TC_BOUND",
        title="Bound happy",
        steps=[
            TestStep(
                step_no=1,
                action="fill",
                element_ref="EL_PWD",
                intent_hint="Password",
                input_data="SuperSecret99",
                binding_status="BOUND",
            ),
            TestStep(
                step_no=2,
                action="click",
                element_ref="EL_SUBMIT",
                intent_hint="Sign in",
                binding_status="BOUND",
            ),
            TestStep(
                step_no=3,
                action="assert_visible",
                element_ref="EL_OK",
                intent_hint="Welcome",
                binding_status="BOUND",
            ),
        ],
    )
    return case, {"EL_PWD": pwd, "EL_SUBMIT": submit, "EL_OK": ok}


@pytest.mark.asyncio
async def test_happy_path_bound_no_llm_masks_password() -> None:
    case, repo = _bound_case()
    raw_calls: list[dict[str, Any]] = []
    progress: list[tuple[int, str]] = []

    async def call_tool(server: str, tool: str, arguments: dict[str, Any]) -> dict[str, Any]:
        raw_calls.append({"tool": tool, "args": dict(arguments)})
        return {"ok": True, "content": "ok", "is_error": False}

    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="Welcome")
    page.screenshot = AsyncMock(return_value=b"\x89PNG")
    agent = DualLoopAgent(page, repo)
    agent.mcp_actions_enabled = True
    agent.mcp_executor = SmartMCPToolExecutor(call_tool, actions_enabled=True)

    report = await agent.run_case(
        case,
        project_name="demo",
        on_step=lambda n, a, s, d, m: progress.append((n, s)),
    )

    statuses = [s.step_status for s in report.cases[0].steps]
    assert statuses == [StepStatus.PASSED, StepStatus.PASSED, StepStatus.PASSED]
    assert report.cases[0].status == "PASS"
    # Raw password reaches Playwright tool; outbound messages are masked
    fill = next(c for c in raw_calls if c["tool"] == "browser_fill")
    assert fill["args"]["text"] == "SuperSecret99"
    assert fill["args"]["selector"] == '[data-testid="login-password"]'
    for step in report.cases[0].steps:
        assert "SuperSecret99" not in step.message or step.message == ""
    # Lifecycle includes Running then Passed for each step
    assert any(s == StepStatus.RUNNING.value for _, s in progress)
    assert progress[-1][1] == StepStatus.PASSED.value
    # Masker still redacts password-shaped outbound text
    assert SECRET_MASK_PLACEHOLDER in get_secret_masker().mask_text(
        "password=SuperSecret99"
    )


@pytest.mark.asyncio
async def test_self_heal_072_runtime_only_vs_090_persists(tmp_path: Path) -> None:
    page = AsyncMock()
    page.screenshot = AsyncMock(return_value=b"\x89PNG")

    # --- 0.72: heal continues, YAML fingerprint not overwritten ---
    element_mid = SharedElement(
        element_id="EL_BTN",
        page_name="P",
        friendly_name="Go",
        css="#stale",
        primary_playwright="#stale",
        fingerprint=ElementFingerprint(
            tag="button",
            role="button",
            accessible_name="Continue",
            css="#stale",
        ),
    )
    distiller_mid = MagicMock()
    distiller_mid.distill = AsyncMock(
        return_value=[
            DistilledNode(
                short_id="e1",
                fingerprint=ElementFingerprint(
                    tag="button",
                    role="button",
                    accessible_name="Continue action",
                    css="#healed-mid",
                    text_sample="Continue action",
                ),
            )
        ]
    )
    healer_mid = Healer(distiller=distiller_mid)
    step = TestStep(
        step_no=1,
        action="click",
        element_ref="EL_BTN",
        intent_hint="Continue",
        binding_status="BOUND",
    )
    audit_mid = await healer_mid.heal_step(page, step, element_mid)
    assert audit_mid is not None
    assert audit_mid.confidence_score is not None
    assert audit_mid.confidence_score >= HEAL_RUN_THRESHOLD
    assert audit_mid.confidence_score < HEAL_PERSIST_THRESHOLD
    assert audit_mid.persisted is False
    assert "medium_confidence" in audit_mid.reason
    assert element_mid.css == "#healed-mid"
    # Fingerprint object not replaced below persist threshold
    assert element_mid.fingerprint is not None
    assert element_mid.fingerprint.css == "#stale"

    # --- 0.90: persist new fingerprint (no short_id) ---
    element_hi = SharedElement(
        element_id="EL_BTN2",
        page_name="P",
        friendly_name="Go",
        css="#old",
        primary_playwright="#old",
        fingerprint=ElementFingerprint(
            tag="button",
            role="button",
            accessible_name="Sign in",
            test_id="login-submit",
            css="#old",
            xpath="/html/body/button[1]",
        ),
    )
    distiller_hi = MagicMock()
    distiller_hi.distill = AsyncMock(
        return_value=[
            DistilledNode(
                short_id="e9",
                fingerprint=ElementFingerprint(
                    tag="button",
                    role="button",
                    accessible_name="Sign in",
                    test_id="login-submit",
                    css="#new-submit",
                    xpath="/html/body/button[1]",
                    text_sample="Sign in",
                ),
            )
        ]
    )
    healer_hi = Healer(distiller=distiller_hi)
    step2 = TestStep(
        step_no=1,
        action="click",
        element_ref="EL_BTN2",
        intent_hint="Sign in",
        binding_status="BOUND",
    )
    audit_hi = await healer_hi.heal_step(page, step2, element_hi)
    assert audit_hi is not None
    assert audit_hi.confidence_score is not None
    assert audit_hi.confidence_score >= HEAL_PERSIST_THRESHOLD
    assert audit_hi.persisted is True
    assert element_hi.fingerprint is not None
    assert element_hi.fingerprint.css == "#new-submit"
    assert element_hi.fingerprint.test_id == "login-submit"
    # DistilledNode short_id must never land in persisted fingerprint fields
    dumped = element_hi.fingerprint.model_dump()
    assert "e9" not in str(dumped.values())


@pytest.mark.asyncio
async def test_fail_below_065_blocks_and_writes_evidence(tmp_path: Path) -> None:
    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="")
    page.screenshot = AsyncMock(return_value=b"\x89PNG\r\n\x1a\nfake")

    element = SharedElement(
        element_id="EL_A",
        page_name="P",
        friendly_name="A",
        css="#missing",
        primary_playwright="#missing",
        fingerprint=ElementFingerprint(
            tag="button",
            role="button",
            accessible_name="UniqueTargetXYZ",
            css="#missing",
            test_id="unique-a",
        ),
    )
    distiller = MagicMock()
    distiller.distill = AsyncMock(
        return_value=[
            DistilledNode(
                short_id="e1",
                fingerprint=ElementFingerprint(
                    tag="div",
                    role="presentation",
                    accessible_name="noise",
                    css="#noise",
                ),
            )
        ]
    )
    healer = Healer(distiller=distiller)

    class _FailResolver:
        async def execute_step(self, *_a: Any, **_k: Any) -> None:
            raise RuntimeError("Element(s) not found")

        def bound_selector(self, el: SharedElement) -> str:
            return el.css or ""

    case = TestCase(
        id="case-fail-1",
        code="TC_FAIL",
        title="fail",
        steps=[
            TestStep(
                step_no=1,
                action="click",
                element_ref="EL_A",
                intent_hint="UniqueTargetXYZ",
                binding_status="BOUND",
            ),
            TestStep(
                step_no=2,
                action="click",
                element_ref="EL_A",
                intent_hint="next",
                binding_status="BOUND",
            ),
        ],
    )
    evidence = EvidenceCollector(tmp_path)
    agent = DualLoopAgent(
        page,
        {"EL_A": element},
        resolver=_FailResolver(),  # type: ignore[arg-type]
        healer=healer,
        evidence=evidence,
    )
    agent.mcp_actions_enabled = False

    report = await agent.run_case(case, project_name="demo", run_id="run-fail-1")
    steps = report.cases[0].steps
    assert steps[0].step_status == StepStatus.FAILED
    assert steps[1].step_status == StepStatus.BLOCKED
    assert report.cases[0].status == "FAIL"
    shot = Path(steps[0].screenshot_path or "")
    assert shot.is_file()
    assert STUDIO_DIRNAME in str(shot)
    assert STUDIO_RUNS_DIRNAME in str(shot)
    assert (tmp_path / STUDIO_DIRNAME / STUDIO_RUNS_DIRNAME / "run-fail-1").is_dir()


@pytest.mark.asyncio
async def test_stop_cancels_supervisor_task_cleanly() -> None:
    supervisor = AsyncTaskSupervisor()
    cancel_flag = {"v": False}
    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="ok")
    page.screenshot = AsyncMock(return_value=b"png")

    class _SlowResolver:
        def __init__(self) -> None:
            self.calls = 0

        async def execute_step(self, *_a: Any, **_k: Any) -> None:
            self.calls += 1
            await asyncio.sleep(0.05)

        def bound_selector(self, el: SharedElement) -> str:
            return el.css or "#a"

    resolver = _SlowResolver()
    el = SharedElement(element_id="EL_A", page_name="P", friendly_name="A", css="#a")
    case = TestCase(
        id="case-stop-1",
        code="TC_STOP",
        title="stop",
        steps=[
            TestStep(step_no=1, action="click", element_ref="EL_A", binding_status="BOUND"),
            TestStep(step_no=2, action="click", element_ref="EL_A", binding_status="BOUND"),
            TestStep(step_no=3, action="click", element_ref="EL_A", binding_status="BOUND"),
        ],
    )
    agent = DualLoopAgent(page, {"EL_A": el}, resolver=resolver)  # type: ignore[arg-type]
    agent.mcp_actions_enabled = False
    key = f"{RUN_TASK_KEY_PREFIX}{case.id}"

    async def _run() -> None:
        await agent.run_case(
            case,
            project_name="demo",
            should_cancel=lambda: cancel_flag["v"],
        )

    task = supervisor.register(_run(), key=key, replace=True)
    await asyncio.sleep(0.02)
    cancel_flag["v"] = True
    await supervisor.cancel(key)
    assert task.done()
    # No RuntimeError / crash — CancelledError swallowed by gather
    assert resolver.calls <= 2


def test_target_url_validation() -> None:
    assert is_valid_target_url("https://example.com/login")
    assert is_valid_target_url("http://127.0.0.1:3000")
    assert is_valid_target_url("file:///tmp/app.html")
    assert not is_valid_target_url("")
    assert not is_valid_target_url("not-a-url")
    assert not is_valid_target_url("ftp://example.com")
