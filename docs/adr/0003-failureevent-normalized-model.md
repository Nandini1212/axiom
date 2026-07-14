# ADR-0003: FailureEvent as the Normalized Domain Model

## Status
Accepted

## Context
Axiom needs to support multiple test report formats (JUnit XML, TestNG XML, and potentially
others later) without every downstream module (classifier, analyzer, reporter, GitHub
integration) needing to know which format a given failure originated from.

## Decision
`axiom-parser` is the only module allowed to know about JUnit/TestNG XML structure. It parses
into a single normalized domain type, `FailureEvent` (`axiom-common`), and every other module
consumes only `FailureEvent`. `FailureEvent` retains `sourceFormat` as a field (not discarded
after parsing) since downstream rules or AI prompts may reasonably care where a failure
originated, but the *shape* of every failure is uniform regardless of source.

## Consequences
- Adding a new source format (e.g. a future CI system's native report format) only requires a
  new `Parser` implementation — no changes to `axiom-classifier`, `axiom-analyzer`,
  `axiom-reporting`, or `axiom-github`.
- `FailureEvent`'s field semantics (nullable-with-meaning vs. mandatory, blank-string rejection,
  immutable metadata) are the single contract every module relies on — see
  `03-domain-model.md` and the validation behavior already built and tested in `axiom-common`.
