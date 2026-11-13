from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

import pytest

STUB = Path("tests/fixtures/mcp_stub/server.py")


@pytest.mark.asyncio
async def test_qasync_compatible_stdio_stub_mcp() -> None:
    if sys.platform.startswith("win"):
        asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())

    proc = await asyncio.create_subprocess_exec(
        sys.executable,
        str(STUB),
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    assert proc.stdin and proc.stdout
    payload = json.dumps({"id": 1, "method": "ping"}) + "\n"
    proc.stdin.write(payload.encode())
    await proc.stdin.drain()
    line = await asyncio.wait_for(proc.stdout.readline(), timeout=5)
    data = json.loads(line.decode())
    assert data["result"]["ok"] is True
    proc.terminate()
    await proc.wait()
