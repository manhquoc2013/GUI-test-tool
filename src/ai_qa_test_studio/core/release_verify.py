"""Release artifact authenticity: pinned OpenPGP key + SHA256SUMS manifest.

The updater refuses to install anything that is not signed by the release
signing key baked into the application, so a compromised GitHub account or a
DNS/TLS interception cannot push code into a running install.
"""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

import pysequoia
from pysequoia import Cert, Sig

from ai_qa_test_studio.constants import (
    APP_UPDATE_DOWNLOAD_CHUNK_BYTES,
    APP_UPDATE_SIGNING_FINGERPRINT,
    APP_UPDATE_SIGNING_KEY_FILENAME,
)

_CHECKSUM_LINE_RE = re.compile(r"^(?P<digest>[0-9a-fA-F]{64})\s+\*?(?P<name>.+?)\s*$")


class ReleaseVerificationError(RuntimeError):
    """Raised when a release cannot be proven authentic."""


def signing_key_path() -> Path:
    """Bundled public signing certificate (``resources/release_signing_key.asc``)."""
    module_root = Path(__file__).resolve().parent.parent
    candidates = [module_root / "resources" / APP_UPDATE_SIGNING_KEY_FILENAME]
    meipass = getattr(sys, "_MEIPASS", None)
    if meipass:
        candidates.append(Path(meipass) / "ai_qa_test_studio" / "resources" / APP_UPDATE_SIGNING_KEY_FILENAME)
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return candidates[0]


def load_pinned_cert(
    *,
    key_path: Path | None = None,
    expected_fingerprint: str | None = None,
) -> Cert:
    """Load the bundled certificate and assert its fingerprint matches the pin."""
    path = key_path or signing_key_path()
    try:
        raw = path.read_bytes()
    except OSError as exc:
        raise ReleaseVerificationError(f"missing release signing key: {path}") from exc
    try:
        cert = Cert.from_bytes(raw)
    except Exception as exc:  # noqa: BLE001 — pysequoia raises RuntimeError subclasses
        raise ReleaseVerificationError(f"invalid release signing key: {path}") from exc
    pin = APP_UPDATE_SIGNING_FINGERPRINT if expected_fingerprint is None else expected_fingerprint
    pinned = pin.strip().lower()
    if not pinned:
        raise ReleaseVerificationError("no pinned release fingerprint configured")
    actual = cert.fingerprint.strip().lower()
    if actual != pinned:
        raise ReleaseVerificationError(f"signing key fingerprint mismatch: expected {pinned}, got {actual}")
    if cert.is_revoked:
        raise ReleaseVerificationError("release signing key is revoked")
    return cert


def verify_detached_signature(*, data: bytes, signature: bytes, cert: Cert) -> None:
    """Verify ``signature`` over ``data``; raise unless ``cert`` (or its subkey) signed."""
    try:
        sig = Sig.from_bytes(signature)
    except Exception as exc:  # noqa: BLE001
        raise ReleaseVerificationError("malformed detached signature") from exc
    try:
        pysequoia.verify(bytes=data, store=lambda _ids: [cert], signature=sig)
    except Exception as exc:  # noqa: BLE001 — verification failures surface as RuntimeError
        raise ReleaseVerificationError("signature does not match pinned release key") from exc


def parse_checksums(text: str) -> dict[str, str]:
    """Parse ``sha256sum`` output into ``{filename: lowercase hex digest}``."""
    entries: dict[str, str] = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        match = _CHECKSUM_LINE_RE.match(stripped)
        if match is None:
            raise ReleaseVerificationError(f"malformed checksum line: {stripped[:120]}")
        entries[match.group("name")] = match.group("digest").lower()
    if not entries:
        raise ReleaseVerificationError("empty checksum manifest")
    return entries


def verify_artifact_digest(manifest: dict[str, str], artifact_name: str, digest: str) -> None:
    """Assert ``artifact_name`` digest matches the signed manifest entry."""
    expected = manifest.get(artifact_name)
    if expected is None:
        raise ReleaseVerificationError(f"artifact {artifact_name} missing from signed manifest")
    if expected != digest.strip().lower():
        raise ReleaseVerificationError(f"checksum mismatch for {artifact_name}")


def sha256_file(path: Path, *, chunk_bytes: int = APP_UPDATE_DOWNLOAD_CHUNK_BYTES) -> str:
    """Stream ``path`` through SHA-256 without loading it into memory."""
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(chunk_bytes), b""):
            hasher.update(chunk)
    return hasher.hexdigest()
