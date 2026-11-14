# Capability Map: Wizard multi-doc inputs

| Module id | Responsibility | Depends on |
|---|---|---|
| doc-ingest | MarkItDown load + md/xlsx/csv structured extract | — |
| doc-classify | Auto-detect SRS / test_scenario / test_case | doc-ingest |
| doc-import | Map classified docs → TestScenario/TestCase UNBOUND | doc-classify |
| wizard-ui | Multi-file attach list + merge import with LLM context | doc-import |

Build order: doc-ingest → doc-classify → doc-import → wizard-ui
