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
- **Responsibilities** — parsing JUnit XML (TestNG XML is a future, separate `Parser`
  implementation, not yet built).
- **Non-responsibilities** — no classification, no normalization decisions beyond what
  `FailureEvent`'s own constructor already enforces. Does not know about rules, AI, or reporting.
  Does not enrich `pipelineContext` (no CI/repo/branch info exists in a test report XML itself).
- **Inputs** — a JUnit XML document (`InputStream`).
- **Outputs** — `ParserResult` (successfully parsed `FailureEvent`s, plus recoverable
  `ParserWarning`s for records it couldn't fully understand).
- **Dependencies** — axiom-common.
- **Interfaces** — `Parser`.
- **Extension points** — a new source format (TestNG, pytest, a future CI system's native report)
  is a new `Parser` implementation; no other module changes (see ADR-0003).
- **Status** — built (JUnit XML only). See `10-parser.md`.

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
- **Purpose** — orchestrate `Parser` and the classifier (`RuleEngine` + `ClassificationStrategy`)
  into one call: report bytes in, a complete `AnalysisResult` out. Fully deterministic; AI is a
  future enhancement to this same interface, not something this module depends on.
- **Responsibilities** — calling `Parser.parse()`, then `RuleEngine.evaluate()` +
  `ClassificationStrategy.classify()` per failure, and assembling the result. (Future: building a
  grounded prompt from an `AnalyzedFailure`, invoking an LLM provider, adding explanation — see
  `05-ai-analyzer.md`.)
- **Non-responsibilities** — no classification authority of its own — classification already
  happened by the time this module assembles a result (ADR-0001). Owns no rule loading or parser
  construction; both are handed in already built.
- **Inputs** — a report document (`InputStream`).
- **Outputs** — `AnalysisResult` (every `AnalyzedFailure`, plus parser warnings carried through).
- **Dependencies** — axiom-common, axiom-classifier, axiom-parser.
- **Interfaces** — `Analyzer` (built). `LLMProvider` (future, see `05-ai-analyzer.md`).
- **Extension points** — a future `AIEnhancedAnalyzer` implements the same `Analyzer` interface;
  a new LLM vendor would be a new `LLMProvider` implementation.
- **Status** — built (orchestration only; `DeterministicAnalyzer`). See `11-analyzer.md`.

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
- **Purpose** — a local entry point for running Axiom outside of GitHub Actions: `axiom [--ai]
  <rules.yaml> <report.xml>`.
- **Responsibilities** — constructing the concrete pipeline (`YamlRuleSource` ->
  `DefaultRuleProcessor` -> `DefaultRuleEngine`, `DeterministicStrategy`, `JUnitXmlParser`,
  `DeterministicAnalyzer`, optionally wrapped in `AIEnhancedAnalyzer`/`ClaudeProvider` when `--ai`
  is passed) and printing the resulting `AnalysisResult`. Construction and execution are
  deliberately separated: `createAnalyzer(Path, boolean, Map<String,String>)` builds the
  dependency graph, `run(...)`'s execution path calls only `Analyzer` — presentation code never
  reaches into axiom-classifier/axiom-parser/axiom-analyzer's AI types directly.
- **Non-responsibilities** — no business logic of its own — pure composition/wiring. No CI-gating
  decisions (exit code is `0` whenever analysis completes, regardless of failures found — gating
  is explicitly deferred to a future flag/command, not this milestone). Console output is
  deliberately temporary/inline, not a preview of `axiom-reporting`'s eventual `Reporter` design.
- **Inputs** — command-line arguments: an optional `--ai` flag, a rules YAML path, and a report
  XML path. When `--ai` is passed: `AXIOM_LLM_PROVIDER` (optional, default `claude`),
  `AXIOM_LLM_API_KEY` (required), `AXIOM_LLM_TIMEOUT_SECONDS` (optional, default 30) from the
  environment only, never a CLI argument or committed file.
- **Outputs** — console text; exit `0` (analysis completed, any number of failures found), `1`
  (execution failure — missing file, malformed YAML/XML, missing/invalid AI config, unexpected
  runtime error), `2` (invalid usage).
- **Dependencies** — axiom-common, axiom-classifier, axiom-parser, axiom-analyzer.
- **Interfaces** — none — this is the composition root, not something else depends on it.
- **Extension points** — n/a today; a future `--fail-on <category>` flag or `axiom gate` command
  would add CI-gating semantics without changing this milestone's default behavior.
- **Status** — built, including the `--ai` flag. `./gradlew :axiom-cli:run --args="rules.yaml
  report.xml"` or an installable `bin/axiom` script via the `application` plugin. The AI path
  builds a real `ClaudeProvider`, confirmed with a live call against the real Anthropic API
  (2026-07-21) — see `05-ai-analyzer.md`.

## Core Interfaces
- Parser
- RuleSource
- RuleProcessor
- RuleEngine
- ClassificationStrategy
- Analyzer
- LLMProvider
- Reporter

## API Conventions

**`*Result` types**: every stage's result is its primary output plus whatever diagnostics or
warnings must not be silently lost — not just the bare output type. `ParserResult(failures,
warnings)` and `AnalysisResult(analyses, parserWarnings)` are the two instances that established
this; treat it as the standing convention for future modules' result types (e.g. a future
reporting/GitHub result), not something to re-decide per module.

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
