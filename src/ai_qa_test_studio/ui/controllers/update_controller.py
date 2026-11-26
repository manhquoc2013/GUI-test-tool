"""Update check + verified install orchestration (qasync-safe, cancel-aware)."""

from __future__ import annotations

import asyncio
import logging
import sys
from typing import TYPE_CHECKING

from PySide6.QtCore import Qt, QUrl, Signal
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import QMessageBox, QWidget
from qfluentwidgets import InfoBar, InfoBarPosition, PushButton

from ai_qa_test_studio import __version__
from ai_qa_test_studio.constants import (
    APP_EXE_NAME,
    UI_INFOBAR_DURATION_MS,
    UI_UPDATE_AVAILABLE,
    UI_UPDATE_CONFIRM_BODY,
    UI_UPDATE_CONFIRM_TITLE,
    UI_UPDATE_DOWNLOAD_FAILED,
    UI_UPDATE_MACOS_HINT,
    UI_UPDATE_NEEDS_ADMIN,
    UI_UPDATE_OFFLINE,
    UI_UPDATE_TITLE,
    UI_UPDATE_UP_TO_DATE,
    UI_UPDATE_VERIFYING,
    UPDATE_CHECK_TASK_KEY,
    UPDATE_INSTALL_TASK_KEY,
)
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.core.update_installer import (
    InstallError,
    is_install_writable,
    launch_swap_helper,
    safe_extract_zip,
    staging_dir,
)
from ai_qa_test_studio.core.updater import (
    GitHubReleaseClient,
    PreparedUpdate,
    ReleaseInfo,
    UpdateError,
    is_newer,
    prepare_update,
    select_platform_asset,
)

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow

logger = logging.getLogger(__name__)


class _UpdateBridge(QWidget):
    """Host widget so background results reach the UI thread safely."""

    checked = Signal(object, object)  # release: ReleaseInfo | None, error: str | None
    prepared = Signal(object, object)  # update: PreparedUpdate | None, error: str | None

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.hide()


class UpdateController:
    """Stable-channel updater: check → confirm → download → verify → swap."""

    def __init__(self, window: MainWindow) -> None:
        self._w = window
        self._bridge = _UpdateBridge(window)
        self._bridge.checked.connect(self._on_checked)
        self._bridge.prepared.connect(self._on_prepared)
        self._manual = False

    def check(self, *, manual: bool) -> None:
        self._manual = manual
        get_async_task_supervisor().register(self._check_async(), key=UPDATE_CHECK_TASK_KEY, replace=True)

    def check_on_startup(self) -> None:
        if not self._w.env.settings.auto_update_check:
            return
        self.check(manual=False)

    async def _check_async(self) -> None:
        supervisor = get_async_task_supervisor()
        release: ReleaseInfo | None = None
        error: str | None = None
        try:
            candidate = await asyncio.to_thread(lambda: GitHubReleaseClient().latest())
            release = candidate if is_newer(candidate.version, __version__) else None
        except UpdateError as exc:
            error = str(exc)
        except Exception as exc:  # noqa: BLE001 — never break the shell
            logger.warning("update_check_failed err=%s", exc)
            error = str(exc)
        supervisor.emit_if_alive(self._bridge, self._bridge.checked, release, error)

    def _on_checked(self, release: object, error: object) -> None:
        w = self._w
        if error:
            if self._manual:
                w._toast_warn(UI_UPDATE_TITLE, UI_UPDATE_OFFLINE)
            return
        if release is None:
            if self._manual:
                w._toast_info(UI_UPDATE_TITLE, UI_UPDATE_UP_TO_DATE.format(version=__version__))
            return
        if not isinstance(release, ReleaseInfo):
            return
        self._announce(release)

    def _announce(self, release: ReleaseInfo) -> None:
        w = self._w
        bar = InfoBar.info(
            title=UI_UPDATE_TITLE,
            content=UI_UPDATE_AVAILABLE.format(version=release.version, current=__version__),
            orient=Qt.Orientation.Horizontal,
            isClosable=True,
            position=InfoBarPosition.TOP,
            duration=UI_INFOBAR_DURATION_MS * 3,
            parent=w,
        )
        button = PushButton("Cập nhật", bar)
        button.clicked.connect(lambda: self._confirm(release))
        bar.widgetLayout.addWidget(button)

    def _confirm(self, release: ReleaseInfo) -> None:
        box = QMessageBox(self._w)
        box.setWindowTitle(UI_UPDATE_CONFIRM_TITLE)
        box.setText(UI_UPDATE_CONFIRM_BODY.format(version=release.version))
        notes = release.notes.strip()
        if notes:
            box.setDetailedText(notes[:4000])
        box.setStandardButtons(QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
        box.setDefaultButton(QMessageBox.StandardButton.No)
        if box.exec() != QMessageBox.StandardButton.Yes:
            return
        get_async_task_supervisor().register(self._install_async(release), key=UPDATE_INSTALL_TASK_KEY, replace=True)

    async def _install_async(self, release: ReleaseInfo) -> None:
        supervisor = get_async_task_supervisor()
        prepared: PreparedUpdate | None = None
        error: str | None = None
        try:
            asset = select_platform_asset(release)
            prepared = await asyncio.to_thread(
                lambda: prepare_update(release, staging_dir(release.version), asset=asset)
            )
        except (UpdateError, InstallError) as exc:
            error = str(exc)
        except Exception as exc:  # noqa: BLE001
            logger.warning("update_install_failed err=%s", exc)
            error = str(exc)
        supervisor.emit_if_alive(self._bridge, self._bridge.prepared, prepared, error)

    def _on_prepared(self, prepared: object, error: object) -> None:
        w = self._w
        if error or not isinstance(prepared, PreparedUpdate):
            w._toast_error(
                UI_UPDATE_TITLE,
                UI_UPDATE_DOWNLOAD_FAILED.format(error=error or "unknown"),
            )
            return
        try:
            self._apply(prepared)
        except InstallError as exc:
            w._toast_error(UI_UPDATE_TITLE, str(exc))
        except OSError as exc:
            w._toast_error(UI_UPDATE_TITLE, UI_UPDATE_DOWNLOAD_FAILED.format(error=exc))

    def _apply(self, prepared: PreparedUpdate) -> None:
        w = self._w
        if sys.platform == "darwin":
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(prepared.path)))
            w._toast_info(UI_UPDATE_TITLE, UI_UPDATE_MACOS_HINT)
            return
        if not is_install_writable():
            w._toast_warn(UI_UPDATE_TITLE, UI_UPDATE_NEEDS_ADMIN.format(url=prepared.release.html_url))
            return
        w._toast_info(UI_UPDATE_TITLE, UI_UPDATE_VERIFYING)
        staging = staging_dir(prepared.release.version)
        payload = safe_extract_zip(prepared.path, staging / "payload")
        exe_name = f"{APP_EXE_NAME}.exe" if sys.platform.startswith("win") else APP_EXE_NAME
        launch_swap_helper(
            staging_payload=payload,
            version=prepared.release.version,
            exe_name=exe_name,
        )
        w.close()
