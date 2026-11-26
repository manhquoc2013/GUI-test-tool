"""GitHub Releases updater: version gate, asset selection, verified download.

Only the stable channel is supported: the updater reads the repository's
``latest`` release, which GitHub never points at a prerelease or draft.
"""

from __future__ import annotations

import json
import logging
import platform
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from ai_qa_test_studio import __version__
from ai_qa_test_studio.constants import (
    APP_EXE_NAME,
    APP_UPDATE_ASSET_TEMPLATE_LINUX_DEB,
    APP_UPDATE_ASSET_TEMPLATE_LINUX_ZIP,
    APP_UPDATE_ASSET_TEMPLATE_MACOS_DMG,
    APP_UPDATE_ASSET_TEMPLATE_WINDOWS_SETUP,
    APP_UPDATE_ASSET_TEMPLATE_WINDOWS_ZIP,
    APP_UPDATE_CHECKSUMS_ASSET,
    APP_UPDATE_DOWNLOAD_CHUNK_BYTES,
    APP_UPDATE_LATEST_API_URL,
    APP_UPDATE_MAX_ARCHIVE_BYTES,
    APP_UPDATE_SIGNATURE_ASSET,
    APP_UPDATE_TIMEOUT_S,
    APP_UPDATE_USER_AGENT,
)
from ai_qa_test_studio.core.release_verify import (
    ReleaseVerificationError,
    load_pinned_cert,
    parse_checksums,
    sha256_file,
    verify_artifact_digest,
    verify_detached_signature,
)

logger = logging.getLogger(__name__)

Opener = Callable[..., Any]

_VERSION_TOKEN_RE = re.compile(r"\d+")


class UpdateError(RuntimeError):
    """User-facing updater failure (network, payload, or verification)."""


@dataclass(frozen=True, slots=True)
class ReleaseAsset:
    name: str
    url: str
    size: int


@dataclass(frozen=True, slots=True)
class ReleaseInfo:
    version: str
    tag: str
    html_url: str
    published_at: str
    notes: str
    assets: tuple[ReleaseAsset, ...]

    def asset(self, name: str) -> ReleaseAsset | None:
        for item in self.assets:
            if item.name == name:
                return item
        return None


@dataclass(frozen=True, slots=True)
class PreparedUpdate:
    """A downloaded artifact plus the signed manifest it was validated against."""

    release: ReleaseInfo
    asset: ReleaseAsset
    path: Path


def normalize_version(text: str) -> tuple[int, ...]:
    """Turn ``v4.1.6`` / ``4.1.6-rc1`` into a comparable numeric tuple."""
    digits = _VERSION_TOKEN_RE.findall((text or "").strip())
    if not digits:
        raise UpdateError(f"unparsable version: {text!r}")
    return tuple(int(part) for part in digits[:4])


def is_newer(candidate: str, current: str = __version__) -> bool:
    """True when ``candidate`` is strictly newer than ``current``."""
    return normalize_version(candidate) > normalize_version(current)


def parse_release(payload: Mapping[str, Any]) -> ReleaseInfo:
    """Validate the GitHub Releases JSON payload we depend on."""
    tag = str(payload.get("tag_name") or "").strip()
    version = str(payload.get("version") or tag.lstrip("vV") or "").strip()
    if not tag or not version:
        raise UpdateError("release payload is missing tag_name")
    assets_raw = payload.get("assets")
    if not isinstance(assets_raw, list):
        raise UpdateError("release payload is missing assets")
    assets: list[ReleaseAsset] = []
    for item in assets_raw:
        if not isinstance(item, Mapping):
            continue
        name = str(item.get("name") or "").strip()
        url = str(item.get("browser_download_url") or "").strip()
        if not name or not url:
            continue
        size = item.get("size")
        assets.append(
            ReleaseAsset(
                name=name,
                url=url,
                size=int(size) if isinstance(size, int) and size > 0 else 0,
            )
        )
    return ReleaseInfo(
        version=version,
        tag=tag,
        html_url=str(payload.get("html_url") or ""),
        published_at=str(payload.get("published_at") or ""),
        notes=str(payload.get("body") or ""),
        assets=tuple(assets),
    )


def host_arch() -> str:
    """Map the running interpreter to the arch token used in release asset names."""
    machine = platform.machine().lower()
    if machine in {"arm64", "aarch64"}:
        return "arm64"
    return "x64"


def select_platform_asset(
    release: ReleaseInfo,
    *,
    platform_id: str = sys.platform,
    arch: str | None = None,
    prefer_deb: bool = False,
) -> ReleaseAsset:
    """Pick the artifact this OS can install, in preference order."""
    resolved_arch = arch or host_arch()
    fields = {"exe": APP_EXE_NAME, "version": release.version, "arch": resolved_arch}

    def template(name: str) -> str:
        return name.format(**fields)

    if platform_id.startswith("win"):
        candidates: tuple[str, ...] = (
            template(APP_UPDATE_ASSET_TEMPLATE_WINDOWS_ZIP),
            template(APP_UPDATE_ASSET_TEMPLATE_WINDOWS_SETUP),
        )
    elif platform_id == "darwin":
        candidates = (template(APP_UPDATE_ASSET_TEMPLATE_MACOS_DMG),)
    else:
        deb = template(APP_UPDATE_ASSET_TEMPLATE_LINUX_DEB)
        portable = template(APP_UPDATE_ASSET_TEMPLATE_LINUX_ZIP)
        candidates = (deb, portable) if prefer_deb else (portable, deb)

    for name in candidates:
        asset = release.asset(name)
        if asset is not None:
            return asset
    raise UpdateError(f"release {release.tag} has no asset for {platform_id}/{resolved_arch}")


