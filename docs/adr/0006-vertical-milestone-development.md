# ADR-0006: Vertical Milestone Development

## Status
Accepted

## Context
Two independent prior documents (the original design doc's Build Order, and
`15_Development_Guide.md`'s milestone list) both sequenced `axiom-parser` before the rule engine
work. That sequence was deliberately overridden during implementation: `RuleSource` and
`RuleProcessor` were built and fully tested (84 passing tests) using hand-constructed
`RuleDefinition` fixtures, without the parser existing at all. This worked because `RuleEngine`
and the classifier vertical do not need real parsed `FailureEvent`s to be designed, implemented,
or tested — hand-built fixtures are sufficient, and the parser is not required to validate
classifier correctness.

Conversely, switching to the parser mid-way through the classifier would leave an unfinished
classifier milestone half-built while a new, unrelated module is started — the opposite of
reviewable, small increments.

## Decision
Complete one architectural slice — design, review, implementation, tests — before starting the
next module or the next major component within a module. Do not interleave two unfinished
verticals (e.g. a half-built `RuleEngine` and a half-built `axiom-parser` in flight at once).
Within a single module, closely related components (e.g. `RuleEngine` and
`DeterministicStrategy`) are still designed, implemented, and tested as **separate** milestones
in sequence, not bundled into one combined design-and-build pass, so each gets its own review
checkpoint.

## Consequences
- Build order can and does deviate from an original phase plan when a later component doesn't
  depend on an earlier one — the dependency graph, not document order, determines what's safe to
  reorder.
- Every module in `docs/02-system-architecture.md` is marked with its actual current
  implementation status, not just its planned position, so this is always checkable.
- More design-review checkpoints, not fewer: e.g. `RuleEngine` and `DeterministicStrategy` get
  two separate design proposals and two separate approvals rather than one combined one, even
  though they're adjacent in the same module.
