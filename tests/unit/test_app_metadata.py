"""Packaging metadata CLI / SSOT."""

from __future__ import annotations

import json

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


def test_version_flag(capsys: pytest.CaptureFixture[str]) -> None:
    code = main(["--version"])
    assert code == 0
    assert capsys.readouterr().out.strip()
