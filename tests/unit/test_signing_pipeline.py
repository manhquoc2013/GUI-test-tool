"""Guard the OS-level signing pipeline (Authenticode + Developer ID).

These checks are textual on purpose: the actual signing tools only exist on
Windows/macOS runners, so a missing secret or directive must fail fast in CI
rather than silently publishing unsigned artifacts.
"""

from __future__ import annotations

from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
WORKFLOW = ROOT / ".github" / "workflows" / "release-v4.yml"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_windows_signing_accepts_base64_certificate() -> None:
    common = _read(SCRIPTS / "sign_common.ps1")
    assert "FromBase64String" in common
    assert "/fd SHA256" in common
    assert "Get-AuthenticodeSignature" in common


def test_windows_signing_fails_on_invalid_signature() -> None:
    assert "Assert-AuthenticodeValid" in _read(SCRIPTS / "windows_sign.ps1")
    assert "signtool verify" not in _read(SCRIPTS / "windows_sign.ps1")


def test_inno_setup_signs_uninstaller() -> None:
    iss = _read(SCRIPTS / "installer.iss")
    assert "SignTool=aiqa" in iss
    assert "SignedUninstaller=yes" in iss
    # Guarded so local builds without a certificate still compile.
    assert "#ifdef SignedBuild" in iss


def test_inno_sign_tool_is_defined_by_the_build_script() -> None:
    build = _read(SCRIPTS / "build-installer-win.ps1")
    assert "/DSignedBuild" in build
    assert "/Saiqa=" in build
    assert "inno_sign.ps1" in build


def test_macos_dmg_is_signed_notarized_and_stapled() -> None:
    dmg = _read(SCRIPTS / "build-dmg.sh")
    assert "codesign --force --timestamp --sign" in dmg
    assert "notarytool submit" in dmg
    assert "stapler staple" in dmg
    assert "spctl --assess" in dmg
    # CI runners have no notary profile; explicit credentials must be supported.
    assert "--apple-id" in dmg


def test_macos_codesign_uses_hardened_runtime() -> None:
    codesign = _read(SCRIPTS / "macos_codesign.sh")
    assert "--options runtime" in codesign
    assert "--timestamp" in codesign
    assert "--deep" not in codesign.replace("never use --deep", "")


@pytest.fixture(scope="module")
def workflow() -> dict:
    return yaml.safe_load(_read(WORKFLOW))


def test_workflow_imports_macos_certificate_into_ephemeral_keychain(workflow: dict) -> None:
    steps = workflow["jobs"]["build"]["steps"]
    import_step = next(
        step for step in steps if step.get("name") == "Import macOS signing certificate"
    )
    script = import_step["run"]
    assert "security create-keychain" in script
    assert "security import" in script
    assert "set-key-partition-list" in script
    assert "MACOS_CERT_P12" in import_step["env"]


def test_workflow_cleans_up_keychain(workflow: dict) -> None:
    names = [step.get("name") for step in workflow["jobs"]["build"]["steps"]]
    assert "Forget macOS signing keychain" in names


def test_workflow_verifies_both_platforms(workflow: dict) -> None:
    names = [step.get("name") for step in workflow["jobs"]["build"]["steps"]]
    assert "Verify Authenticode signatures (Windows)" in names
    assert "Verify macOS signature + Gatekeeper" in names


def test_workflow_passes_notary_credentials(workflow: dict) -> None:
    steps = workflow["jobs"]["build"]["steps"]
    dmg_step = next(step for step in steps if step.get("name") == "Build macOS DMG")
    assert "MACOS_NOTARY_APPLE_ID" in dmg_step["env"]
    assert "MACOS_NOTARY_TEAM_ID" in dmg_step["env"]
    assert "MACOS_NOTARY_PASSWORD" in dmg_step["env"]


def test_secret_setup_covers_every_secret_the_workflow_reads(workflow: dict) -> None:
    setup = _read(SCRIPTS / "setup_github_secrets.sh")
    body = _read(WORKFLOW)
    for name in (
        "RELEASE_SIGNING_KEY",
        "WINDOWS_CERT_PFX",
        "WINDOWS_CERT_PASSWORD",
        "MACOS_CERT_P12",
        "MACOS_CERT_PASSWORD",
        "MACOS_CODESIGN_IDENTITY",
        "MACOS_NOTARY_APPLE_ID",
        "MACOS_NOTARY_TEAM_ID",
        "MACOS_NOTARY_PASSWORD",
    ):
        assert f"secrets.{name}" in body, f"{name} not consumed by the workflow"
        assert name in setup, f"{name} not set by setup_github_secrets.sh"


def test_secret_setup_never_puts_values_on_the_command_line() -> None:
    setup = _read(SCRIPTS / "setup_github_secrets.sh")
    assert "--body" not in setup
    # base64 payloads must be piped, not passed as arguments (argv is visible in ps).
    assert "base64 < " in setup
