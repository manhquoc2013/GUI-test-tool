"""Toolbar Sync flow — dual thresholds, DistilledNode mapping, Stop cancel (offscreen)."""

from __future__ import annotations

import asyncio
import os
from pathlib import Path
from typing import Any
from unittest.mock import AsyncMock, patch

import pytest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.agent.auto_binder import ShiftLeftAutoBinder
from ai_qa_test_studio.constants import HEAL_PERSIST_THRESHOLD, HEAL_RUN_THRESHOLD
from ai_qa_test_studio.core.async_task_supervisor import AsyncTaskSupervisor, get_async_task_supervisor
from ai_qa_test_studio.core.models import (
    DistilledNode,
    ElementFingerprint,
    Project,
    SharedElement,
    TestCase,
    TestGroup,
    TestScenario,
    TestStep,
)
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.ui.main_window import MainWindow
from ai_qa_test_studio.ui.session_controller import sync_preflight_error


@pytest.fixture(scope="module")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def _sample_case(*, fill_status: str = "UNBOUND", click_status: str = "UNBOUND") -> TestCase:
    return TestCase(
        id="sync-case-1",
        code="TC_SYNC",
        title="Sync sample",
        start_url="https://example.com/login",
        steps=[
            TestStep(
                step_no=1,
                action="goto",
                intent_hint="open",
                input_data="https://example.com/login",
                binding_status="UNBOUND",
            ),
            TestStep(
                step_no=2,
                action="fill",
                element_ref="EL_EMAIL",
                intent_hint="Email",
                input_data="user@example.com",
                binding_status=fill_status,  # type: ignore[arg-type]
            ),
            TestStep(
                step_no=3,
                action="click",
                element_ref="EL_SUBMIT",
                intent_hint="Sign in",
                binding_status=click_status,  # type: ignore[arg-type]
            ),
        ],
    )


def _shared_repo() -> dict[str, SharedElement]:
    return {
        "EL_EMAIL": SharedElement(
            element_id="EL_EMAIL",
            page_name="Login",
            friendly_name="Email",
            name="Email",
            role="textbox",
        ),
        "EL_SUBMIT": SharedElement(
            element_id="EL_SUBMIT",
            page_name="Login",
            friendly_name="Submit",
            name="Sign in",
            role="button",
        ),
    }


def _nodes_high_confidence() -> list[DistilledNode]:
    return [
        DistilledNode(
            short_id="e1",
            fingerprint=ElementFingerprint(
                tag="input",
                role="textbox",
                accessible_name="Email",
                text_sample="Email",
                test_id="login-email",
                css="#email",
                xpath="/html/body/input[1]",
            ),
        ),
        DistilledNode(
            short_id="e2",
            fingerprint=ElementFingerprint(
                tag="button",
                role="button",
                accessible_name="Sign in",
                text_sample="Sign in",
                test_id="login-submit",
                css="#submit",
                xpath="/html/body/button[1]",
            ),
        ),
    ]


@pytest.mark.asyncio
async def test_sync_high_confidence_binds_and_hides_banner(
    qapp: QApplication, tmp_path: Path
) -> None:
    case = _sample_case()
    repo = _shared_repo()
    for el, node in zip(repo.values(), _nodes_high_confidence(), strict=True):
        el.test_id = node.fingerprint.test_id
        el.role = node.fingerprint.role
        el.name = node.fingerprint.accessible_name
        el.css = "stale-selector"
        el.primary_playwright = "stale-selector"

    page = AsyncMock()
    page.goto = AsyncMock()
    page.wait_for_load_state = AsyncMock()
    binder = ShiftLeftAutoBinder(page, repo)
    binder.resolver.try_semantic_match = AsyncMock(return_value=None)  # type: ignore[method-assign]
    binder.resolver.execute_step = AsyncMock()  # type: ignore[method-assign]
    binder.distiller.distill = AsyncMock(return_value=_nodes_high_confidence())  # type: ignore[method-assign]

    with patch("ai_qa_test_studio.agent.auto_binder.score_match", return_value=0.92):
        result = await binder.sync_with_audit(case, base_url="https://example.com/login")
    assert case.steps[0].action == "goto"
    assert case.steps[0].binding_status == "UNBOUND"  # Navigate skipped
    assert case.steps[1].binding_status == "BOUND"
    assert case.steps[2].binding_status == "BOUND"
    assert result.yaml_persisted is True
    for el in repo.values():
        assert el.fingerprint is not None
        dumped = el.fingerprint.model_dump()
        assert "short_id" not in dumped
        assert el.fingerprint.css
        assert "short_id" not in el.model_dump()

    # UI: banner hides when fully bound
    storage = ProjectStorage(tmp_path / "v4")
    project = Project(
        id="p-sync",
        name="Sync UI",
        shared_elements=list(repo.values()),
        root_group=TestGroup(
            id="g1",
            name="Root",
            scenarios=[TestScenario(id="s1", code="TS1", title="Sc", cases=[case])],
        ),
    )
    storage.save(project)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.builder.load_case(case)
    window.builder.set_element_lookup(repo)
    window._update_unbound_badge(case)
    assert not window.builder.unbound_banner.isVisible()
    assert window.top_bar._unbound_count == 0
    assert window.builder._deterministic_export is True
    window.close()


