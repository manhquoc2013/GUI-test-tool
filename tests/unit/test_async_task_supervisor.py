from __future__ import annotations

import asyncio

import pytest

from ai_qa_test_studio.core.async_task_supervisor import AsyncTaskSupervisor


@pytest.mark.asyncio
async def test_register_replace_cancels_previous() -> None:
    supervisor = AsyncTaskSupervisor()
    started: list[str] = []
    cancelled: list[str] = []

    async def worker(name: str) -> None:
        started.append(name)
        try:
            await asyncio.sleep(10)
        except asyncio.CancelledError:
            cancelled.append(name)
            raise

    first = supervisor.register(worker("a"), key="run", replace=True)
    await asyncio.sleep(0)
    second = supervisor.register(worker("b"), key="run", replace=True)
    await asyncio.sleep(0)
    assert first.cancelled() or first.done()
    second.cancel()
    await asyncio.gather(first, second, return_exceptions=True)
    assert "a" in started
    assert "a" in cancelled or first.cancelled()


@pytest.mark.asyncio
async def test_lazy_bind_loop_on_register() -> None:
    supervisor = AsyncTaskSupervisor()
    assert supervisor._loop is None

    async def noop() -> str:
        return "ok"

    task = supervisor.register(noop(), key="k", replace=True)
    assert supervisor._loop is not None
    assert await task == "ok"


@pytest.mark.asyncio
async def test_emit_if_alive_skips_invalid_widget() -> None:
    supervisor = AsyncTaskSupervisor()
    supervisor.bind_loop()
    emitted: list[int] = []

    class _Sig:
        def emit(self, value: int) -> None:
            emitted.append(value)

    supervisor.emit_if_alive(None, _Sig(), 1)
    assert emitted == []


@pytest.mark.asyncio
async def test_cancel_key_stops_task() -> None:
    supervisor = AsyncTaskSupervisor()
    hit: list[str] = []

    async def worker() -> None:
        hit.append("start")
        try:
            await asyncio.sleep(10)
        except asyncio.CancelledError:
            hit.append("cancelled")
            raise

    task = supervisor.register(worker(), key="run:case-1", replace=True)
    await asyncio.sleep(0)
    await supervisor.cancel("run:case-1")
    assert task.cancelled() or task.done()
    assert "cancelled" in hit or task.cancelled()