def _request(url: str) -> urllib.request.Request:
    return urllib.request.Request(
        url,
        headers={
            "User-Agent": APP_UPDATE_USER_AGENT,
            "Accept": "application/vnd.github+json",
        },
    )


class GitHubReleaseClient:
    """Read the stable ``latest`` release; never mutates remote state."""

    def __init__(
        self,
        *,
        url: str = APP_UPDATE_LATEST_API_URL,
        timeout_s: float = APP_UPDATE_TIMEOUT_S,
        opener: Opener | None = None,
    ) -> None:
        self._url = url
        self._timeout_s = timeout_s
        self._opener = opener

    def latest(self) -> ReleaseInfo:
        opener = self._opener or urllib.request.urlopen
        try:
            with opener(_request(self._url), timeout=self._timeout_s) as response:
                body = response.read()
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise UpdateError(f"release lookup failed: {exc}") from exc
        try:
            payload = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise UpdateError(f"release payload is not JSON: {exc}") from exc
        if not isinstance(payload, Mapping):
            raise UpdateError("release payload is not an object")
        return parse_release(payload)


def download_asset(
    asset: ReleaseAsset,
    dest: Path,
    *,
    timeout_s: float = APP_UPDATE_TIMEOUT_S,
    max_bytes: int = APP_UPDATE_MAX_ARCHIVE_BYTES,
    opener: Opener | None = None,
) -> Path:
    """Stream ``asset`` to ``dest`` while enforcing a hard size ceiling."""
    if max_bytes > 0 and asset.size > max_bytes:
        raise UpdateError(f"asset {asset.name} exceeds size limit")
    opener = opener or urllib.request.urlopen
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    written = 0
    try:
        with opener(_request(asset.url), timeout=timeout_s) as response:
            with tmp.open("wb") as handle:
                while True:
                    chunk = response.read(APP_UPDATE_DOWNLOAD_CHUNK_BYTES)
                    if not chunk:
                        break
                    written += len(chunk)
                    if max_bytes > 0 and written > max_bytes:
                        raise UpdateError(f"asset {asset.name} exceeds size limit")
                    handle.write(chunk)
    except UpdateError:
        tmp.unlink(missing_ok=True)
        raise
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        tmp.unlink(missing_ok=True)
        raise UpdateError(f"download failed for {asset.name}: {exc}") from exc
    tmp.replace(dest)
    return dest


def _download_small_text(
    asset: ReleaseAsset,
    *,
    timeout_s: float,
    max_bytes: int,
    opener: Opener | None,
) -> bytes:
    opener = opener or urllib.request.urlopen
    try:
        with opener(_request(asset.url), timeout=timeout_s) as response:
            body = bytes(response.read(max_bytes + 1))
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise UpdateError(f"download failed for {asset.name}: {exc}") from exc
    if len(body) > max_bytes:
        raise UpdateError(f"asset {asset.name} exceeds size limit")
    return body


def prepare_update(
    release: ReleaseInfo,
    dest_dir: Path,
    *,
    asset: ReleaseAsset,
    timeout_s: float = APP_UPDATE_TIMEOUT_S,
    opener: Opener | None = None,
    expected_fingerprint: str | None = None,
) -> PreparedUpdate:
    """Download the manifest + signature + artifact, then refuse anything unproven."""
    manifest_asset = release.asset(APP_UPDATE_CHECKSUMS_ASSET)
    signature_asset = release.asset(APP_UPDATE_SIGNATURE_ASSET)
    if manifest_asset is None or signature_asset is None:
        raise UpdateError(f"release {release.tag} is missing manifest/signature assets")

    manifest_bytes = _download_small_text(
        manifest_asset, timeout_s=timeout_s, max_bytes=APP_UPDATE_MAX_ARCHIVE_BYTES, opener=opener
    )
    signature_bytes = _download_small_text(
        signature_asset, timeout_s=timeout_s, max_bytes=APP_UPDATE_MAX_ARCHIVE_BYTES, opener=opener
    )

    cert = load_pinned_cert(expected_fingerprint=expected_fingerprint)
    try:
        verify_detached_signature(data=manifest_bytes, signature=signature_bytes, cert=cert)
    except ReleaseVerificationError as exc:
        raise UpdateError(str(exc)) from exc

    try:
        manifest = parse_checksums(manifest_bytes.decode("utf-8", errors="strict"))
    except UnicodeDecodeError as exc:
        raise UpdateError("checksum manifest is not UTF-8") from exc
    except ReleaseVerificationError as exc:
        raise UpdateError(str(exc)) from exc

    dest = dest_dir / asset.name
    download_asset(asset, dest, timeout_s=timeout_s, opener=opener)
    try:
        verify_artifact_digest(manifest, asset.name, sha256_file(dest))
    except ReleaseVerificationError as exc:
        dest.unlink(missing_ok=True)
        raise UpdateError(str(exc)) from exc
    return PreparedUpdate(release=release, asset=asset, path=dest)


def check_for_update(
    *,
    current_version: str = __version__,
    client: GitHubReleaseClient | None = None,
) -> ReleaseInfo | None:
    """Return the newer stable release, or ``None`` when already current."""
    release = (client or GitHubReleaseClient()).latest()
    if not is_newer(release.version, current_version):
        return None
    return release
