"""Run async repository calls from sync Qt / pytest callers safely."""

from __future__ import annotations

import asyncio
import concurrent.futures
from collections.abc import Coroutine
from typing import TypeVar

T = TypeVar("T")


def run_sync(coro: Coroutine[object, object, T]) -> T:
    """Execute ``coro`` to completion from sync code.

    Uses ``asyncio.run`` when no loop is running; otherwise runs the coroutine
    in a worker thread with its own event loop so qasync's loop is never nested.
    """
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(coro)

    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        return pool.submit(asyncio.run, coro).result()
