# Rule Engine

Deep-dive for the `axiom-classifier` module. Short reference: `02-system-architecture.md`'s
Module Reference section (Purpose/Responsibilities/Non-responsibilities/Inputs/Outputs/
Dependencies/Interfaces/Extension points/Status).

## Lifecycle
```
YAML -> RuleSource -> RuleDefinition -> RuleProcessor -> PreparedRule -> RuleEngine
     -> ClassificationStrategy -> ClassificationResult
```

## RuleSource — built
Loads `RuleDefinition`s from YAML. `YamlRuleSource`: single file only (directory support
deliberately deferred until a real multi-file rule set justifies the added surface area —
recursion, filename ordering, cross-file concatenation order). Uses Jackson YAML
(`jackson-dataformat-yaml`, reusing the same databind stack as `axiom-common` rather than adding
a second YAML library), `FAIL_ON_UNKNOWN_PROPERTIES` enabled, case-insensitive enum matching (so
YAML can use lowercase `contains` against the `CONTAINS` enum constant). Wraps I/O and parse
failures in `RuleSourceException` with the offending file path.

## RuleProcessor — built
Turns `RuleDefinition` -> `PreparedRule` (`DefaultRuleProcessor`):
- Rejects duplicate rule ids across the **whole input batch** — checked before enabled-filtering,
  so a duplicate between an enabled and a disabled rule still fails. Whole-batch rejection, not a
  per-rule skip: a rule id is a stable downstream reference, so silently picking a "winner"
  between colliding ids would just hide an unresolved tie-break one level deeper.
- Filters out disabled rules. `enabled` defaults to `true` when unspecified — deliberately
  opt-out, not opt-in, since defaulting to `false` would let an author's rule silently do nothing
  with no error anywhere to catch it.
- Resolves `priority` (default `0`, unbounded — negative priorities are valid, e.g. for a
  low-priority fallback/catch-all rule) and `caseSensitive` (default `false`) defaults.
- Precompiles `REGEX` conditions into `java.util.regex.Pattern` (`CASE_INSENSITIVE` flag applied
  when not case-sensitive); invalid regex syntax rejects the whole batch via
  `RuleProcessingException` — a bad regex is a rule-file bug, not a runtime condition to skip.
- Returns `PreparedRule`s sorted by priority descending, then id ascending — a deterministic order
  so `RuleEngine`/`ClassificationStrategy` never have to re-sort or guess at evaluation time.

## RuleEngine — built
`RuleEngine` (`com.axiom.classifier.engine`), constructed with its `PreparedRule` list
(`DefaultRuleEngine(List<PreparedRule> rules)` — rules are configuration, loaded once; only the
`FailureEvent` varies per call, so `evaluate(FailureEvent)` takes just the event). Principles:
- Evaluate every rule it was constructed with — the engine itself never decides a winner.
  (Disabled rules never reach this point at all — `RuleProcessor` already filtered them out.)
