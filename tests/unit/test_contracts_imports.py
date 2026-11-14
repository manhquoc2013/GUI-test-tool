from __future__ import annotations

import ast
from pathlib import Path

from ai_qa_test_studio.core import contracts
from ai_qa_test_studio.core.enums import IntentType, StepStatus
from ai_qa_test_studio.core.models import DistilledNode, ElementFingerprint, StepResult


def test_contracts_reexports_models_and_enums() -> None:
    assert contracts.ElementFingerprint is ElementFingerprint
    assert contracts.DistilledNode is DistilledNode
    assert contracts.StepResult is StepResult
    assert contracts.IntentType is IntentType
    assert contracts.StepStatus is StepStatus


def test_models_module_does_not_import_contracts() -> None:
    root = Path(__file__).resolve().parents[2]
    models_path = root / "src" / "ai_qa_test_studio" / "core" / "models.py"
    tree = ast.parse(models_path.read_text(encoding="utf-8"))
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module:
            assert "contracts" not in node.module
        if isinstance(node, ast.Import):
            for alias in node.names:
                assert "contracts" not in alias.name
