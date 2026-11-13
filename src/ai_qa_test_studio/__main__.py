"""Application entry: Windows Proactor + qasync unified loop."""

from __future__ import annotations

import asyncio
import logging
import sys
from pathlib import Path


def _configure_loop_policy() -> None:
    if sys.platform.startswith("win"):
        asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())


def _ensure_playwright_browsers() -> None:
    """Best-effort first-run Chromium check (non-blocking message only here)."""
    try:
        from playwright.sync_api import sync_playwright

        with sync_playwright() as p:
            _ = p.chromium
    except Exception as exc:  # noqa: BLE001
        logging.getLogger(__name__).warning("playwright_probe_failed err=%s", exc)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    _configure_loop_policy()

    import qasync
    from PySide6.QtWidgets import QApplication

    from ai_qa_test_studio.ui.main_window import MainWindow
    from ai_qa_test_studio.ui.theme import apply_theme

    app = QApplication(sys.argv)
    apply_theme(app, "dark")
    loop = qasync.QEventLoop(app)
    asyncio.set_event_loop(loop)

    window = MainWindow(data_root=Path("data"))
    window.show()

    with loop:
        loop.run_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
