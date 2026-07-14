# ADR-0008: Replace `Analyzer.analyze(InputStream)` with `AnalysisRequest` (Candidate)

## Status
Proposed — not accepted, not scheduled. This is a documented future consideration, not a decision.

## Context
`Analyzer.analyze(InputStream)` works cleanly while every report source is effectively file-like
(a local JUnit XML file, bytes read from disk). It stops being a clean fit the moment a source
carries information beyond raw bytes — a GitHub Actions artifact download has a repo/run/PR
context attached to it; a ReportPortal-style REST API response might carry its own metadata; an
S3 object has a bucket/key identity. None of that has anywhere to go through a bare
`InputStream`, and today's design doesn't need it to, since only one ingestion path
(local JUnit XML, in tests) exists.

## Decision (proposed, not made)
When a second real ingestion source is implemented (most likely `axiom-github`'s GitHub Actions
artifact path), evaluate replacing `Analyzer.analyze(InputStream)` with
`Analyzer.analyze(AnalysisRequest)`, where `AnalysisRequest` would carry the report bytes plus
whatever source metadata that second ingestion path actually needs — not a hypothetical superset
guessed at now. This is intentionally **not decided yet** — the shape of `AnalysisRequest` should
be informed by a second real, concrete integration, the same reasoning already applied to
deferring a shared parser base class until TestNG exists (ADR-0006) and to removing `ConditionMatch`
(no speculative shape without a concrete consumer).

## Why this is recorded now, before being decided
This is flagged as a candidate ADR — not left as a quiet backlog line — specifically because it
is expected to be one of the largest public API changes in axiom: every caller of
`Analyzer.analyze()` (currently just tests; eventually `axiom-cli`, `axiom-github`) would need to
change. Recording the shape of the *question* now, even unanswered, means nobody building against
`Analyzer` today is surprised by a breaking signature change later — they can see it coming.

## Consequences (if eventually accepted)
- `Analyzer.analyze(InputStream)`'s current callers (tests, and any interim `axiom-cli`) would
  need to migrate to constructing an `AnalysisRequest`.
- `AnalysisRequest`'s exact fields remain undefined until a second ingestion source exists to
  inform them — this ADR intentionally does not propose a shape.
- Until then, `InputStream` remains the interface. Do not build `AnalysisRequest` speculatively.