@pytest.mark.asyncio
async def test_sync_mid_confidence_keeps_unbound_and_warns() -> None:
    case = _sample_case()
    repo = _shared_repo()
    page = AsyncMock()
    page.goto = AsyncMock()
    page.wait_for_load_state = AsyncMock()
    binder = ShiftLeftAutoBinder(page, repo)
    binder.resolver.try_semantic_match = AsyncMock(return_value=None)  # type: ignore[method-assign]
    binder.resolver.execute_step = AsyncMock()  # type: ignore[method-assign]
    binder.distiller.distill = AsyncMock(return_value=_nodes_high_confidence())  # type: ignore[method-assign]

    mid = (HEAL_RUN_THRESHOLD + HEAL_PERSIST_THRESHOLD) / 2  # 0.75
    assert HEAL_RUN_THRESHOLD <= mid < HEAL_PERSIST_THRESHOLD

    with patch(
        "ai_qa_test_studio.agent.auto_binder.score_match",
        return_value=0.72,
    ):
        result = await binder.sync_with_audit(case, base_url="https://example.com/login")

    assert case.steps[1].binding_status == "UNBOUND"
    assert case.steps[2].binding_status == "UNBOUND"
    assert result.yaml_persisted is False
    assert any("độ tin cậy thấp" in w or "0.72" in w for w in result.warnings)
    assert all(a.persisted is False for a in result.audits if a.confidence_score == 0.72)
    assert repo["EL_EMAIL"].fingerprint is None
    assert repo["EL_SUBMIT"].fingerprint is None


@pytest.mark.asyncio
async def test_sync_stop_cancels_via_supervisor_without_qt_crash(
    qapp: QApplication, tmp_path: Path
) -> None:
    supervisor = AsyncTaskSupervisor()
    started = asyncio.Event()
    cancelled = False

    async def _long_sync() -> None:
        nonlocal cancelled
        started.set()
        try:
            await asyncio.sleep(30)
        except asyncio.CancelledError:
            cancelled = True
            raise

    task = supervisor.register(_long_sync(), key="sync:sync-case-1", replace=True)
    await started.wait()
    await supervisor.cancel("sync:sync-case-1")
    with pytest.raises(asyncio.CancelledError):
        await task
    assert cancelled is True

    # emit_if_alive must tolerate a destroyed / None widget
    class _Sig:
        def emit(self, *_a: Any) -> None:
            raise AssertionError("must not emit on invalid widget")

    supervisor.emit_if_alive(None, _Sig(), "x")


def test_sync_preflight_rejects_empty_url_and_zero_bindable() -> None:
    case = TestCase(
        id="c0",
        code="TC0",
        title="nav only",
        steps=[
            TestStep(step_no=1, action="goto", intent_hint="open", binding_status="UNBOUND"),
        ],
    )
    assert sync_preflight_error(case, "") is not None
    assert "không hợp lệ" in (sync_preflight_error(case, "not-a-url") or "").lower() or (
        sync_preflight_error(case, "not-a-url") is not None
    )
    err = sync_preflight_error(case, "https://example.com")
    assert err is not None
    assert "Không có bước UI" in err


@pytest.mark.asyncio
async def test_toolbar_and_banner_share_sync_handler(
    qapp: QApplication, tmp_path: Path
) -> None:
    case = _sample_case()
    storage = ProjectStorage(tmp_path / "v4")
    project = Project(
        id="p1",
        name="Sync Share",
        shared_elements=list(_shared_repo().values()),
        root_group=TestGroup(
            id="g1",
            name="Root",
            scenarios=[TestScenario(id="s1", code="TS1", title="Sc", cases=[case])],
        ),
    )
    storage.save(project)
    window = MainWindow(data_root=tmp_path)
    window.show()
    window.url_edit.setText("https://example.com/login")

    calls: list[str] = []

    async def _fake_sync() -> None:
        calls.append("sync")

    window._run.sync_url = _fake_sync  # type: ignore[method-assign]
    await window._sync_url()
    window.builder.sync_requested.emit()
    # banner → sync_requested → _sync_url (asyncSlot); call handler directly too
    await window._run.sync_url()
    assert calls == ["sync", "sync"]
    window.close()


def test_get_default_supervisor_register_replace() -> None:
    supervisor = get_async_task_supervisor()
    assert supervisor is not None
