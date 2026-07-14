# Axiom

Axiom is an Engineering Intelligence Platform that classifies CI/CD test failures
deterministically — via rules, not guesses — and is designed so AI can later *explain*
those results without ever being the thing deciding what a failure means.

```text
JUnit XML
    ↓
Parser
    ↓
FailureEvent
    ↓
Rule Engine
    ↓
Classification
    ↓
Analyzer
    ↓
CLI
```

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
tool itself couldn't run (bad file, malformed YAML/XML); `2` means invalid usage. CI-gating on a
category (e.g. "fail the build if an `APPLICATION_BUG` shows up") is intentionally not built yet —
see `docs/12-cli.md`.

**Note**: this CLI output is formatted for humans reading a terminal, not for machine
consumption — there's no `--json` (or similar structured-output) flag yet.

## What's built

- `axiom-common` — the normalized `FailureEvent` domain model
- `axiom-classifier` — YAML rule loading/preparation, the deterministic rule engine, and
  `DeterministicStrategy`
- `axiom-parser` — JUnit XML → `FailureEvent` (TestNG and other formats are future work)
- `axiom-analyzer` — orchestrates parser + classifier into one `AnalysisResult` call (no AI yet)
- `axiom-cli` — the `axiom <rules.yaml> <report.xml>` command shown above

Not yet built: AI-enhanced explanations, GitHub PR integration, Markdown/HTML/JSON reporting.
See `docs/07-roadmap.md` for what's next and why.

## Running tests

```bash
./gradlew test
```

## Architecture docs

- `docs/01-product-vision.md` — mission, principles, success metrics
- `docs/02-system-architecture.md` — module reference, API conventions
- `docs/03-domain-model.md`, `docs/04-rule-engine.md`, `docs/10-parser.md`, `docs/11-analyzer.md`,
  `docs/12-cli.md` — per-module deep dives
- `docs/09-end-to-end-walkthrough.md` — one failure traced through the whole pipeline
- `docs/adr/` — architecture decision records
- `docs/07-roadmap.md` — build order, backlog, and future phases
