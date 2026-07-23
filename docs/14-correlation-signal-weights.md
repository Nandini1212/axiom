# Correlation Signal/Weight Matrix — v0.2 (as of 2026-07-21, `InfrastructureFailureRule`)

Internal design documentation for `axiom-correlation` — not because the code needs it (each
weight is a named constant on its own rule class, see `04-rule-engine.md`'s equivalent reasoning
for `axiom-classifier`), but because comparing rules "by eye" across separate files gets harder as
more of them accumulate. This doc is a human-readable index into the actual constants, not a
second source of truth — if it and the code ever disagree, the code wins; fix this doc.

## Weight matrix (as of `InfrastructureFailureRule`, the second rule)

| Signal | ApplicationBugCorrelationRule | InfrastructureFailureRule |
| --- | ---: | ---: |
| Stack frame matches changed file | +0.40 | -0.35 |
| Retry failed | +0.25 | 0 (not consumed) |
| Retry passed | -0.35 (blocking) | +0.20 |
| Top frame is test code | -0.30 (blocking) | 0 (not consumed) |
| Failure cluster present | 0 (not consumed) | +0.25 |
| Change-set evidence missing | -0.15 | 0 (not consumed) |
| Existing classification = `APPLICATION_BUG` | +0.15 | -0.25 |
| Existing classification = `INFRASTRUCTURE_FAILURE` | 0 (not consumed) | +0.40 |

Read column-by-column, not row-by-row: **the same signal can mean opposite things to different
rules** (`RETRY_PASSED` contradicts application-bug but supports infrastructure — see ADR-equivalent
reasoning in `13-evidence-correlation-design.md` §9), and **a rule consuming "0" for a signal is a
deliberate omission, not a missing feature** (e.g. `InfrastructureFailureRule` intentionally never
penalizes missing change-set evidence — see below).

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

## Why these weights (rationale, not just numbers)

The exact numeric value of each weight will likely shift with real-world tuning; the reasoning
behind it is what should survive that.

**Stack frame matches changed file** — `+0.40` app-bug / `-0.35` infra: the failing code path
itself was just changed, which is the most direct evidence a regression connects to a specific
commit. For infrastructure, the same fact points the other way — it's not proof the failure is
code-caused, but it's evidence competing with an infra explanation, so it lowers confidence rather
than ruling infrastructure out entirely (see "Blocking contradictions" below for why this isn't a
hard veto).

**Retry failed** — `+0.25` app-bug (not consumed by infra): a real defect reproduces reliably; a
failure that keeps failing on retry looks less like transient noise.

**Retry passed** — `-0.35` app-bug (blocking) / `+0.20` infra: real application bugs don't
spontaneously disappear on retry with nothing changed — this is treated as a hard veto for
app-bug, not just a lowered score, because "the defect vanished on its own" is a qualitatively
different claim than "the defect is less likely." For infrastructure it's supporting but
deliberately modest (`+0.20`, and never sufficient alone — see the eligibility gate): passing on
retry is consistent with a transient infra hiccup, but it's equally consistent with a flaky test,
so it can't carry an infrastructure hypothesis by itself.

**Top frame is test code** — `-0.30` app-bug (blocking, not consumed by infra): if the failure
originates in the test method itself rather than production code, the defect is more likely in the
test, not the system under test — a different category of problem (test-automation, a future rule)
entirely, hence a hard veto rather than a partial deduction. Says nothing about infrastructure
health either way, so infra doesn't consume it.

**Failure cluster present** — `+0.25` infra (not consumed by app-bug): several tests failing in
the same execution window is a classic signature of a shared, external cause (a degraded
dependency, a network blip) — but per `FAILURE_CLUSTER_PRESENT`'s own definition, it only proves
other failures existed, not that they share a cause, which is why it's supporting rather than
determinative on its own. A cluster says nothing about whether *this specific test's* logic has a
bug, so app-bug doesn't consume it.

**Change-set evidence missing** — `-0.15` app-bug (not consumed by infra): this rule's entire
claim is "this specific change caused this specific failure," so its confidence should erode when
there's no visibility into what changed at all. Infrastructure makes no such claim about a specific
change, so the absence of that evidence isn't evidence for or against it — treating "we don't know
what changed" as support for infrastructure would be fabricating a signal from an absence of data
(same principle applied in the presentation layer's refusal to invent an owner or time estimate).

**Existing classification = `APPLICATION_BUG`** — `+0.15` app-bug / `-0.25` infra: the
single-event deterministic classifier already reached this conclusion independently, via its own
YAML-authored rule matching — corroboration from an earlier, separately-validated mechanism. For
infrastructure, an independent classifier already confidently calling it a bug is real (if not
conclusive) evidence pointing away.

**Existing classification = `INFRASTRUCTURE_FAILURE`** — `+0.40` infra (not consumed by app-bug,
see the asymmetry note above): the strongest single contribution in either rule, because it reuses
an entirely separate, already-validated mechanism (e.g. the classifier's own "Connection refused"
pattern rule) rather than deriving anything new.

## Blocking contradictions (hard veto, independent of the arithmetic sum)

- **`ApplicationBugCorrelationRule`**: `RETRY_PASSED` and `TOP_FRAME_IS_TEST_CODE` are both
  blocking — either one forces `NEEDS_INVESTIGATION` regardless of the confidence number.
- **`InfrastructureFailureRule`**: no blocking contradictions. A changed production file matching
  the stack trace makes application-bug *more plausible*, but doesn't make infrastructure
  *impossible* — there's no domain invariant strong enough to justify a hard veto here, only a
  score reduction. Resist adding one without being able to state that invariant explicitly (per
  review discussion, 2026-07-21) — "this evidence makes X less likely" is not the same claim as
  "this evidence makes X impossible."

## Eligibility gates (rule fires at all vs. returns `Optional.empty()`)

- **`ApplicationBugCorrelationRule`**: fires whenever *any* contribution applies — no separate
  eligibility check beyond "did at least one signal fire."
- **`InfrastructureFailureRule`**: requires `classifiedAsInfrastructure || failureClusterPresent`
  before evaluating anything else. A single passing retry, alone, must never suggest
  infrastructure — that gate exists specifically to keep a future flaky-test rule's territory
  from being claimed by this one. Eligibility currently lives inline at the top of `evaluate()`;
  fine while there are two rules; revisit extracting a separate `isApplicable(...)` step only if a
  third or fourth rule's eligibility logic becomes noticeably more complex than this one's single
  boolean OR (flagged in review, not yet a concrete need).

## Assessment-level thresholds (apply to every rule, not rule-specific)

```
CONFIDENCE_THRESHOLD    = 0.70   // AssessmentSelector
MINIMUM_HYPOTHESIS_LEAD = 0.15   // AssessmentSelector — top hypothesis must lead the
                                 // second-ranked one by at least this much to be selected
```

## Adding a new rule to this matrix

When a third rule (test-automation, flaky-test, deployment, dependency — per `07-roadmap.md`)
lands, add its column here in the same commit that adds the rule. If a weight or blocking flag
listed here stops matching the actual `static final` constant in code, that's a bug in this doc,
not in the code.
