"""Leaf enums for ISO step status and hybrid intent routing (no reverse imports)."""

from __future__ import annotations

from enum import StrEnum


class StepStatus(StrEnum):
    QUEUED = "Queued"
    RUNNING = "Running"
    PASSED = "Passed"
    FAILED = "Failed"
    HEALED = "Healed"
    BLOCKED = "Blocked"
    SKIPPED = "Skipped"


class IntentType(StrEnum):
    CHAT_FAST = "chat_fast"
    RESEARCH = "research"
    TEST_SCENARIO = "test_scenario"
    TEST_CASE_GEN = "test_case_gen"
    TEST_SCRIPT_GEN = "test_script_gen"
    EXEC_AND_BIND = "exec_and_bind"
    SWARM_AUDIT = "swarm_audit"


class DeliveryMode(StrEnum):
    DRAFT = "draft"
    APPLY = "apply"


class ThinkingLevel(StrEnum):
    OFF = "off"
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
