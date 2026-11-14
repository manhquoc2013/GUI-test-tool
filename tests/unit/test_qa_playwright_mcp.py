from __future__ import annotations

from ai_qa_test_studio.agent.mcp_servers.qa_playwright import QaPlaywrightServer


def test_screenshot_without_page_available_false() -> None:
    server = QaPlaywrightServer()
    result = server.screenshot()
    assert result["available"] is False
    assert result.get("png_b64", "") == ""


def test_cdp_info_after_bind() -> None:
    server = QaPlaywrightServer()
    server.bind_cdp("http://127.0.0.1:9222")
    info = server.cdp_info()
    assert info["endpoint"] == "http://127.0.0.1:9222"
