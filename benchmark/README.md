# Axiom Benchmark

`axiom benchmark --rules <rules.yaml> <benchmark-dir>` runs every fixture case in a directory
through the real Investigation pipeline (`InvestigationRunner` -> `FileEvidenceCollector` ->
`CorrelationEngine`, the same composition `axiom investigate` uses) and compares the actual
`RootCauseAssessment` against a hand-written expectation, to catch regressions in the
deterministic engine's rules and weights before they're trusted.

## What "100%" means here — and what it doesn't

**This directory's cases are regression fixtures, not an independent accuracy evaluation.** Every
expected outcome here was derived by reading the actual rule weight constants
(`ApplicationBugCorrelationRule`, `InfrastructureFailureRule`, `TransientFailureRule`, and the
signal extractors) and confirming the engine's real output, then encoding that as the expectation.
That makes a passing run a meaningful signal that **existing behavior hasn't unexpectedly
changed** — it does not independently prove Axiom is correct on real-world failures, since the
same implementation being checked also informed what "correct" means for these cases.

An independently labeled dataset (real failures, a human-decided root cause, no peeking at the
engine's own output while labeling) is a distinct, not-yet-built need — see
`docs/07-roadmap.md`'s benchmark-related backlog. Don't read a `100%` here as "Axiom is 100%
accurate."

## Directory shape

```
benchmark/
  <category>/
    <case-id>/
      report.xml               (mandatory)
      changes.json             (optional)
      execution.json           (optional)
      history.json             (optional)
      expected-assessment.json (mandatory)
```

`changes.json`/`execution.json`/`history.json` are exactly as optional as they are for
`FileEvidenceCollector` itself — a missing one just means that evidence source contributes
nothing to the case.

`<category>` is a free-form label (a subdirectory name), used only for display/grouping in the
report — it is not validated against `FailureCategory` and can be anything descriptive
(`application`, `infrastructure`, `flaky`, `unknown`, etc.).

## `expected-assessment.json`

```json
{
  "disposition": "DETERMINED",
  "category": "APPLICATION_BUG"
}
```

or, for an abstention:

```json
{
  "disposition": "NEEDS_INVESTIGATION"
}
```

`disposition` is one of `DETERMINED`/`NEEDS_INVESTIGATION` (`AssessmentDisposition`'s exact
names). `category` is one of `FailureCategory`'s exact names, mandatory when `disposition` is
`DETERMINED` and forbidden when it's `NEEDS_INVESTIGATION` — the same invariant
`RootCauseAssessment` itself enforces, so a self-contradictory fixture fails to load rather than
silently comparing wrong.

The comparison checks disposition and selected category only — not confidence, not which rule
won, not the rendered text. A future version may compare more (minimum confidence, required
supporting reasons, expected missing-evidence types) once a concrete need for that precision
shows up; not built speculatively now.

## Incomplete case directories fail validation by default

A case directory missing `report.xml` or `expected-assessment.json` is **not silently skipped** —
by default, `axiom benchmark` reports it and returns exit `1`, because a benchmark whose job is
catching regressions must never quietly get easier (e.g. an accidentally deleted
`expected-assessment.json` raising reported accuracy without anyone noticing). Valid cases still
run and are reported; the incomplete one is listed separately with its reason, and the whole run
is treated as a dataset validation failure regardless of whether the cases that did run passed.

Pass `--skip-incomplete` to opt into the lenient, development-only behavior instead: incomplete
directories are still counted and reported ("Cases skipped: N"), but don't affect the exit code —
useful while a fixture is mid-edit, not for a CI quality gate.

## Exit codes

- **`0`** — every executed case passed, and (without `--skip-incomplete`) no incomplete case
  directories were found.
- **`1`** — at least one executed case failed, or (without `--skip-incomplete`) at least one
  incomplete case directory was found, or an unexpected execution error occurred.
- **`2`** — bad usage (missing `--rules`, missing/nonexistent `<benchmark-dir>`, or zero valid
  cases discovered at all).

## Reproducibility

Benchmark runs use a fixed `Clock` and deterministic investigation ids
(`benchmark-<category>-<case-id>`), not `Clock.systemUTC()`/a random UUID — output is reproducible
run to run, which matters as soon as anything (a future JSON snapshot, an evidence id) depends on
either.
