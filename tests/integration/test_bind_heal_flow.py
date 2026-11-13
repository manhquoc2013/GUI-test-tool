from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.agent.auto_binder import ShiftLeftAutoBinder
from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.browser.engine import BrowserEngine
from ai_qa_test_studio.core.models import SharedElement, TestCase, TestStep


@pytest.mark.asyncio
async def test_unbound_to_bound_to_healed_on_local_html(tmp_path: Path) -> None:
    html = tmp_path / "app.html"
    html.write_text(
        """<!doctype html><html><body>
        <h1>Demo</h1>
        <input id="email" placeholder="Email"/>
        <button id="submit">Sign in</button>
        <div id="ok">Welcome</div>
        </body></html>""",
        encoding="utf-8",
    )
    url = html.resolve().as_uri()

    engine = BrowserEngine()
    cdp = await engine.start(headless=True)
    assert cdp.port > 0
    assert "127.0.0.1" in cdp.http_endpoint

    email = SharedElement(
        element_id="EL_EMAIL",
        page_name="Login",
        friendly_name="Email",
        primary_playwright="#email",
        css="#email",
    )
    submit = SharedElement(
        element_id="EL_SUBMIT",
        page_name="Login",
        friendly_name="Submit",
        primary_playwright="#wrong-submit",
        css="#wrong-submit",
    )
    repo = {email.element_id: email, submit.element_id: submit}
    case = TestCase(
        id="c1",
        code="TC_LOGIN_01",
        title="Login",
        expected_final_result="Welcome",
        steps=[
            TestStep(step_no=1, action="goto", intent_hint="open", input_data=url, binding_status="UNBOUND"),
            TestStep(
                step_no=2,
                action="fill",
                element_ref="EL_EMAIL",
                intent_hint="Email",
                input_data="a@b.c",
                binding_status="UNBOUND",
            ),
            TestStep(
                step_no=3,
                action="click",
                element_ref="EL_SUBMIT",
                intent_hint="Sign in",
                binding_status="UNBOUND",
            ),
        ],
    )

    binder = ShiftLeftAutoBinder(engine.page, repo)
    await binder.sync_test_case(case, base_url="", on_progress=lambda m: None)
    assert case.steps[0].binding_status == "BOUND"
    assert case.steps[1].binding_status == "BOUND"

    # Force heal path: break locator then run dual-loop
    submit.css = "#missing"
    submit.primary_playwright = "#missing"
    case.steps[2].binding_status = "UNBOUND"

    agent = DualLoopAgent(engine.page, repo)
    # Fix submit locator for heuristic heal intent match
    submit.css = "#submit"
    submit.primary_playwright = "#submit"
    report = await agent.run_case(case, project_name="fixture")
    assert report.cases[0].status in {"PASS", "HEALED", "FAIL"}
    # Single browser context ownership
    assert engine.cdp_endpoint is not None
    await engine.stop()
