"""Updater unit tests: version gate, asset picks, signed download, tamper refusal."""

from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
from typing import Any

import pysequoia
import pytest
from pysequoia import SignatureMode, Tsk

from ai_qa_test_studio.constants import APP_EXE_NAME
from ai_qa_test_studio.core import updater
from ai_qa_test_studio.core.release_verify import ReleaseVerificationError
from ai_qa_test_studio.core.updater import (
    GitHubReleaseClient,
    ReleaseAsset,
    UpdateError,
    is_newer,
    normalize_version,
    parse_release,
    prepare_update,
    select_platform_asset,
)

_VERSION = "4.2.0"


def _release_payload(*names: str) -> dict[str, Any]:
    return {
        "tag_name": f"v{_VERSION}",
        "html_url": "https://github.com/o/r/releases/tag/v4.2.0",
        "published_at": "2026-11-26T00:00:00Z",
        "body": "notes",
        "assets": [
            {
                "name": name,
                "browser_download_url": f"https://example.invalid/{name}",
                "size": 4,
            }
            for name in names
        ],
    }


class _FakeOpener:
    """Serve canned bytes per URL; records requested URLs."""

    def __init__(self, payloads: dict[str, bytes]) -> None:
        self._payloads = payloads
        self.requested: list[str] = []

    def __call__(self, request: Any, timeout: float = 0) -> io.BytesIO:
        url = request.full_url
        self.requested.append(url)
        if url not in self._payloads:
            raise AssertionError(f"unexpected URL {url}")
        return io.BytesIO(self._payloads[url])


@pytest.fixture(scope="module")
def signing_key() -> Tsk:
    return Tsk.generate(user_id="Updater Test <test@aiqa.local>")


def test_normalize_version_and_is_newer() -> None:
    assert normalize_version("v4.1.6") == (4, 1, 6)
    assert normalize_version("4.2.0-rc1") == (4, 2, 0, 1)
    assert is_newer("4.2.0", "4.1.6")
    assert not is_newer("4.1.6", "4.1.6")
    assert not is_newer("4.1.5", "4.1.6")
    with pytest.raises(UpdateError):
        normalize_version("nightly")


def test_parse_release_requires_tag_and_assets() -> None:
    info = parse_release(_release_payload("a.zip"))
    assert info.version == _VERSION
    assert info.tag == "v4.2.0"
    assert info.asset("a.zip") is not None
    with pytest.raises(UpdateError):
        parse_release({"assets": []})
    with pytest.raises(UpdateError):
        parse_release({"tag_name": "v4.2.0"})


def test_select_asset_per_platform() -> None:
    names = (
        f"{APP_EXE_NAME}-{_VERSION}-windows-x64.zip",
        f"{APP_EXE_NAME}-{_VERSION}-windows-x64-Setup.exe",
        f"{APP_EXE_NAME}-{_VERSION}-macos-arm64.dmg",
        f"{APP_EXE_NAME}-{_VERSION}-linux-x64.zip",
        f"{APP_EXE_NAME}-{_VERSION}-linux-x64.deb",
    )
    release = parse_release(_release_payload(*names))
    assert select_platform_asset(release, platform_id="win32", arch="x64").name == names[0]
    assert select_platform_asset(release, platform_id="darwin", arch="arm64").name == names[2]
    assert select_platform_asset(release, platform_id="linux", arch="x64").name == names[3]
    assert select_platform_asset(release, platform_id="linux", arch="x64", prefer_deb=True).name == names[4]
    with pytest.raises(UpdateError):
        select_platform_asset(release, platform_id="linux", arch="arm64")


def test_latest_release_reads_payload() -> None:
    opener = _FakeOpener({updater.APP_UPDATE_LATEST_API_URL: json.dumps(_release_payload("x.zip")).encode()})
    release = GitHubReleaseClient(opener=opener).latest()
    assert release.version == _VERSION


def test_latest_release_surfaces_network_errors() -> None:
    def boom(request: Any, timeout: float = 0) -> Any:
        raise OSError("no route to host")

    with pytest.raises(UpdateError):
        GitHubReleaseClient(opener=boom).latest()


