# Domain Model

## FailureEvent (axiom-common) — built
A single normalized test failure, independent of source format and downstream consumer.

Fields: `id`, `testName`, `className`, `suiteName`, `sourceFormat`, `status`, `message`,
`stackTrace`, `durationMillis`, `occurredAt`, `pipelineContext`, `metadata`.

Mandatory: `id` (non-blank), `status`, `sourceFormat`, at least one of `testName`/`className`/
`suiteName` (a blank string doesn't count as present). Optional/nullable-with-meaning:
`testName`/`className`/`suiteName` individually, `message`, `stackTrace`, `durationMillis` (`null`
means unknown, not zero), `occurredAt`, `pipelineContext` (`null` means no CI context; an
all-null `PipelineContext` is normalized to `null`). `metadata` defaults to an immutable empty
map; `null` keys/values are rejected explicitly.

## PipelineContext (axiom-common) — built
`provider`, `repository`, `workflow`, `job`, `branch`, `commitSha`, `pullRequestNumber`, `runId` —
all individually nullable. No "empty" sentinel: an all-null instance carries no more information
than `null` itself, so `FailureEvent` normalizes it away.

## Rule domain (axiom-classifier) — built
- `RuleField` — closed enum of `FailureEvent` fields a rule may match against: `MESSAGE`,
  `STACK_TRACE`, `TEST_NAME`, `CLASS_NAME`, `SUITE_NAME`.
- `Operator` — `CONTAINS`, `EQUALS`, `REGEX`, `STARTS_WITH`, `ENDS_WITH`.
- `Condition` — `field` + `operator` + `value` + `caseSensitive` (nullable; defaults to `false`
  at the RuleProcessor stage).
- `MatchGroup` — `any` (OR) or `all` (AND) list of `Condition`; exactly one must be non-empty
  (no nested groups in v1 — deferred until a concrete rule needs it).
- `ClassificationSpec` — `category` (`FailureCategory`) + `confidence` (validated to `[0.0, 1.0]`
  at construction, not deferred — an invariant of the value itself).
- `EvidenceSpec` — `message` (human-readable evidence template).
- `RuleDefinition` — `id` (non-blank) + `description` + `priority` (nullable) + `enabled`
  (nullable) + `match` + `classification` + `evidence`. Raw, as authored in YAML; RuleProcessor
  resolves everything nullable.

## FailureCategory (axiom-classifier) — built
`APPLICATION_BUG`, `TEST_AUTOMATION_BUG`, `INFRASTRUCTURE_FAILURE`, `DEPLOYMENT_FAILURE`,
`ENVIRONMENT_FAILURE`, `CONFIGURATION_FAILURE`, `DATA_ISSUE`, `DEPENDENCY_FAILURE`,
`FLAKY_TEST`, `UNKNOWN`.

## Prepared rule domain (axiom-classifier, post-RuleProcessor) — built
- `PreparedCondition` — `field`, `operator`, `value`, `caseSensitive` (resolved), `compiledPattern`
  (non-null iff `operator == REGEX`).
- `PreparedMatchGroup` — same any/all shape as `MatchGroup`, holding `PreparedCondition`s.
- `PreparedRule` — `id`, `priority` (resolved `int`, default `0`, unbounded), `match`, `category`,
  `confidence`, `evidenceMessage`. Flattened — no nested `ClassificationSpec`/`EvidenceSpec`, no
  `description` (documentation-only, dropped at this stage), no `enabled` field (disabled rules
  are filtered out entirely during processing, not carried as a flag).

## Evidence (axiom-classifier, `com.axiom.classifier.model`) — built
`field`, `operator`, `expectedValue`, `actualValue`, `explanation` — one per condition that
contributed to a match, constructed by `RuleEngine` from the matched `PreparedCondition` plus the
actual value read from the `FailureEvent` being evaluated. Named `actualValue`, not
`actualExcerpt` — a domain object describing what matched, not a presentation-layer decision
about how much of a long value to display (truncation for display is `axiom-reporting`'s future
concern). Distinct from `EvidenceSpec.message`/`PreparedRule.evidenceMessage`, which is the
authored template, not the runtime-populated object — `Evidence.explanation` is that same
authored message, carried through unchanged into each `Evidence` entry for a match.

## RuleMatch (axiom-classifier, `com.axiom.classifier.model`) — built
`ruleId`, `priority`, `category`, `confidence`, `evidence` (`List<Evidence>`, never empty for a
match). Produced by `RuleEngine.evaluate(FailureEvent)`, one per rule that matched. `evidence` is
a list, not a single value, because more than one condition can contribute to a match (every
condition in an `all` group, or several satisfied conditions in an `any` group) — each gets its
own `Evidence` entry rather than collapsing to just one. `priority` is carried directly on
`RuleMatch` (flattened, same reasoning as `PreparedRule`) so `ClassificationStrategy` (not yet
built) doesn't need to look back at the original `PreparedRule`.

## ClassificationResult (axiom-classifier) — not yet implemented
`category`, `confidence`, `matchedRuleId`, `evidence`. Produced by `ClassificationStrategy`
picking among the `RuleMatch`es `RuleEngine` returns — effectively the winning `RuleMatch`,
reshaped as the platform's final answer for one `FailureEvent`.
