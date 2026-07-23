# Correlation Signal/Weight Matrix — v0.3 (as of 2026-07-22, `FlakyTestRule`)

Internal design documentation for `axiom-correlation` — not because the code needs it (each
weight is a named constant on its own rule class, see `04-rule-engine.md`'s equivalent reasoning
for `axiom-classifier`), but because comparing rules "by eye" across separate files gets harder as
more of them accumulate. This doc is a human-readable index into the actual constants, not a
second source of truth — if it and the code ever disagree, the code wins; fix this doc.

## Weight matrix (as of `FlakyTestRule`, the third rule)

| Signal | ApplicationBugCorrelationRule | InfrastructureFailureRule | FlakyTestRule |
| --- | ---: | ---: | ---: |
| Stack frame matches changed file | +0.40 | -0.35 | -0.40 (blocking) |
| No stack frame correlates with changed file | 0 (not consumed) | 0 (not consumed) | +0.10 |
| Retry failed | +0.25 | 0 (not consumed) | 0 (not consumed) |
| Retry passed | -0.35 (blocking) | +0.20 | +0.45 |
| Top frame is test code | -0.30 (blocking) | 0 (not consumed) | 0 (not consumed) |
| Failure cluster present | 0 (not consumed) | +0.25 | -0.30 |
| No failure cluster present | 0 (not consumed) | 0 (not consumed) | +0.10 |
| Change-set evidence missing | -0.15 | 0 (not consumed) | 0 (not consumed) |
| Existing classification = `APPLICATION_BUG` | +0.15 | -0.25 | -0.25 |
| Existing classification = `INFRASTRUCTURE_FAILURE` | 0 (not consumed) | +0.40 | -0.20 |
| Existing classification = `FLAKY_TEST` | 0 (not consumed) | 0 (not consumed) | +0.20 |

Read column-by-column, not row-by-row: **the same signal can mean opposite things to different
rules** (`RETRY_PASSED` contradicts application-bug but supports both infrastructure and
flaky-test — see ADR-equivalent reasoning in `13-evidence-correlation-design.md` §9), and **a rule
consuming "0" for a signal is a deliberate omission, not a missing feature** (e.g.
`InfrastructureFailureRule` intentionally never penalizes missing change-set evidence — see
below). `FlakyTestRule` is the first rule to score an *absence* as a positive contribution (no
stack match, no cluster) rather than only scoring presence — see its rationale below for why that
absence-as-evidence pattern is treated as weaker than direct positive evidence, not equally strong.

**Known asymmetry, documented rather than silently fixed**: `InfrastructureFailureRule` penalizes
`-0.25` when the existing classification is already `APPLICATION_BUG`, but
`ApplicationBugCorrelationRule` has no corresponding penalty when the existing classification is
already `INFRASTRUCTURE_FAILURE` — it simply doesn't check for that category at all. This is a
real gap left over from when `ApplicationBugCorrelationRule` was the only rule and had nothing to
be asymmetric against. Not changed here — introducing that penalty would be a scoring-behavior
change, not a documentation fix, and needs its own dedicated tests and fixture review. **Open
question for the next scoring iteration**: consider whether existing classifications for competing
categories should contribute symmetric negative evidence. Do not assume symmetry automatically —
validate it against real failure examples first; some classifications may deserve asymmetric
treatment because they have different reliability or specificity.

## `FlakyTestRule`'s scope is narrower than its name (read before tuning its weights)

`FlakyTestRule` identifies a **single-run transient failure**, not historical flakiness. Axiom has
no evidence source for behavior across multiple runs today — no `FAILED_IN_MULTIPLE_RECENT_RUNS`,
`FLAKE_RATE_ABOVE_THRESHOLD`, or similar signal exists — so "this test is known to be flaky" is not
a claim this rule is entitled to make; only "this failure appears transient in this execution" is.
`FailureCategory.FLAKY_TEST` is reused for taxonomy compatibility, but the presentation layer must
never render "this test is flaky" — see `AssessmentFacts`'s `"Possibly flaky (this run)"` label and
its category-specific recommended action. When a real historical-evidence source is added later,
this rule should evolve (e.g. distinguishing a `TRANSIENT_THIS_RUN` hypothesis from a
`HISTORICALLY_FLAKY` one), not be silently replaced.

