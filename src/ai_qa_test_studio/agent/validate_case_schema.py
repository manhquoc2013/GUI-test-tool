"""Sprint 3 APPLY gate — minimal Pydantic validation before project_tree_ops."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, ValidationError, field_validator

from ai_qa_test_studio.core.models import TestCase, TestStep


class ApplyCasePayload(BaseModel):
    """Minimal schema for APPLY: id/code/title + at least one enabled step."""

    id: str = Field(min_length=1)
    code: str = Field(min_length=1)
    title: str = Field(min_length=1)
    steps: list[TestStep] = Field(default_factory=list)

    @field_validator("steps")
    @classmethod
    def _require_steps(cls, value: list[TestStep]) -> list[TestStep]:
        if not value:
            raise ValueError("APPLY requires at least one step")
        if not any(s.enabled for s in value):
            raise ValueError("APPLY requires at least one enabled step")
        return value


def validate_case_schema(payload: TestCase | dict[str, Any]) -> tuple[bool, str, TestCase | None]:
    """Return (ok, message, case_or_none)."""
    try:
        if isinstance(payload, TestCase):
            data = payload.model_dump()
        elif isinstance(payload, dict):
            data = payload
        else:
            return False, "payload must be TestCase or dict", None
        checked = ApplyCasePayload.model_validate(data)
        case = TestCase.model_validate(data)
        # Ensure ApplyCasePayload constraints already enforced
        _ = checked
        return True, "ok", case
    except ValidationError as exc:
        return False, str(exc), None
    except Exception as exc:  # noqa: BLE001
        return False, str(exc), None
