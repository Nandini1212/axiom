# ADR-0004: ClassificationStrategy Abstraction

## Status
Accepted

## Context
`RuleEngine` evaluating every enabled rule can produce zero, one, or several matching rules for a
given `FailureEvent`. Deciding which match (if any) becomes the final `ClassificationResult` is a
separate concern from evaluation itself, and that decision policy is expected to evolve — e.g. a
future strategy might use AI to break ties or combine multiple partial matches, without changing
how rules are evaluated.

## Decision
`RuleEngine` evaluates every rule and never picks a winner itself; it produces a `RuleMatch` per
matching rule. A separate `ClassificationStrategy` interface decides the final
`ClassificationResult`. The initial implementation, `DeterministicStrategy`, picks by priority
descending, then confidence descending, then id ascending among matches. Future strategies
(`AIEnhancedStrategy`, `HybridStrategy`) implement the same interface.

## Consequences
- Swapping or adding a classification policy requires no change to `RuleEngine` or to how rules
  are authored/prepared.
- The tie-break logic lives in exactly one place (`ClassificationStrategy`), not duplicated
  across callers.
- Note this evaluation-time tie-break (priority -> confidence -> id) is distinct from
  `RuleProcessor`'s prepare-time sort (priority -> id only, no confidence) — they solve different
  problems: one produces a deterministic *input* ordering, the other picks a *winner* among
  matches that can carry different confidences even at equal priority.
