"""Cancel-aware task registry with Qt-safe signal emit (lazy loop bind)."""

from __future__ import annotations

import asyncio
import logging
import threading
from collections.abc import Coroutine
from typing import Any

logger = logging.getLogger(__name__)

def _shiboken_is_valid(obj: Any) -> bool:
    try:
        from shiboken6 import isValid

        return bool(isValid(obj))
    except ImportError:  # pragma: no cover - PySide6 always ships shiboken6
        return obj is not None


class AsyncTaskSupervisor:
    """Register/cancel asyncio tasks by key; emit Signals only when widget still alive."""

    def __init__(self) -> None:
        self._loop: asyncio.AbstractEventLoop | None = None
        self._tasks: dict[str, asyncio.Task[Any]] = {}
        self._lock = threading.Lock()

    def bind_loop(self) -> None:
        self._loop = asyncio.get_running_loop()

    def register(
        self,
        coro: Coroutine[Any, Any, Any],
        *,
        key: str,
        replace: bool = True,
    ) -> asyncio.Task[Any]:
        if self._loop is None:
            self.bind_loop()
        assert self._loop is not None
        with self._lock:
            existing = self._tasks.get(key)
            if existing is not None and not existing.done():
                if not replace:
                    raise RuntimeError(f"task already registered key={key}")
                existing.cancel()
            task = self._loop.create_task(coro, name=key)
            self._tasks[key] = task

            def _cleanup(done: asyncio.Task[Any]) -> None:
                with self._lock:
                    current = self._tasks.get(key)
                    if current is done:
                        self._tasks.pop(key, None)

            task.add_done_callback(_cleanup)
            return task

    async def cancel_all(self) -> None:
        with self._lock:
            tasks = list(self._tasks.values())
        for task in tasks:
            if not task.done():
                task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    async def wait(self, key: str | None = None) -> None:
        with self._lock:
            if key is None:
                tasks = list(self._tasks.values())
            else:
                task = self._tasks.get(key)
                tasks = [task] if task is not None else []
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    def emit_if_alive(self, widget: Any, signal: Any, *args: Any) -> None:
        def _safe_emit() -> None:
            try:
                if widget is None or not _shiboken_is_valid(widget):
                    return
                signal.emit(*args)
            except RuntimeError as exc:
                logger.debug("emit_if_alive_skipped err=%s", exc)

        loop = self._loop
        try:
            running = asyncio.get_running_loop()
        except RuntimeError:
            running = None
        if loop is not None and running is not loop:
            loop.call_soon_threadsafe(_safe_emit)
            return
        if running is not None and threading.current_thread() is not threading.main_thread():
            running.call_soon_threadsafe(_safe_emit)
            return
        _safe_emit()


_DEFAULT_SUPERVISOR = AsyncTaskSupervisor()


def get_async_task_supervisor() -> AsyncTaskSupervisor:
    return _DEFAULT_SUPERVISOR
