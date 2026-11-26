#!/usr/bin/env python3
"""Sign a release ``SHA256SUMS`` manifest with the pinned OpenPGP release key.

Usage:
    RELEASE_SIGNING_KEY=<armored secret key or path> \
        python scripts/release_sign.py <dist_dir>

Writes ``<dist_dir>/SHA256SUMS.asc`` and refuses to continue when the secret
key does not match the public certificate bundled with the app, because that
would ship updates no client can verify.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import pysequoia
from pysequoia import Cert, Tsk

MANIFEST_NAME = "SHA256SUMS"
SIGNATURE_NAME = "SHA256SUMS.asc"
SIGNING_KEY_FILENAME = "release_signing_key.asc"
REPO_ROOT = Path(__file__).resolve().parent.parent
PUBLIC_KEY_PATH = REPO_ROOT / "src" / "ai_qa_test_studio" / "resources" / SIGNING_KEY_FILENAME


def _load_secret(env_value: str) -> Tsk:
    candidate = Path(env_value)
    if "\n" not in env_value and candidate.is_file():
        return Tsk.from_bytes(candidate.read_bytes())
    return Tsk.from_bytes(env_value.encode("utf-8"))


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    dist = Path(argv[0])
    manifest = dist / MANIFEST_NAME
    if not manifest.is_file():
        print(f"missing manifest: {manifest}", file=sys.stderr)
        return 1
    secret_value = os.environ.get("RELEASE_SIGNING_KEY", "").strip()
    if not secret_value:
        print("RELEASE_SIGNING_KEY is empty — refusing to publish unsigned", file=sys.stderr)
        return 1

    tsk = _load_secret(secret_value)
    expected = Cert.from_bytes(PUBLIC_KEY_PATH.read_bytes()).fingerprint.lower()
    actual = tsk.extract_certificate().fingerprint.lower()
    if actual != expected:
        print(f"secret key {actual} does not match pinned {expected}", file=sys.stderr)
        return 1

    signature = pysequoia.sign(tsk.signer(), manifest.read_bytes(), mode=pysequoia.SignatureMode.DETACHED, armor=True)
    (dist / SIGNATURE_NAME).write_bytes(signature)
    print(f"signed {manifest} with {actual}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
