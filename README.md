# Axiom

![Java 21](https://img.shields.io/badge/Java-21-orange)

Modern CI pipelines often fail with many test failures at once, but engineers still spend
significant time working out whether each one represents a real application bug, a flaky test,
an infrastructure problem, or an environment issue — before they can even start fixing anything.

Axiom is a deterministic failure intelligence platform for CI/CD. Deterministic logic always
decides the classification; AI, where enabled, only explains an already-decided result — it
never invents or overrides the classification itself.

```text
JUnit XML
    ↓
Parser
    ↓
FailureEvent
    ↓
Rule Engine → Classification
    ↓
Analyzer (+ optional AI explanation)
    ↓
CLI
```

A second, newer capability — **Evidence Correlation** — reasons across multiple independent
signals (not just the failure message) to produce a ranked root-cause assessment with full
evidence and confidence trails. It's a library today, not yet wired into the CLI — see
[Evidence Correlation](#evidence-correlation-library-only-not-yet-in-the-cli) below for exactly
what that means.

See `docs/` for the full architecture, design decisions (`docs/adr/`), and roadmap.

## Quick start

Requires JDK 21 (the Gradle wrapper handles the rest — no local Gradle install needed).

```bash
git clone <this-repo>
cd axiom
./gradlew build
```

Run the CLI against a rules file and a JUnit XML report:

```bash
./gradlew :axiom-cli:run --args="path/to/rules.yaml path/to/report.xml"
```

### Example: a rule file

```yaml
rules:
  - id: connection-refused
    priority: 100
    match:
      any:
        - field: message
          operator: contains
          value: "Connection refused"
    classification:
      category: INFRASTRUCTURE_FAILURE
      confidence: 0.95
```

### Example: matched failure

```text
$ ./gradlew :axiom-cli:run --args="rules.yaml report.xml"

Detected 1 failure(s)

FAILED  shouldLogin (com.example.LoginTest)
  Category:   INFRASTRUCTURE_FAILURE
  Confidence: 0.95
  Rule:       connection-refused

Warnings: none
```

### Example: with `--ai`, a Claude-generated explanation alongside the classification

```bash
export AXIOM_LLM_API_KEY="sk-ant-..."
./gradlew :axiom-cli:run --args="--ai rules.yaml report.xml"
```

```text
FAILED  shouldLogin (com.example.LoginTest)
  Category:   INFRASTRUCTURE_FAILURE
  Confidence: 0.95
  Rule:       connection-refused
  AI Summary:    The shouldLogin test failed with 'Connection refused'...
  AI Root Cause: A network connection could not be established...
  AI Suggested Next Steps:
    - Verify that the target service or dependency is running and reachable...
  AI Confidence Note: High confidence — the message is an unambiguous match...
```

AI is always an explicit opt-in (`--ai`); without it, nothing calls out to any AI provider. See
`docs/05-ai-analyzer.md` for exactly what's been verified against the real Anthropic API versus
what's still unmeasured (latency, retries, token limits).

### Example: a failure no rule recognizes

```text
Detected 1 failure(s)

FAILED  shouldCheckout (com.example.CheckoutTest)
  Category:   UNKNOWN
  Confidence: 0.0
  (no rule matched)

Warnings: none
```

### Example: a clean report (nothing failed)

```text
Detected 0 failure(s)

Warnings: none
```

Exit code is `0` whenever analysis completes — including when failures are found. `1` means the
tool itself couldn't run (bad file, malformed YAML/XML, missing/invalid AI config); `2` means
invalid usage. CI-gating on a category (e.g. "fail the build if an `APPLICATION_BUG` shows up") is
intentionally not built yet — see `docs/12-cli.md`.

**Note**: this CLI output is formatted for humans reading a terminal, not for machine
consumption — there's no `--json` (or similar structured-output) flag yet.

## Evidence Correlation (library only, not yet in the CLI)

The deterministic rule engine above classifies a failure from its message and stack trace alone.
`axiom-correlation` goes further: it correlates the failure with a source-code change (a
`changes.json` diff summary) and execution context (retry outcome, whether other tests failed
alongside it) to produce a ranked, evidence-backed root-cause assessment — or an honest
`NEEDS_INVESTIGATION` when the evidence doesn't clearly support one conclusion over another.

```text
Test failure + source change + execution evidence
    ↓
Signal extraction
    ↓
Competing correlation rules (ApplicationBugCorrelationRule, InfrastructureFailureRule, FlakyTestRule)
    ↓
AssessmentSelector (confidence threshold + minimum lead over the runner-up + blocking checks)
    ↓
RootCauseAssessment
    ↓
Text or Markdown renderer
```

Sample Markdown output — this is real, verified output from the test suite
(`MarkdownAssessmentRendererTest`), not a mockup:

```markdown
## Axiom Investigation: PaymentServiceTest.testCharge

**Verdict:** Application bug (Moderate confidence - 80%)

**Why**
- Changed production file matches stack frame
- Failure reproduced on retry
- Existing deterministic classification is already APPLICATION_BUG

**Evidence against:** none

**Files to review**
- src/main/java/com/example/PaymentService.java

**Recommended next step**
Review the recent changes in src/main/java/com/example/PaymentService.java.

**Result:** Root cause determined
```

Notice what's deliberately absent: no assigned owner, no time estimate. Axiom has no
code-ownership mapping or historical-timing evidence source today, so it doesn't invent either —
every line in that report traces back to real evidence, or it isn't printed at all.

**Current status, honestly**: this engine (`axiom-correlation`) and its renderers
(`TextAssessmentRenderer`, `MarkdownAssessmentRenderer`) are fully built and tested, but
`axiom-cli` doesn't depend on `axiom-correlation` yet — there is no `axiom investigate` command.
Today, the only way to see this output is via the test suite
(`axiom-correlation/src/test/java/.../presentation/`). CLI wiring is deliberately deferred, not
forgotten — see `docs/13-evidence-correlation-design.md` and `docs/07-roadmap.md`.

## What's built

- `axiom-common` — the normalized `FailureEvent` domain model
- `axiom-classifier` — YAML rule loading/preparation, the deterministic rule engine, and
  `DeterministicStrategy`
- `axiom-parser` — JUnit XML → `FailureEvent` (TestNG and other formats are future work)
- `axiom-analyzer` — orchestrates parser + classifier into one `AnalysisResult` call, plus an
  optional AI-generated explanation (`ClaudeProvider`) layered on top without changing the
  classification — verified against the real Anthropic API, not just mocked tests
- `axiom-correlation` — multi-signal root-cause correlation (`ApplicationBugCorrelationRule`,
  `InfrastructureFailureRule`, `FlakyTestRule`), deterministic confidence scoring, and
  text/Markdown presentation. A library today — no CLI entry point yet (see above)
- `axiom-cli` — the `axiom [--ai] <rules.yaml> <report.xml>` command shown above

See `docs/07-roadmap.md` for what's next and why (the next correlation rule targets test-automation
failures).

## Running tests

```bash
./gradlew test
```

## Architecture docs

- `docs/01-product-vision.md` — mission, principles, success metrics
- `docs/02-system-architecture.md` — module reference, API conventions
- `docs/03-domain-model.md`, `docs/04-rule-engine.md`, `docs/05-ai-analyzer.md`,
  `docs/10-parser.md`, `docs/11-analyzer.md`, `docs/12-cli.md` — per-module deep dives
- `docs/13-evidence-correlation-design.md` — the correlation engine's design and the compatibility
  decisions behind it
- `docs/14-correlation-signal-weights.md` — every correlation rule's weights, blocking
  contradictions, and eligibility gates, with the reasoning behind each one
- `docs/09-end-to-end-walkthrough.md` — one failure traced through the whole pipeline
- `docs/adr/` — architecture decision records
- `docs/07-roadmap.md` — build order, backlog, and future phases
