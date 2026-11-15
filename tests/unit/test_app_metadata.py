"""Packaging metadata CLI / SSOT."""

from __future__ import annotations

import io
import json
import sys

import pytest

from ai_qa_test_studio.__main__ import main
from ai_qa_test_studio.app_metadata import metadata_dict


def test_metadata_dict_keys() -> None:
    meta = metadata_dict()
    for key in (
        "version",
        "display_name",
        "exe_name",
        "publisher",
        "publisher_email",
        "publisher_url",
        "identifier",
        "copyright",
    ):
        assert key in meta
        assert meta[key]


def test_print_metadata_json(capsys: pytest.CaptureFixture[str]) -> None:
    code = main(["--print-metadata-json"])
    assert code == 0
    payload = json.loads(capsys.readouterr().out)
    assert payload["exe_name"] == "AIQATestStudio"
    assert "Mạnh" in payload["publisher"] or "Qu" in payload["publisher"]


def test_print_metadata_json_survives_cp1252_stdout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """GitHub windows-latest defaults to a legacy code page; CLI must not crash."""
    raw = io.BytesIO()
    wrapper = io.TextIOWrapper(raw, encoding="cp1252", errors="strict", newline="\n")
    monkeypatch.setattr(sys, "stdout", wrapper)
    code = main(["--print-metadata-json"])
    assert code == 0
    wrapper.flush()
    text = raw.getvalue().decode("utf-8")
    payload = json.loads(text.strip())
    assert payload["exe_name"] == "AIQATestStudio"


def test_version_flag(capsys: pytest.CaptureFixture[str]) -> None:
    code = main(["--version"])
    assert code == 0
    assert capsys.readouterr().out.strip()
