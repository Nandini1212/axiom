# Historical Execution Evidence — Design (v0.1, proposed)

**Status: proposed, not approved, no production code exists yet.** Written before implementation
per the agreed sequence: design first, then `TestIdentity`, then the evidence/adapter, then
signals, then the aggregation decision, and only then `HistoricalFlakyTestRule`. Short reference
once built: `02-system-architecture.md`'s Module Reference section.

## 1. Context

Every rule so far (`ApplicationBugCorrelationRule`, `InfrastructureFailureRule`, `FlakyTestRule`)
reasons from a single execution: this failure's message, stack trace, one changed-file summary,
one retry outcome. `FlakyTestRule`'s own javadoc already names the gap this closes: it can only
say "this failure appears transient in this execution," never "this test is known to be flaky,"
because no evidence source describes behavior across multiple runs. This is the smallest
architectural step that unlocks a genuinely new kind of conclusion without adding a live
integration (GitHub, CI APIs) — same "local file, no external system access" discipline as
`changes.json`/`execution.json`.

## 2. Goals

- A new, optional local input (`history.json`) describing prior outcomes for the same logical
  test — last **N** completed runs, not a time window (bounded, inspectable sample; a time window
  makes clock handling and uneven run frequency part of the domain for no real benefit).
- A stable test identity so history entries can be matched to "the current test" — not string
  concatenation done ad hoc at each call site (see §3).
- Raw evidence only. No precomputed `flakeRate`/`isFlaky` anywhere in the evidence or adapter layer
  — those are the engine's conclusions to derive deterministically, not the input's to assert.
- A small, honest signal set (§7) — no signature-similarity matching in this slice (a real project
  of its own; deferred).
- A concrete decision, made here rather than discovered mid-implementation, on how a second rule
  that can also produce `FLAKY_TEST` interacts with the existing one at selection time (§9) —
  this is the one open question with no obvious answer, so it's resolved before code, not after.

## 3. Test identity

**Found while designing this, not assumed**: `FailureEvent` has no single identity accessor today
— just `testName`/`className`/`suiteName` as three separate nullable fields, with at least one
guaranteed present. The presentation layer already derives an ad hoc "className.testName" string
in `AssessmentFacts.describeTest()`, duplicated logic with no shared type behind it. That's a
second real consumer (history matching needs the exact same concept as the renderer's display
name), which is what justifies introducing a type now rather than speculatively:

```java
package com.axiom.correlation.model;

public record TestIdentity(String className, String testName) {
    public TestIdentity {
        className = requireNonBlank(className, "className");
        testName = requireNonBlank(testName, "testName");
    }

    public String canonicalName() {
        return className + "#" + testName;
    }

    public static Optional<TestIdentity> from(FailureEvent event) { ... }
}
```

Lives in `axiom-correlation`, not `axiom-common` — the only concrete consumers today (history
matching, this module's own presentation layer) are both here. Move it to `axiom-common` only if
a consumer outside this module needs it later, not preemptively.

`canonicalName()` uses `#`, not `.` — distinguishes the class/method boundary more clearly than
Java's own dot-heavy fully-qualified names would. It's a distinct concern from `toString()` (not
overridden to match): `canonicalName()` is for matching/machine use (history lookups), while the
renderer's own display format (simple class name + `.` + test name, e.g.
`"PaymentServiceTest.testCharge"`) is a presentation concern derived from the same two fields, not
a reuse of `canonicalName()` — the domain type doesn't own display formatting, same principle as
`RootCauseHypothesis` not owning renderer-ready text (§11). Matching is exact and case-sensitive —
no normalization.

**`from(FailureEvent)` returns `Optional`, not `TestIdentity` directly — found while grounding this
against the actual `FailureEvent` model.** `FailureEvent` allows a suite-level failure with only
`suiteName` present (no `testName`, no `className` — e.g. a container startup failure with no
individual test method); `TestIdentity` requires both non-blank. Returning `Optional.empty()` for
that case is honest: a suite-level failure has no logical *test* identity to match history against
at all — not a gap to paper over with a fallback substitution (e.g. treating `suiteName` as a
stand-in `className`, which would misrepresent identity, not preserve it). `AssessmentFacts`'s
display path falls back to its own existing testName/className/suiteName logic when `from` returns
empty; only the "both present" case is replaced by `TestIdentity`.

Deliberately excludes the failure message/stack trace from identity — those describe one
occurrence, not the logical test. Known, accepted limitation for v0.1: parameterized/dynamic
tests sharing the same `className`+`testName`, and renamed/moved tests, don't have a distinguishing
identity under this scheme — documented, not solved here.

