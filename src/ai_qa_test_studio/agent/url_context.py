"""Fetch http(s) page structure for scenario/research prompts when a reader is ready."""

from __future__ import annotations

import asyncio
import logging
import re
from html.parser import HTMLParser
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from ai_qa_test_studio.constants import (
    COPILOT_URL_BROWSER_OFF_NOTE,
    URL_CONTEXT_MAX_CHARS,
    URL_FETCH_TIMEOUT_S,
    URL_HTTP_RE,
)

logger = logging.getLogger(__name__)

_URL_RE = re.compile(URL_HTTP_RE, re.IGNORECASE)


class _TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._chunks: list[str] = []
        self._skip = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        del attrs
        if tag in {"script", "style", "noscript"}:
            self._skip = True

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "noscript"}:
            self._skip = False
        if tag in {"p", "div", "li", "tr", "h1", "h2", "h3", "h4", "br"}:
            self._chunks.append("\n")

    def handle_data(self, data: str) -> None:
        if self._skip:
            return
        text = " ".join(data.split())
        if text:
            self._chunks.append(text)

    def text(self) -> str:
        return "\n".join(part for part in "".join(self._chunks).splitlines() if part.strip())


def extract_http_urls(text: str) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for match in _URL_RE.finditer(text or ""):
        url = match.group(0).rstrip(".,;:)")
        if url in seen:
            continue
        seen.add(url)
        out.append(url)
    return out


def _http_fetch_sync(url: str, *, timeout_s: float = URL_FETCH_TIMEOUT_S) -> str:
    req = Request(
        url,
        headers={"User-Agent": "AI-QA-Test-Studio/4.2 (+scenario-designer)"},
        method="GET",
    )
    with urlopen(req, timeout=timeout_s) as resp:  # noqa: S310 — user-supplied URL for QA research
        raw = resp.read(URL_CONTEXT_MAX_CHARS * 4)
        charset = "utf-8"
        ctype = str(resp.headers.get("Content-Type") or "")
        if "charset=" in ctype.lower():
            charset = ctype.lower().split("charset=", 1)[1].split(";")[0].strip() or charset
        text = raw.decode(charset, errors="replace")
    if "<html" in text.lower() or "<body" in text.lower():
        parser = _TextExtractor()
        parser.feed(text)
        text = parser.text()
    clipped: str = text[:URL_CONTEXT_MAX_CHARS].strip()
    return clipped


def _playwright_page_ready(mcp: Any | None) -> bool:
    if mcp is None:
        return False
    playwright = getattr(mcp, "playwright", None)
    if playwright is None:
        return False
    page = None
    resolve = getattr(playwright, "_resolve_page", None)
    if callable(resolve):
        try:
            page = resolve()
        except Exception:  # noqa: BLE001
            page = None
    cdp = getattr(playwright, "cdp_info", None)
    cdp_ok = False
    if callable(cdp):
        try:
            info = cdp()
            cdp_ok = bool(isinstance(info, dict) and info.get("available"))
        except Exception:  # noqa: BLE001
            cdp_ok = False
    return page is not None or cdp_ok


async def enrich_user_text_with_urls(
    user_text: str,
    *,
    mcp: Any | None = None,
) -> tuple[str, str | None]:
    """Return (augmented prompt, optional one-line note when browser/MCP is off)."""
    urls = extract_http_urls(user_text)
    if not urls:
        return user_text, None
    browser_ready = _playwright_page_ready(mcp)
    blocks: list[str] = []
    note: str | None = None if browser_ready else COPILOT_URL_BROWSER_OFF_NOTE
    for url in urls[:3]:
        try:
            body = await asyncio.to_thread(_http_fetch_sync, url)
        except (HTTPError, URLError, TimeoutError, OSError, ValueError) as exc:
            logger.info("url_context_fetch_failed url=%s err=%s", url, exc)
            blocks.append(f"### URL (fetch failed): {url}\n{exc}")
            continue
        if not body:
            blocks.append(f"### URL (empty): {url}")
            continue
        blocks.append(f"### URL page structure: {url}\n{body}")
    if not blocks:
        return user_text, note
    augmented = f"{user_text.strip()}\n\n" + "\n\n".join(blocks)
    return augmented, note
