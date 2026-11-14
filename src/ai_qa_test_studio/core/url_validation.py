"""Target URL gate for toolbar Launch / Sync / Run."""

from __future__ import annotations

from urllib.parse import urlparse

from ai_qa_test_studio.constants import RUN_ALLOWED_URL_SCHEMES


def is_valid_target_url(url: str) -> bool:
    """Return True when *url* is a non-empty http(s) or file URL with a usable target."""
    text = (url or "").strip()
    if not text:
        return False
    parsed = urlparse(text)
    scheme = (parsed.scheme or "").lower()
    if scheme not in RUN_ALLOWED_URL_SCHEMES:
        return False
    if scheme == "file":
        return bool(parsed.path)
    return bool(parsed.netloc)