**Extension point, reserved but not built**: version 1 intentionally defines identity as
`className`+`testName` only. Future schema versions may extend `TestIdentity` with additional
identity components (e.g. a parameterized-test discriminator) without breaking existing equality
semantics for the tests that don't need one — this is a documented reservation of intent, not an
implementation. Recorded here specifically so the `className`+`testName`-only limitation reads as
a deliberate v1 boundary if revisited later, not as something nobody thought about.

## 4. Evidence model

```java
public enum HistoricalOutcome { PASSED, FAILED }

public record HistoricalTestRun(String runId, Instant timestamp, HistoricalOutcome outcome) {}

public record HistoricalExecutionEvidence(
        String evidenceId,
        Instant observedAt,
        TestIdentity testIdentity,
        Optional<String> branch,
        List<HistoricalTestRun> runs
) implements CorrelationEvidence {
    @Override public EvidenceType type() { return EvidenceType.HISTORICAL_EXECUTION; }
}
```

Raw and immutable — no derived fields (`totalRuns`, `failureRate`, etc.) stored here. Those are
signal-extraction's job (§7), same reasoning as keeping `ConfidenceContribution` weights out of
the evidence layer today.

## 5. Wire format

Same DTO/domain split as `ChangeSetInput`/`SourceChangeEvidence`:

```json
{
  "schemaVersion": "1.0",
  "generatedAt": "2026-07-22T18:00:00Z",
  "branch": "main",
  "tests": [
    {
      "className": "com.example.PaymentServiceTest",
      "testName": "testCharge",
      "runs": [
        { "runId": "build-1042", "timestamp": "2026-07-22T15:00:00Z", "outcome": "PASSED" },
        { "runId": "build-1041", "timestamp": "2026-07-22T13:00:00Z", "outcome": "FAILED" }
      ]
    }
  ]
}
```

```java
public record HistoryInput(String schemaVersion, Instant generatedAt, Optional<String> branch, List<HistoricalTestInput> tests) {}
public record HistoricalTestInput(String className, String testName, List<HistoricalRunInput> runs) {}
public record HistoricalRunInput(String runId, Instant timestamp, HistoricalOutcome outcome) {}
```

Deliberately no `flakeRate`/`failureRate` fields in this schema — a source file that could compute
and assert its own flake rate is exactly the "second source of truth" problem the correlation
weight-matrix doc already warns against for a different reason (docs vs. code); here it would be
input data vs. engine calculation, same failure mode.

## 6. Adapter

```java
package com.axiom.correlation.adapter; // new package — first adapter with real parsing/validation

public final class HistoryFileAdapter {
    public HistoricalExecutionEvidence adapt(HistoryInput input, TestIdentity currentTest) { ... }
}
```

