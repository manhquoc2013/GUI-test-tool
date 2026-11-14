"""Application entry: Windows Proactor + qasync unified loop."""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import sys


def _configure_loop_policy() -> None:
    if sys.platform.startswith("win"):
        asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())


def _parse_early_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(add_help=True)
    parser.add_argument("--version", action="store_true", help="Print app version and exit")
    parser.add_argument(
        "--print-metadata-json",
        action="store_true",
        help="Print packaging metadata JSON (no Qt) and exit",
    )
    parser.add_argument(
        "--smoke-check",
        action="store_true",
        help="Load Qt + runtime_paths then exit 0 (packaging CI)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    raw = list(sys.argv[1:] if argv is None else argv)
    ns = _parse_early_args(raw)

    if ns.version or ns.print_metadata_json:
        from ai_qa_test_studio import __version__
        from ai_qa_test_studio.app_metadata import metadata_dict

        if ns.print_metadata_json:
            print(json.dumps(metadata_dict(), ensure_ascii=False))
            return 0
        print(__version__)
        return 0

    # Before any Playwright / heavy app imports that may touch browsers
    from ai_qa_test_studio.runtime_paths import (
        app_dir,
        apply_playwright_browsers_env,
        browsers_dir,
        data_root,
        used_user_data_fallback,
    )

    apply_playwright_browsers_env()

    if ns.smoke_check:
        from PySide6.QtWidgets import QApplication

        from ai_qa_test_studio.ui.qt_compat import ensure_qt_api, install_platform_noise_filters

        ensure_qt_api()
        install_platform_noise_filters()
        app = QApplication([])
        _ = (app_dir(), data_root(), browsers_dir(), app.applicationName())
        return 0

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    _configure_loop_policy()

    import qasync
    from PySide6.QtCore import QCoreApplication
    from PySide6.QtWidgets import QApplication

    from ai_qa_test_studio.constants import (
        APP_DISPLAY_NAME,
        APP_PUBLISHER,
        UI_DATA_FALLBACK_NOTICE,
    )
    from ai_qa_test_studio.core.env_manager import EnvManager
    from ai_qa_test_studio.resources import load_app_icon
    from ai_qa_test_studio.ui.main_window import MainWindow
    from ai_qa_test_studio.ui.qt_compat import ensure_qt_api, install_platform_noise_filters
    from ai_qa_test_studio.ui.theme import apply_studio_theme

    ensure_qt_api()
    install_platform_noise_filters()

    root = data_root()
    env = EnvManager(root / "config")
    env.load_settings()

    app = QApplication(sys.argv)
    QCoreApplication.setOrganizationName(APP_PUBLISHER)
    app.setApplicationName(APP_DISPLAY_NAME)
    app_icon = load_app_icon()
    app.setWindowIcon(app_icon)
    apply_studio_theme(app, preference=env.settings.theme)
    loop = qasync.QEventLoop(app)
    asyncio.set_event_loop(loop)

    window = MainWindow(data_root=root)
    window.setWindowIcon(app_icon)
    window.show()
    if used_user_data_fallback():
        window._toast_warn("Data", UI_DATA_FALLBACK_NOTICE)

    with loop:
        loop.run_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