This rule also reasons **by elimination** more than the other two: "not code, not infrastructure,
and it un-failed" is weaker than `ApplicationBugCorrelationRule`'s direct stack-match or
`InfrastructureFailureRule`'s direct cluster-present evidence. That's reflected in treating
absence-of-correlation as a modest `+0.10` bonus (not a strong claim) while treating an *actual*
code correlation as a blocking veto (see below) — a real intermittent bug can still pass on retry,
so "no evidence of a bug" must not be allowed to override "evidence of a bug," however weak the
former's positive framing looks numerically.

## Why these weights (rationale, not just numbers)

The exact numeric value of each weight will likely shift with real-world tuning; the reasoning
behind it is what should survive that.

**Stack frame matches changed file** — `+0.40` app-bug / `-0.35` infra / `-0.40` flaky (blocking):
the failing code path itself was just changed, which is the most direct evidence a regression
connects to a specific commit. For infrastructure, the same fact points the other way — it's not
proof the failure is code-caused, but it's evidence competing with an infra explanation, so it
lowers confidence rather than ruling infrastructure out entirely (see "Blocking contradictions"
below for why this isn't a hard veto there). For flaky, it *is* a hard veto: a real, intermittent
application bug can still pass on retry, so "the test passed on retry" must never be allowed to
override "the failing code was just changed" — the latter is direct positive evidence, the former
(retry passing) is comparatively weak.

**No stack frame correlates with a changed file** — `+0.10` flaky only: absence of evidence, not
evidence of absence — kept deliberately modest (a third of `RETRY_PASSED`'s weight) precisely
because "we didn't find a code correlation" is a much weaker claim than "we found a cluster" or "we
found a code correlation" would be for the other two rules' positive signals.

**Retry failed** — `+0.25` app-bug (not consumed by infra or flaky): a real defect reproduces
reliably; a failure that keeps failing on retry looks less like transient noise.

**Retry passed** — `-0.35` app-bug (blocking) / `+0.20` infra / `+0.45` flaky: real application
bugs don't spontaneously disappear on retry with nothing changed — this is treated as a hard veto
for app-bug, not just a lowered score, because "the defect vanished on its own" is a qualitatively
different claim than "the defect is less likely." For infrastructure it's supporting but
deliberately modest (`+0.20`, and never sufficient alone — see the eligibility gate): passing on
retry is consistent with a transient infra hiccup, but it's equally consistent with a flaky test,
so it can't carry an infrastructure hypothesis by itself. For flaky it's the dominant contribution
(`+0.45`, over half the 0.85 ceiling) — it's the one piece of direct evidence this rule actually
has (see "narrower than its name" above); everything else is corroboration or absence-of-evidence.

**Top frame is test code** — `-0.30` app-bug (blocking, not consumed by infra or flaky): if the
failure originates in the test method itself rather than production code, the defect is more
likely in the test, not the system under test — a different category of problem (test-automation,
a future rule) entirely, hence a hard veto rather than a partial deduction. Says nothing about
infrastructure health or transience either way, so neither infra nor flaky consume it.

**Failure cluster present** — `+0.25` infra (not consumed by app-bug) / `-0.30` flaky
(non-blocking): several tests failing in the same execution window is a classic signature of a
shared, external cause (a degraded dependency, a network blip) — but per
`FAILURE_CLUSTER_PRESENT`'s own definition, it only proves other failures existed, not that they
share a cause, which is why it's supporting rather than determinative on its own for infra, and why
it doesn't block flaky outright: isolated flaky tests and infrastructure incidents can coexist, so
this substantially lowers flaky's confidence without ruling it out. A cluster says nothing about
whether *this specific test's* logic has a bug, so app-bug doesn't consume it.

**No failure cluster present** — `+0.10` flaky only: same modest weight and same reasoning as "no
stack frame correlates" above — absence of a cluster is weaker evidence than its presence would be.

**Change-set evidence missing** — `-0.15` app-bug (not consumed by infra or flaky): this rule's
entire claim is "this specific change caused this specific failure," so its confidence should erode
when there's no visibility into what changed at all. Neither infrastructure nor flakiness make such
a claim about a specific change, so the absence of that evidence isn't evidence for or against
either — treating "we don't know what changed" as support would be fabricating a signal from an
absence of data (same principle applied in the presentation layer's refusal to invent an owner or
time estimate).

**Existing classification = `APPLICATION_BUG`** — `+0.15` app-bug / `-0.25` infra / `-0.25` flaky:
the single-event deterministic classifier already reached this conclusion independently, via its
own YAML-authored rule matching — corroboration from an earlier, separately-validated mechanism.
For infrastructure and flaky, an independent classifier already confidently calling it a bug is
real (if not conclusive) evidence pointing away from either.

**Existing classification = `INFRASTRUCTURE_FAILURE`** — `+0.40` infra (not consumed by app-bug,
see the asymmetry note above) / `-0.20` flaky: the strongest single contribution any rule makes,
because it reuses an entirely separate, already-validated mechanism (e.g. the classifier's own
"Connection refused" pattern rule) rather than deriving anything new. For flaky, a confident
existing infrastructure call is real evidence against a transient single-test explanation, though
weighted slightly less than the application-bug penalty since infrastructure and flakiness are
somewhat closer neighbors than infrastructure and a code defect.

**Existing classification = `FLAKY_TEST`** — `+0.20` flaky only: the same corroboration pattern as
the other two rules' self-referential bonus — an earlier, separately-validated mechanism already
reached the same conclusion. Not the dominant contribution (unlike `RETRY_PASSED`) because the
deterministic classifier's own flaky-detection rules (if authored) are typically heuristic
pattern-matching on the message, not evidence of actual transience within this execution.

## Blocking contradictions (hard veto, independent of the arithmetic sum)

- **`ApplicationBugCorrelationRule`**: `RETRY_PASSED` and `TOP_FRAME_IS_TEST_CODE` are both
  blocking — either one forces `NEEDS_INVESTIGATION` regardless of the confidence number.
- **`InfrastructureFailureRule`**: no blocking contradictions. A changed production file matching
  the stack trace makes application-bug *more plausible*, but doesn't make infrastructure
  *impossible* — there's no domain invariant strong enough to justify a hard veto here, only a
  score reduction. Resist adding one without being able to state that invariant explicitly (per
  review discussion, 2026-07-21) — "this evidence makes X less likely" is not the same claim as
  "this evidence makes X impossible."
- **`FlakyTestRule`**: `STACK_FRAME_MATCHES_CHANGED_FILE` is blocking; `FAILURE_CLUSTER_PRESENT` is
  not. The domain invariant that justifies the first: a real, intermittent application bug can
  still pass on retry, so "it passed on retry" must never override "the failing code was just
  changed" — that would be treating weak evidence as if it outranked strong evidence. No equivalent
  invariant exists for a failure cluster (isolated flakiness and infrastructure incidents genuinely
  can coexist), so that one only lowers confidence substantially, same non-blocking treatment as
  infrastructure's stack-match contradiction above.

## Eligibility gates (rule fires at all vs. returns `Optional.empty()`)

- **`ApplicationBugCorrelationRule`**: fires whenever *any* contribution applies — no separate
  eligibility check beyond "did at least one signal fire."
- **`InfrastructureFailureRule`**: requires `classifiedAsInfrastructure || failureClusterPresent`
  before evaluating anything else. A single passing retry, alone, must never suggest
  infrastructure — that gate exists specifically to keep a future flaky-test rule's territory
  from being claimed by this one.
- **`FlakyTestRule`**: requires `RETRY_PASSED` — you cannot call a failure transient without having
  seen it pass at least once. The narrowest gate of the three (a single boolean, not an OR), and
  the rule this project's own earlier review anticipated when writing `InfrastructureFailureRule`'s
  gate ("keep a future flaky-test rule's territory from being claimed by this one" — see above).
  Eligibility still lives inline at the top of each rule's `evaluate()`; fine at three rules, still
  each a single condition or a two-term OR. Revisit extracting a separate `isApplicable(...)` step
  only if a fourth rule's eligibility logic becomes noticeably more complex than this (flagged in
  review, not yet a concrete need).

## Assessment-level thresholds (apply to every rule, not rule-specific)

```
CONFIDENCE_THRESHOLD    = 0.70   // AssessmentSelector
MINIMUM_HYPOTHESIS_LEAD = 0.15   // AssessmentSelector — top hypothesis must lead the
                                 // second-ranked one by at least this much to be selected
```

## Adding a new rule to this matrix

When the next rule (test-automation, deployment, dependency — per `07-roadmap.md`) lands, add its
column here in the same commit that adds the rule. If a weight or blocking flag listed here stops
matching the actual `static final` constant in code, that's a bug in this doc, not in the code.
