# System Architecture

## High-Level Flow
```
GitHub Actions
    |
JUnit/TestNG XML
    |
Parser
    |
FailureEvent
    |
Rule Source (YAML)
    |
Rule Processor
    |
Prepared Rules
    |
Rule Engine
    |
Classification Strategy
    |
Classification Result
    |
AI Analyzer
    |
Reporting (GitHub / HTML / JSON)
```

## Module Reference

Each module follows the same template: Purpose, Responsibilities, Non-responsibilities, Inputs,
Outputs, Dependencies, Interfaces, Extension points, Current implementation status. Modules with
their own deep-dive doc (`axiom-classifier` → `04-rule-engine.md`, `axiom-analyzer` →
`05-ai-analyzer.md`, `axiom-github` → `06-github-integration.md`) keep internal design detail
there; this section is the canonical short reference for every module.

### axiom-common
- **Purpose** — the normalized domain model every other module depends on.
- **Responsibilities** — `FailureEvent`, `PipelineContext`, `FailureStatus`, `SourceFormat`, and
  their construction-time validation invariants.
- **Non-responsibilities** — no parsing logic, no Spring, no GitHub/AI/reporting concerns. No
  module-specific behavior of any kind — this is pure data.
- **Inputs** — none (constructed by callers, primarily `axiom-parser` once it exists).
- **Outputs** — `FailureEvent` instances consumed by every downstream module.
- **Dependencies** — none within axiom. External: Jackson databind + jsr310 (for JSON
  serialization of the domain model, e.g. in reports/PR comments).
- **Interfaces** — none; concrete records only. There is nothing to swap here — the whole point
  of this module is one shared, non-negotiable shape.
- **Extension points** — none intended. A new field here is a breaking change for every module.
- **Status** — built. See `03-domain-model.md`.

### axiom-parser
- **Purpose** — convert raw test-report formats into `FailureEvent`.
- **Responsibilities** — parsing JUnit XML and TestNG XML.
- **Non-responsibilities** — no classification, no normalization decisions beyond what
  `FailureEvent`'s own constructor already enforces. Does not know about rules, AI, or reporting.
- **Inputs** — JUnit XML files, TestNG XML files.
- **Outputs** — `FailureEvent` instances (axiom-common).
- **Dependencies** — axiom-common.
- **Interfaces** — `Parser`.
- **Extension points** — a new source format (a future CI system's native report) is a new
  `Parser` implementation; no other module changes (see ADR-0003).
- **Status** — not yet built. Deliberately deferred until the deterministic classifier vertical
  (`axiom-classifier`) is complete — see ADR-0006.

### axiom-classifier
- **Purpose** — deterministic root-cause classification of a `FailureEvent`.
- **Responsibilities** — loading rule definitions (`RuleSource`), turning them into runtime-ready
  rules (`RuleProcessor`), evaluating rules against a `FailureEvent` (`RuleEngine`), and deciding
  a final result among matches (`ClassificationStrategy`).
- **Non-responsibilities** — no AI, no parsing, no reporting/presentation. Never guesses when
  rule evidence exists (ADR-0001).
- **Inputs** — rule YAML files, a `FailureEvent` to classify.
- **Outputs** — `ClassificationResult` (category, confidence, matched rule id, evidence).
- **Dependencies** — axiom-common (`FailureEvent`).
- **Interfaces** — `RuleSource`, `RuleProcessor`, `RuleEngine`, `ClassificationStrategy`.
- **Extension points** — new rule sources (e.g. a database-backed `RuleSource`), new
  classification policies (`ClassificationStrategy` implementations) are additive.
- **Status** — `RuleSource`, `RuleProcessor`/`PreparedRule`, `RuleEngine`, and
  `ClassificationStrategy`/`DeterministicStrategy` all built and tested (134 tests passing
  project-wide). The deterministic classification vertical is now complete end to end
  (`FailureEvent` -> `ClassificationResult`, given hand-built fixtures) — `axiom-parser` is next.
  See `04-rule-engine.md` and `07-roadmap.md`.

### axiom-analyzer
- **Purpose** — explain a classification result in natural language; never classify from scratch.
- **Responsibilities** — building a grounded prompt from `FailureEvent` + `ClassificationResult`,
  invoking an LLM provider, returning a structured explanation.
- **Non-responsibilities** — no classification authority — a `ClassificationResult` already
  exists before this module runs (ADR-0001).
- **Inputs** — `FailureEvent`, `ClassificationResult`, historical context (future).
- **Outputs** — root cause explanation, suggested next steps, confidence explanation.
- **Dependencies** — axiom-common, axiom-classifier.
- **Interfaces** — `LLMProvider`.
- **Extension points** — a new LLM vendor is a new `LLMProvider` implementation.
- **Status** — not yet built. See `05-ai-analyzer.md`.

### axiom-reporting
- **Purpose** — render a classification (and, later, an AI explanation) as a presentable report.
- **Responsibilities** — Markdown, HTML, JSON, console output formats.
- **Non-responsibilities** — no classification or explanation logic — pure presentation.
- **Inputs** — `ClassificationResult`, AI explanation output.
- **Outputs** — rendered reports in the requested format.
- **Dependencies** — axiom-common, axiom-classifier, axiom-analyzer.
- **Interfaces** — `Reporter`.
- **Extension points** — a new output format (e.g. Slack, Teams, a dashboard) is a new `Reporter`
  implementation.
- **Status** — not yet built.

### axiom-github
- **Purpose** — surface Axiom's output directly inside the GitHub workflow where the failure
  occurred.
- **Responsibilities** — posting PR comments, workflow run summaries.
- **Non-responsibilities** — no classification, no report rendering (delegates to
  axiom-reporting) — this module is about *where* output goes, not how it's formatted.
- **Inputs** — a rendered report (from axiom-reporting), GitHub Actions run context (repo, PR
  number, commit SHA).
- **Outputs** — a posted PR comment / workflow summary.
- **Dependencies** — axiom-reporting.
- **Interfaces** — none finalized yet.
- **Extension points** — Checks API integration, inline review annotations, historical trend
  reporting are additive future capabilities, not replacements.
- **Status** — not yet built. See `06-github-integration.md`.

### axiom-cli
- **Purpose** — a local entry point for running Axiom outside of GitHub Actions (e.g. against a
  local test run).
- **Responsibilities** — wiring the other modules together behind a command-line interface.
- **Non-responsibilities** — no business logic of its own — pure composition/wiring.
- **Inputs** — command-line arguments, local file paths (rule files, test reports).
- **Outputs** — console/file output via axiom-reporting.
- **Dependencies** — all other modules.
- **Interfaces** — none — this is the composition root, not something else depends on it.
- **Extension points** — n/a.
- **Status** — not yet built.

## Core Interfaces
- Parser
- RuleSource
- RuleProcessor
- RuleEngine
- ClassificationStrategy
- LLMProvider
- Reporter

Every implementation hides behind one of these.

## Design Principles
- SOLID
- Clean Architecture
- Immutable domain objects (Java records)
- Interface-first design
- Composition over inheritance
- Configuration over code
- Constructor injection, no static state, no Spring in the domain layer

## Configuration
Sources: YAML (rule files, primary config), environment variables (credentials, provider
selection). Configurable: rule files, LLM provider, report format, logging.

## Security
- No secrets in code
- Environment-based credentials
- Provider abstraction (no hardcoded LLM vendor lock-in)
- Audit logging
- Least privilege

## Scalability (future)
- Historical datastore
- Failure clustering
- Flaky detection
- Multi-repository analysis
- AI memory

## Build Order
See `07-roadmap.md`.
