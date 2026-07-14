# ADR-0007: Linear Rule Scan in RuleEngine

## Status
Accepted

## Context
`DefaultRuleEngine.evaluate(FailureEvent)` iterates every `PreparedRule` linearly, and within
each rule, iterates every condition in its `any`/`all` group linearly — O(rules × conditions per
rule) per event evaluated. For a CI failure classifier, the realistic rule-set size is small
(tens to a few hundred hand-authored rules, not millions), and evaluation happens once per
failure event as pipelines fail — not in a hot loop processing large event volumes per second.
Introducing rule indexing, a trie/prefix structure, or a Bloom-filter-style fast-negative-check
now would add real implementation and testing surface for a performance problem that does not
exist at any realistic MVP scale.

## Decision
Keep the linear scan as the implementation for the foreseeable future. This ADR exists
specifically to record that this is a **deliberate** choice, not an oversight, so a future
contributor encountering `DefaultRuleEngine` doesn't feel pressure to "optimize" it without a
concrete, measured need to justify the added complexity.

## Consequences
- Acceptable at the MVP scale — informally, up to roughly 1,000 rules and typical CI failure
  volumes. Revisit only if real usage measurably exceeds this, not preemptively.
- If rule-set size or evaluation frequency ever grows enough to matter, options to consider at
  that time (not before): indexing rules by `RuleField` so only relevant rules are scanned per
  condition type; a trie/prefix structure for `STARTS_WITH` conditions; a Bloom filter or similar
  fast-negative-check to skip obviously-non-matching rules before full evaluation; per-event
  memoization. Regex compilation is already cached once at `RuleProcessor` time, independent of
  this decision.
- Any future optimization here must be justified by a measured bottleneck, not intuition —
  consistent with this project's broader discipline of not adding complexity for hypothetical
  future needs (see the `ConditionMatch` removal in `04-rule-engine.md` for the same principle
  applied to a different kind of premature addition).
