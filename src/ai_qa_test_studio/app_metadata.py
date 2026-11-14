"""Release / packaging metadata (SSOT for --print-metadata-json)."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio import __version__
from ai_qa_test_studio.constants import (
    APP_COPYRIGHT,
    APP_DISPLAY_NAME,
    APP_EXE_NAME,
    APP_IDENTIFIER,
    APP_PUBLISHER,
    APP_PUBLISHER_EMAIL,
    APP_PUBLISHER_URL,
    APP_SUPPORT_DIRNAME,
)


def metadata_dict() -> dict[str, Any]:
    return {
        "version": __version__,
        "display_name": APP_DISPLAY_NAME,
        "exe_name": APP_EXE_NAME,
        "publisher": APP_PUBLISHER,
        "publisher_email": APP_PUBLISHER_EMAIL,
        "publisher_url": APP_PUBLISHER_URL,
        "identifier": APP_IDENTIFIER,
        "copyright": APP_COPYRIGHT,
        "support_dirname": APP_SUPPORT_DIRNAME,
    }