Owns: matching `currentTest` against `input.tests()` (by className+testName, not fuzzy), ordering
runs newest-first regardless of input order, deduplicating identical `runId`s, and producing
warnings (mirroring `ParserWarning`'s "no silent data loss" principle) for unusable entries —
missing fields, unparseable timestamps, unknown outcome strings — rather than silently dropping
them. The engine never sees JSON or a file path; this adapter is the only place that exists, same
boundary `CorrelationEngine` already holds for the other two evidence sources.

**First adapter with real logic** — `SourceChangeEvidence.from`/`ExecutionEvidence.from` are
one-line static factories on the domain record itself (§8's decision in the v0.1 design doc: "no
new speculative factories"). This one earns a dedicated class because matching/ordering/dedup is
real logic, not a field-for-field copy — revisit whether the other two should also move to
`.adapter` only if this one proves the pattern is worth generalizing, not preemptively.

## 7. Branch semantics

- Both current context and history declare a branch → use only matching-branch runs.
- History has no branch recorded → usable, but the evidence/signal should record it as unscoped
  (a `HISTORICAL_UNSCOPED_BY_BRANCH`-style fact, not silently treated as universal).
- Branch mismatch → **unusable for this assessment, not negative evidence.** A failure on
  `feature/x` doesn't get penalized by `main`'s clean history, but it also doesn't get to claim
  `main`'s history as support.
- No fallback inheritance (e.g. "feature branch falls back to main") in v0.1 — that policy can
  misrepresent a feature branch's actual behavior as more or less stable than it is; not built
  until a concrete need demonstrates the simple version is actually insufficient.

## 8. Sample-size semantics

```java
static final int MINIMUM_USABLE_HISTORICAL_RUNS = 5; // named constant, not inline — same reasoning
                                                       // as every other threshold in this module
```

Below this, history may still be shown (transparency), but must not produce a strong
historical-flakiness signal — "insufficient history" and "stable pass/fail pattern" are distinct
facts, never conflated. No hardcoded percentage (e.g. "30% flaky") anywhere in the evidence or
adapter layer — thresholds belong in signal extraction or rule weights, same layering the
correlation weight-matrix doc already documents for the existing rules.

## 9. Signals (v0.1 set — deliberately small)

```
HISTORICAL_EXECUTION_PRESENT
HISTORICAL_SAMPLE_SUFFICIENT       // runs.size() >= MINIMUM_USABLE_HISTORICAL_RUNS
HISTORICAL_MIXED_OUTCOMES          // both PASSED and FAILED present in the usable sample
HISTORICAL_ALWAYS_PASSED
HISTORICAL_ALWAYS_FAILED
```

Deferred, not designed yet: `HISTORICAL_FAILURE_RATE_ABOVE_THRESHOLD` (needs a threshold decision
informed by real data, same caution as every weight in this project), `RECENT_OUTCOME_ALTERNATION`,
`FAILURE_SIGNATURE_RECURRED` (signature similarity is its own matching problem — explicitly out of
scope here).

## 10. Rule architecture: two rules, not one expanded rule

**Adopted: split, not merge.** `TransientFailureRule` (this execution's retry evidence — today's
`FlakyTestRule`, see below) and a new `HistoricalFlakyTestRule` (historical mixed-outcome evidence)
both map to `FailureCategory.FLAKY_TEST`, but with distinct rule IDs, evidence trails, and wording.
"Retry passed this run" and "this test has repeatedly alternated between pass and fail" are
different claims with different evidentiary weight; collapsing them into one rule's contribution
list would blur exactly the distinction `FlakyTestRule`'s own javadoc was written to preserve.

**This is the trigger the backlog already named.** `07-roadmap.md`'s backlog entry for this exact
rename says: *"Not renaming now — consistency with the classifier's naming has its own value, and
there's only one such rule today, not two competing ones that actually need distinguishing."* Once
`HistoricalFlakyTestRule` exists, that condition flips true. **Sequencing, per review**: the rename
happens as its own commit (`refactor(correlation): rename flaky rule to transient failure`),
immediately before `HistoricalFlakyTestRule` is added (`feat(correlation): add historical
flaky-test reasoning`) — not bundled into one commit, and not done speculatively earlier. `RULE_ID`
becomes `"transient-failure-v1"` (a rule-id change; no external reference to `"flaky-test-v1"`
exists outside this codebase's own tests).

## 11. The aggregation question (must be resolved before implementation)

Today, `AssessmentSelector` treats each `ScoredEvaluation` as one hypothesis and ranks them
directly. With two rules able to produce the same category, three options exist:

1. Keep every rule's evaluation as a fully separate hypothesis.
2. Aggregate same-category evaluations into one hypothesis before ranking.
3. Select the single strongest rule per category, discarding the other's contribution entirely.

**Adopted: option 2, per review** — keep each rule's evaluation separate during scoring (so
`FlakyTestRuleTest`/`HistoricalFlakyTestRuleTest`-style direct unit tests keep working unchanged),
but aggregate same-category results into one hypothesis *before* cross-category selection.
Rejected option 1: two rules that both point at `FLAKY_TEST` would needlessly compete with each
other under the minimum-lead rule, potentially forcing `NEEDS_INVESTIGATION` even when both rules
agree — an artifact of implementation (two rules), not a real evidentiary conflict. Rejected option
3: silently discarding a corroborating rule's evidence violates the "no silent data loss" principle
this project applies everywhere else.

**Mechanism, concretely** — `AssessmentSelector.select` gains a grouping step before ranking:

```java
Map<FailureCategory, List<ScoredEvaluation>> byCategory = scored.stream()
    .collect(Collectors.groupingBy(s -> s.evaluation().category()));

List<ScoredEvaluation> aggregated = byCategory.values().stream()
    .map(AssessmentSelector::aggregate)   // new: combine same-category evaluations into one
    .toList();
// existing sort/lead/threshold/blocking logic runs on `aggregated`, unchanged
```

`aggregate(List<ScoredEvaluation> sameCategory)` is trivial when the list has one element — every
existing test's behavior is unchanged, since today every category has exactly one rule. For more
than one element, two policies need to be fixed now, not discovered mid-implementation:

**Policy A — confidence is the maximum across paths, never a sum.** `0.70 + 0.80` capped at `1.0`
would double-count correlated evidence and make a category "stronger" merely for having more rules
behind it — an artifact of rule count, not of the evidence itself.

```text
TransientFailureRule       0.65
HistoricalFlakyTestRule    0.80
Aggregated FLAKY_TEST      0.80   (the max, not 1.0 and not a weighted blend)
Reasoning paths            both rules retained for transparency
```

Additional same-category rules become corroborating reasoning paths and supporting evidence in the
aggregate's `contributions` list, but do not numerically increase confidence beyond what the
strongest independently-justified rule already claimed. This can be revisited (a documented
corroboration bonus) only after determining whether same-category rules actually consume
independent evidence — not assumed now.

**Policy B — blocking is path-local, not flattened into one category-wide boolean up front.** A
blocker belonging to one rule must not automatically invalidate a different rule's independent
support for the same category unless the contradiction semantically applies to that category
itself. Concretely: `aggregate` partitions `sameCategory` into blocked and unblocked paths;
confidence is the max **among unblocked paths only** (a blocked path's own confidence number is
never allowed to leak into the aggregate — it was already vetoed); the aggregate's
`hasBlockingContradiction` is `true` only if *every* path is blocked. Worked example: this
occurrence's stack frame matches a changed production file, which blocks
`TransientFailureRule` (a real intermittent bug can still pass on retry, so this occurrence isn't
safely callable transient) — but `HistoricalFlakyTestRule`'s own evidence (this test has
repeatedly alternated pass/fail across many prior runs) is untouched by that fact and can still
independently clear the threshold. Result: `FLAKY_TEST` may still be `DETERMINED` on the strength
of the historical path alone, while the transient path's veto and reasoning remain visible in the
evidence trail — "this occurrence may be an application bug, though this test also has a
historical flaky pattern," not a single flattened yes/no.

**Required model change, scoped to the minimal option per review**: `RootCauseHypothesis`'s
`matchedReasoningPath` (today a single `String`) becomes `matchedReasoningPaths: List<String>` —
stable rule ids only, e.g. `["transient-failure-v1", "historical-flaky-test-v1"]`, never
renderer-ready text. No new `ReasoningPath` record in this slice: per-path provenance (which rule
contributed which contradiction, at what weight) is already fully reconstructable from
`ConfidenceContribution.ruleId()` on each entry in the aggregate's (concatenated, never summed-away)
`contributions` list, so a separate structure would duplicate information already present rather
than add new information. Revisit only if a concrete consumer needs per-path confidence *before*
aggregation, which `contributions` doesn't currently expose. This changes the presentation layer's
detailed-mode output (`"Hypothesis 1 [rule: application-bug-v1]"` → something like `"Hypothesis 1
[rules: transient-failure-v1, historical-flaky-test-v1]"` when aggregated, formatted by the
renderer from the raw id list — **the domain object carries ids only, never the formatted label**)
and every existing golden-string test asserting that exact line — a real, visible, one-time
compatibility cost, called out now rather than discovered mid-implementation.

## 12. Non-goals (v0.1 of this evidence source)

- No signature-similarity matching (§9).
- No branch-inheritance policy (§7).
- No live integration (CI API, GitHub) to *produce* `history.json` — same as `changes.json`
  today, generating the file is the caller's problem, not this module's.
- No `HISTORICAL_FAILURE_RATE_ABOVE_THRESHOLD` or any percentage-based signal — needs real data to
  set responsibly, not invented now.
- No CLI wiring — same status as the rest of `axiom-correlation` today.

## 13. Implementation sequence (per review)

```
1. This document (done)
2. TestIdentity — its own commit before continuing to step 3
3. HistoricalExecutionEvidence + HistoricalTestRun (+ HistoryInput wire DTOs)
4. HistoryFileAdapter + validation tests (branch scope, ordering, duplicate runs, malformed entries)
5. The five signals in §9
6. AssessmentSelector aggregation (§11) — implemented and tested against the *existing* three
   rules first, proving it's a no-op for today's one-hypothesis-per-category case, before either
   new rule exists
7. FlakyTestRule → TransientFailureRule rename (§10) — its own commit
   (refactor(correlation): rename flaky rule to transient failure)
8. HistoricalFlakyTestRule — its own commit
   (feat(correlation): add historical flaky-test reasoning)
```

Step 6 still comes before step 7/8 for the same reason as before: prove the aggregation mechanism
safe against the current three rules — where it must change nothing — before a fourth rule and a
rename make it load-bearing.

Step 6 deliberately comes before step 8: the aggregation mechanism should be proven safe against
the current three rules (where it must change nothing) before a fourth rule makes it load-bearing.
