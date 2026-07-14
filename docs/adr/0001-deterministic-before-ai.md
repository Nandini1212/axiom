# ADR-0001: Deterministic Before AI

## Status
Accepted

## Context
CI failure root causes are frequently identifiable from evidence alone (a `Connection refused`
message, a known exception type, a specific stack frame). An AI-first design would ask an LLM to
guess a category from raw logs, which is non-deterministic, harder to debug, and undermines trust
if it hallucinates a root cause that evidence would have ruled out immediately.

## Decision
Classification is performed by a deterministic rule engine (`axiom-classifier`) evaluating
evidence-based conditions against a normalized `FailureEvent`. AI (`axiom-analyzer`) is invoked
only after classification, to explain and elaborate on a result the rule engine already produced
— never to classify from scratch when deterministic evidence exists.

## Consequences
- The rule engine must handle the common/known failure signatures; AI covers explanation quality,
  not classification coverage.
- Classification is reproducible and testable without any LLM in the loop (see the 84 unit tests
  in `axiom-classifier` as of the `RuleProcessor` milestone).
- Success metric: >85% of failures classified deterministically (see `08-success-metrics.md`).
