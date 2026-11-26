"""Signed-release verification primitives."""

from __future__ import annotations

import hashlib
from pathlib import Path

import pysequoia
import pytest
from pysequoia import SignatureMode, Tsk

from ai_qa_test_studio.core.release_verify import (
    ReleaseVerificationError,
    load_pinned_cert,
    parse_checksums,
    sha256_file,
    signing_key_path,
    verify_artifact_digest,
    verify_detached_signature,
)

_MANIFEST_A = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
_MANIFEST_B = "ca3d163bab055381827226140568f3bef7eaac187cebd76878e0b63e9e442356"
_MANIFEST = f"{_MANIFEST_A}  a.zip\n{_MANIFEST_B} *b.zip\n"


def test_bundled_signing_key_is_loadable_and_pinned() -> None:
    cert = load_pinned_cert()
    assert signing_key_path().is_file()
    assert len(cert.fingerprint) == 40
    assert not cert.is_revoked


def test_parse_checksums_accepts_binary_markers() -> None:
    manifest = parse_checksums(_MANIFEST)
    assert manifest["a.zip"] == _MANIFEST_A
    assert manifest["b.zip"] == _MANIFEST_B


def test_parse_checksums_rejects_malformed_lines() -> None:
    with pytest.raises(ReleaseVerificationError):
        parse_checksums("not-a-checksum a.zip\n")
    with pytest.raises(ReleaseVerificationError):
        parse_checksums("\n\n")


def test_verify_artifact_digest_matches_and_rejects() -> None:
    manifest = parse_checksums(_MANIFEST)
    verify_artifact_digest(manifest, "a.zip", manifest["a.zip"].upper())
    with pytest.raises(ReleaseVerificationError):
        verify_artifact_digest(manifest, "a.zip", "0" * 64)
    with pytest.raises(ReleaseVerificationError):
        verify_artifact_digest(manifest, "missing.zip", "0" * 64)


def test_sha256_file_streams(tmp_path: Path) -> None:
    path = tmp_path / "blob.bin"
    payload = b"x" * 5000
    path.write_bytes(payload)
    assert sha256_file(path, chunk_bytes=1024) == hashlib.sha256(payload).hexdigest()


@pytest.fixture(scope="module")
def signer() -> Tsk:
    return Tsk.generate(user_id="Manifest Signer <sign@aiqa.local>")


def test_verify_detached_signature_roundtrip(signer: Tsk) -> None:
    data = b"payload"
    signature = pysequoia.sign(signer.signer(), data, mode=SignatureMode.DETACHED, armor=True)
    verify_detached_signature(data=data, signature=signature, cert=signer.extract_certificate())


def test_verify_detached_signature_rejects_other_key(signer: Tsk) -> None:
    stranger = Tsk.generate(user_id="Stranger <evil@example.com>")
    data = b"payload"
    signature = pysequoia.sign(stranger.signer(), data, mode=SignatureMode.DETACHED, armor=True)
    with pytest.raises(ReleaseVerificationError):
        verify_detached_signature(data=data, signature=signature, cert=signer.extract_certificate())


def test_verify_detached_signature_rejects_tampered_bytes(signer: Tsk) -> None:
    signature = pysequoia.sign(signer.signer(), b"payload", mode=SignatureMode.DETACHED, armor=True)
    with pytest.raises(ReleaseVerificationError):
        verify_detached_signature(data=b"payload-edited", signature=signature, cert=signer.extract_certificate())


def test_verify_detached_signature_rejects_garbage(signer: Tsk) -> None:
    with pytest.raises(ReleaseVerificationError):
        verify_detached_signature(data=b"payload", signature=b"not a signature", cert=signer.extract_certificate())
