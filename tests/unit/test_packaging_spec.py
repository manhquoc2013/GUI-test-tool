"""Guard the frozen-build manifest: dynamic plugin packages must be bundled."""

from __future__ import annotations

from pathlib import Path

SPEC_PATH = Path(__file__).resolve().parents[2] / "packaging" / "AIQATestStudio.spec"

# tiktoken >=0.4 resolves encodings by scanning the tiktoken_ext namespace at
# runtime, so PyInstaller must ship it as a hidden import or every
# get_encoding()/encoding_for_model() call fails with "Unknown encoding".
REQUIRED_HIDDEN_IMPORTS = ("tiktoken_ext", "tiktoken_ext.openai_public")


def test_spec_bundles_tiktoken_plugins() -> None:
    spec = SPEC_PATH.read_text(encoding="utf-8")
    for module in REQUIRED_HIDDEN_IMPORTS:
        assert f'"{module}"' in spec, f"{module} missing from {SPEC_PATH.name}"
