from __future__ import annotations

import pytest

from ai_qa_test_studio.constants import FALLBACK_BLANK_URL
from ai_qa_test_studio.ui.session_controller import SessionController


class _FakePage:
    async def set_viewport_size(self, _size: dict[str, int]) -> None:
        return None


class _FakeEngine:
    def __init__(self, fail_urls: set[str]) -> None:
        self.fail_urls = fail_urls
        self.visited: list[str] = []
        self._page = _FakePage()

    @property
    def page(self) -> _FakePage:
        return self._page

    async def start(self, headless: bool = True) -> object:
        return object()

    async def goto(self, url: str, *, timeout_ms: int = 0) -> None:
        self.visited.append(url)
        if url in self.fail_urls:
            raise RuntimeError(f"net::ERR_NAME_NOT_RESOLVED at {url}")


@pytest.mark.asyncio
async def test_launch_url_falls_back_to_blank_on_dns_error() -> None:
    session = SessionController()
    fake = _FakeEngine(fail_urls={"https://staging.example.com/"})
    session.engine = fake  # type: ignore[assignment]
    session._started = True
    logs: list[str] = []

    opened = await session.launch_url("https://staging.example.com/", log=logs.append)
    assert opened == FALLBACK_BLANK_URL
    assert session.started is True
    assert fake.visited == ["https://staging.example.com/", FALLBACK_BLANK_URL]
    assert any("Không điều hướng" in line for line in logs)