def test_prepare_update_downloads_and_verifies(
    signing_key: Tsk, monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    asset_name = f"{APP_EXE_NAME}-{_VERSION}-linux-x64.zip"
    artifact = b"PK\x03\x04payload"
    digest = hashlib.sha256(artifact).hexdigest()
    manifest = f"{digest}  {asset_name}\n".encode()
    signature = pysequoia.sign(signing_key.signer(), manifest, mode=SignatureMode.DETACHED, armor=True)
    release = parse_release(_release_payload(asset_name, "SHA256SUMS", "SHA256SUMS.asc"))
    urls = {asset.url: asset.name for asset in release.assets}
    blobs = {"SHA256SUMS": manifest, "SHA256SUMS.asc": signature, asset_name: artifact}
    payloads = {url: blobs[name] for url, name in urls.items()}
    monkeypatch.setattr(updater, "load_pinned_cert", lambda **_kw: signing_key.extract_certificate())

    prepared = prepare_update(
        release,
        tmp_path,
        asset=release.asset(asset_name) or pytest.fail("asset missing"),
        opener=_FakeOpener(payloads),
    )
    assert prepared.path.read_bytes() == artifact


def test_prepare_update_rejects_tampered_artifact(
    signing_key: Tsk, monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    asset_name = f"{APP_EXE_NAME}-{_VERSION}-linux-x64.zip"
    good = b"PK\x03\x04good"
    tampered = b"PK\x03\x04evil"
    manifest = f"{hashlib.sha256(good).hexdigest()}  {asset_name}\n".encode()
    signature = pysequoia.sign(signing_key.signer(), manifest, mode=SignatureMode.DETACHED, armor=True)
    release = parse_release(_release_payload(asset_name, "SHA256SUMS", "SHA256SUMS.asc"))
    payloads = {
        asset.url: {"SHA256SUMS": manifest, "SHA256SUMS.asc": signature, asset_name: tampered}[asset.name]
        for asset in release.assets
    }
    monkeypatch.setattr(updater, "load_pinned_cert", lambda **_kw: signing_key.extract_certificate())

    with pytest.raises(UpdateError):
        prepare_update(
            release,
            tmp_path,
            asset=release.asset(asset_name) or pytest.fail("asset missing"),
            opener=_FakeOpener(payloads),
        )


def test_prepare_update_rejects_unsigned_manifest(
    signing_key: Tsk, monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    asset_name = f"{APP_EXE_NAME}-{_VERSION}-linux-x64.zip"
    artifact = b"PK\x03\x04payload"
    manifest = f"{hashlib.sha256(artifact).hexdigest()}  {asset_name}\n".encode()
    stranger = Tsk.generate(user_id="Stranger <evil@example.com>")
    signature = pysequoia.sign(stranger.signer(), manifest, mode=SignatureMode.DETACHED, armor=True)
    release = parse_release(_release_payload(asset_name, "SHA256SUMS", "SHA256SUMS.asc"))
    payloads = {
        asset.url: {"SHA256SUMS": manifest, "SHA256SUMS.asc": signature, asset_name: artifact}[asset.name]
        for asset in release.assets
    }
    monkeypatch.setattr(updater, "load_pinned_cert", lambda **_kw: signing_key.extract_certificate())

    with pytest.raises(UpdateError):
        prepare_update(
            release,
            tmp_path,
            asset=release.asset(asset_name) or pytest.fail("asset missing"),
            opener=_FakeOpener(payloads),
        )


def test_prepare_update_requires_manifest_assets() -> None:
    release = parse_release(_release_payload("only.zip"))
    with pytest.raises(UpdateError):
        prepare_update(
            release,
            Path("."),
            asset=ReleaseAsset(name="only.zip", url="https://example.invalid/only.zip", size=1),
        )


def test_bundled_signing_key_matches_pin() -> None:
    from ai_qa_test_studio.core.release_verify import load_pinned_cert

    cert = load_pinned_cert()
    assert cert.fingerprint.lower() in {
        "71c0641e59937ea0f96bab522671d908dc2c8156",
    }
    assert not cert.is_revoked


def test_fingerprint_mismatch_is_fatal() -> None:
    from ai_qa_test_studio.core.release_verify import load_pinned_cert

    with pytest.raises(ReleaseVerificationError):
        load_pinned_cert(expected_fingerprint="0" * 40)
