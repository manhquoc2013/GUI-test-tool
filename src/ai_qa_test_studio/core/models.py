"""ISO/IEC/IEEE 29119-aligned Pydantic models + Shared Element Repository."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

Priority = Literal["P0 - Critical", "P1 - High", "P2 - Medium", "P3 - Low"]
TestType = Literal["Functional", "Negative", "Boundary", "UI/UX", "Security"]
BindingStatus = Literal["UNBOUND", "BOUND", "HEALED"]
CaseStatus = Literal["NOT_RUN", "PASS", "FAIL", "HEALED", "SKIPPED", "CANCELLED"]
StepAction = Literal[
    "goto",
    "click",
    "fill",
    "select",
    "press_enter",
    "hover",
    "assert_visible",
    "assert_text",
    "assert_url",
    "verify_db",
]


class SharedElement(BaseModel):
    element_id: str = Field(..., description="Unique id, e.g. EL_LOGIN_SUBMIT")
    page_name: str = Field(..., description="Screen name, e.g. LoginPage")
    friendly_name: str = Field(..., description="QA-facing label")
    primary_playwright: str | None = Field(
        None, description="User-facing locator, e.g. get_by_role(...)"
    )
    test_id: str | None = None
    role: str | None = None
    name: str | None = None
    placeholder: str | None = None
    css: str | None = None
    xpath: str | None = None
    vision_prompt: str | None = None
    thumbnail_b64: str | None = None


class TestStep(BaseModel):
    step_no: int
    action: StepAction
    element_ref: str | None = None
    intent_hint: str = ""
    input_data: str | None = None
    expected_result: str = ""
    binding_status: BindingStatus = "UNBOUND"
    enabled: bool = True
    legacy_script_ref: str | None = None


class TestCase(BaseModel):
    id: str
    code: str
    title: str
    priority: Priority = "P1 - High"
    test_type: TestType = "Functional"
    preconditions: list[str] = Field(default_factory=list)
    test_data_rows: list[dict[str, Any]] = Field(default_factory=list)
    expected_final_result: str = ""
    steps: list[TestStep] = Field(default_factory=list)
    last_status: CaseStatus = "NOT_RUN"
    requirement_ref: str = ""
    start_url: str = ""
    script_artifact: str | None = None
    test_data_profile_id: str | None = None
    description: str = ""


class TestScenario(BaseModel):
    id: str
    code: str
    title: str
    requirement_ref: str = ""
    description: str = ""
    cases: list[TestCase] = Field(default_factory=list)


class TestSuite(BaseModel):
    id: str
    name: str
    description: str = ""
    browser: str = ""
    headless: bool | None = None
    cases: list[TestCase] = Field(default_factory=list)
    scenario_ids: list[str] = Field(default_factory=list)


class TestGroup(BaseModel):
    id: str
    name: str
    description: str = ""
    scenarios: list[TestScenario] = Field(default_factory=list)
    suites: list[TestSuite] = Field(default_factory=list)
    children: list[TestGroup] = Field(default_factory=list)


class EnvironmentProfile(BaseModel):
    name: str
    base_url: str = ""
    variables: dict[str, str] = Field(default_factory=dict)


class TestDataProfile(BaseModel):
    id: str
    name: str
    rows: list[dict[str, Any]] = Field(default_factory=list)


class AiModelEntry(BaseModel):
    id: str
    label: str = ""
    tier: str = "mid"
    is_default: bool = False


class AiConnection(BaseModel):
    id: str
    label: str = ""
    provider: str = "offline"
    base_url: str = ""
    api_key_ref: str = ""
    enabled: bool = True
    models: list[AiModelEntry] = Field(default_factory=list)


class AiRoutingSettings(BaseModel):
    mode: str = "auto"
    manual_model_ref: str = ""
    complexity_enabled: bool = True
    task_complexity_models: dict[str, dict[str, str]] = Field(default_factory=dict)


class CopilotRuntimeSettings(BaseModel):
    summary_max_chars: int = 160
    debate_max_rounds: int = 3
    loop_repeat_limit: int = 2
    clarify_timeout_ms: int = 60_000
    lean_tail_max_tokens: int = 2400
    analyzer_enabled: bool = False
    optimizer_enabled: bool = False


class McpExternalSettings(BaseModel):
    playwright: bool = True
    midscene: bool = True
    figma: bool = False
    db: bool = False


class McpSettings(BaseModel):
    external: McpExternalSettings = Field(default_factory=McpExternalSettings)


class AiSettings(BaseModel):
    """Legacy flat fields kept for gateway helpers; prefer `connections`."""

    api_key_ref: str = ""
    base_url: str = ""
    model: str = ""
    provider: str = ""
    temperature: float = 0.2
    max_tokens: int = 512
    connections: list[AiConnection] = Field(default_factory=list)
    default_connection_id: str = ""
    routing: AiRoutingSettings = Field(default_factory=AiRoutingSettings)


class AppSettings(BaseModel):
    browser: str = "CHROMIUM"
    headless_default: bool = False
    theme: str = "auto"
    ai: AiSettings = Field(default_factory=AiSettings)
    active_environment: str = "DEV"
    copilot: CopilotRuntimeSettings = Field(default_factory=CopilotRuntimeSettings)
    mcp: McpSettings = Field(default_factory=McpSettings)


class Project(BaseModel):
    id: str
    name: str
    description: str = ""
    environments: list[EnvironmentProfile] = Field(
        default_factory=lambda: [
            EnvironmentProfile(name="DEV"),
            EnvironmentProfile(name="STAGING"),
            EnvironmentProfile(name="PROD"),
        ]
    )
    shared_elements: list[SharedElement] = Field(default_factory=list)
    test_data_profiles: list[TestDataProfile] = Field(default_factory=list)
    root_group: TestGroup
    settings: AppSettings = Field(default_factory=AppSettings)
    import_warnings: list[str] = Field(default_factory=list)


class StepAuditEntry(BaseModel):
    step_no: int
    old_locator: str | None = None
    new_locator: str | None = None
    reason: str = ""
    binding_status: BindingStatus = "HEALED"


class RunStepResult(BaseModel):
    step_no: int
    action: StepAction
    status: CaseStatus
    duration_ms: int = 0
    message: str = ""
    screenshot_b64: str | None = None
    click_x: float | None = None
    click_y: float | None = None
    audit: StepAuditEntry | None = None


class CaseRunResult(BaseModel):
    case_id: str
    case_code: str
    title: str
    status: CaseStatus
    steps: list[RunStepResult] = Field(default_factory=list)
    skip_reason: str | None = None


class RunReport(BaseModel):
    run_id: str
    project_name: str
    started_at: str
    finished_at: str
    cases: list[CaseRunResult] = Field(default_factory=list)
    healing_audit: list[StepAuditEntry] = Field(default_factory=list)