- Produce a `RuleMatch` for each rule that matches, in the same order the rules were supplied
  (i.e. `RuleProcessor`'s priority-descending/id-ascending order) — the engine does not re-sort.
- `ClassificationStrategy` (a separate, later milestone — see ADR-0006) decides which `RuleMatch`
  (if any) becomes the final `ClassificationResult`.
- No match at all simply means an empty `List<RuleMatch>` — `UNKNOWN`-category fallback is
  `ClassificationStrategy`'s concern, not this layer's.

Evaluation details:
- A condition's actual value comes from a fixed `RuleField -> FailureEvent` accessor mapping. If
  the event's value for that field is `null`, the condition simply does not match — not an error.
- `CONTAINS`/`EQUALS`/`STARTS_WITH`/`ENDS_WITH` case-fold via `toLowerCase(Locale.ROOT)` on both
  sides when not case-sensitive (`Locale.ROOT` specifically, to avoid locale-dependent case
  folding bugs like the Turkish-i problem). `REGEX` uses `compiledPattern.matcher(actual).find()`
  — a partial match anywhere in the string, consistent with `CONTAINS` being substring-based
  rather than requiring a whole-string match.
- Goes directly from `PreparedCondition` to `Evidence`, condition by condition, with no
  intermediate "match result" type: only conditions that actually matched are turned into
  `Evidence` entries, and whether the whole group matched is `!evidence.isEmpty()` for `any` or
  `evidence.size() == conditions.size()` for `all`. An earlier draft introduced an internal
  `ConditionMatch` record for this (kept package-private, meant to help future explainability) —
  it was removed after review because nothing in this milestone actually consumed the
  unmatched-condition information it carried, and the any/all check works without it. Reintroduce
  something like it only when a concrete consumer (e.g. a "why didn't this rule match"
  diagnostic) actually needs it.
- `RuleMatch`/`Evidence` live in `com.axiom.classifier.model`, not `com.axiom.classifier.engine`
  — they're public outputs other modules (reporting, GitHub, eventually AI) will consume, not
  engine-internal behavior. `RuleEngine`/`DefaultRuleEngine` themselves stay in `engine`.

This was designed, implemented, and tested as its own milestone before `ClassificationStrategy`
was even designed — the two are adjacent but deliberately not bundled (ADR-0006).

## ClassificationStrategy — built

### Purpose
`RuleEngine` answers "which rules matched?" `ClassificationStrategy` answers a different
question: "which matching rule wins?" Kept separate deliberately (ADR-0006) — ranking policy is
expected to evolve (AI-assisted tie-breaking, hybrid strategies) without touching evaluation.

### Types
```java
public record ClassificationResult(
    FailureCategory category, double confidence, String matchedRuleId, List<Evidence> evidence
) {}
```
Lives in `com.axiom.classifier.model`, alongside `RuleMatch`/`Evidence` — a public output, not
engine-internal. `matchedRuleId` is `null` and `evidence` is an empty list (not null) when nothing
matched.

```java
public interface ClassificationStrategy {
    ClassificationResult classify(List<RuleMatch> matches);
}
```
Lives in `com.axiom.classifier.engine`, alongside `RuleEngine` — a behavior interface, not a data
model, same reasoning that put `RuleEngine` there.

### DeterministicStrategy — the algorithm
Rank matches by **priority descending, then confidence descending, then rule id ascending**, and
return the top-ranked match's fields as a `ClassificationResult`. Priority is checked first and is
absolute — a lower-priority rule never wins over a higher-priority one no matter how much higher
its confidence is; confidence only breaks ties *within* the same priority; id only breaks ties
where both priority and confidence are equal, purely for determinism.

Worked example: matches at (priority 100, confidence 0.8), (priority 100, confidence 0.95),
(priority 90, confidence 0.99) — the third is eliminated immediately on priority alone despite the
highest confidence; between the remaining two, confidence decides: **(100, 0.95) wins**.

If `matches` is empty: `ClassificationResult(UNKNOWN, 0.0, null, List.of())`. `0.0` because there
is no evidence to support any confidence claim when nothing matched — not a separate sentinel,
just the low end of the existing `[0.0, 1.0]` range every other confidence value already respects.

**The strategy must rank independently of input order, not trust that `RuleEngine` already sorted
things.** `RuleEngine` happens to preserve `RuleProcessor`'s priority-order today (see above), but
`DeterministicStrategy` doing its own full three-key comparison — rather than assuming
`matches.get(0)` is already the winner — is what keeps these two components' responsibilities
actually separate rather than secretly coupled through an assumption neither documents.

**Losing matches are discarded entirely — confirmed after reconsidering.** `ClassificationResult`
carries only the winner's fields, no `runnerUps`/`allMatches` list. `DeterministicStrategy`
receives the complete `List<RuleMatch>` and selects from it, so the full set is available to the
caller *before* the strategy runs; a separate diagnostic/decision-trace model can wrap that later
if reporting or AI ever needs runner-up visibility — not something `ClassificationResult` itself
should carry preemptively. Confidence `0.0` for the no-match case is confirmed for the same
reason: no evidence exists to support any nonzero confidence claim when nothing matched.

### Tests planned
Empty matches -> `UNKNOWN`/`0.0`/`null`/empty evidence; single match trivially wins; priority tie
broken by confidence; priority+confidence tie broken by id; a losing match's evidence never
leaks into the result; and — the one that actually proves independence from `RuleEngine`'s
ordering — the winner is correctly identified even when matches are supplied in a deliberately
scrambled (non-priority-sorted) order.

Future: `AIEnhancedStrategy`, `HybridStrategy` — swappable without `RuleEngine` changes.
