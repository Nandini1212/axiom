# Investigation Domain Model — Design (v0.1, proposed)

**Status: proposed, not approved, no production code exists yet.** Written before the
`Investigation Architecture` document per explicit instruction: define the domain first, let
architecture follow from it, the same way `FailureEvent` (`03-domain-model.md`) was defined before
the parser, rule engine, or CLI existed. This document defines what an Investigation *is*; it does
not define interfaces, connectors, or wire formats — that's `17-investigation-architecture.md`
(not yet written).

## 1. Why domain-first

Every durable part of Axiom so far was driven by a clear domain concept, not a technology choice:
`FailureEvent` before the parser, `Evidence`/`CorrelationEvidence` before the correlation engine's
adapters, `RootCauseAssessment` before `AssessmentSelector`'s ranking logic. The parts that would
have gone wrong if built interface-first are exactly the parts this document exists to pin down:
what an Investigation *is*, what triggers one, and what its lifecycle looks like — before deciding
how `EvidenceCollector` implementations plug into it.

## 2. What is an Investigation?

> An Investigation is a deterministic analysis of an engineering event that collects evidence from
> one or more systems, evaluates hypotheses using deterministic reasoning, produces an explainable
> conclusion, and presents actionable recommendations to help engineers resolve the event.

Same "deterministic decides, AI only explains" principle as everywhere else in Axiom (ADR-0001,
carried forward through the correlation engine's own design) — an Investigation doesn't introduce a
new decision-making philosophy, it names the thing that already happens today (parse → classify →
correlate → assess) plus the evidence sources that don't exist yet. The end goal stays explicit:
not just producing a conclusion, but helping an engineer act on it.

## 3. Scope of an Investigation

An Investigation analyzes **one engineering event**. It may collect evidence from multiple
systems, but it produces exactly one deterministic assessment.

Examples:
- One failed pull request
- One failed deployment
- One failed nightly build

An Investigation is **not** intended to span multiple unrelated engineering events — it is scoped
to the event that triggered it (§4), not to a test, a service, or a repository in general. Whether
several investigations can later be grouped or cross-referenced (e.g. "this test has failed in
three unrelated investigations this month") is a different, unresolved question — see the
historical-evidence carry-over in §13's Architectural Decisions — but that grouping, if it exists,
would be a relationship *between* investigations, not a reason for a single investigation's own
boundary to widen.

## 4. What triggers an Investigation?

| Trigger | Evidence available today | Status |
| --- | --- | --- |
| PR build failure | JUnit report + optional `--diff`/`--execution` files | **in scope today** — this is what `axiom investigate` already models, just not under this name |
| Nightly regression failure | same shape as PR build failure, different trigger metadata (no PR number, a scheduled run instead) | in scope today, same evidence shape |
| Deployment failure | none — no `DeploymentEvidence` source exists | out of scope until a deployment evidence source is built (Phase 2+ per `07-roadmap.md`) |
| Production alert | none — no infrastructure/monitoring evidence source exists | explicitly out of scope — same non-goal as the correlation engine's original design (`13-evidence-correlation-design.md` §4) |
| Manual investigation | any of the above, invoked directly rather than by an automated trigger | in scope today — this is arguably the *only* trigger that exists right now, since there is no automated invocation of `axiom investigate` yet |

**Finding**: today, every "trigger" is really the same thing — a human or a CI job manually running
`axiom investigate` with whatever evidence files happen to be available. Distinguishing trigger
*types* only matters once (a) something invokes Axiom automatically (a GitHub Action, not built
yet) and (b) a rule or renderer actually behaves differently depending on trigger type. Neither is
true today — flagged as a Product Decision (§13), not decided here.

## 5. Investigation lifecycle

```
Trigger
    |
Create Investigation
    |
Collect Evidence
    |
Normalize Evidence
    |
Derive Signals
    |
Execute Rules
    |
Generate Hypotheses
    |
Rank & Aggregate
    |
Render Investigation
    |
AI Explanation
```

**Mapping each stage to what already exists** (found by inspecting `axiom-correlation`, not
assumed):

| Lifecycle stage | Existing component | Status |
| --- | --- | --- |
| Trigger | none — today's entry point is direct CLI invocation | not yet modeled as a first-class concept |
| Create Investigation | none | **new** — no `Investigation` type exists |
| Collect Evidence | ad hoc per-input-type construction: `TestFailureEvidence.from(AnalyzedFailure)`, `HistoryFileAdapter`, and (per `13-evidence-correlation-design.md` §8) adapters for `ChangeSetInput`/`ExecutionInput` | works, but not behind a uniform interface — every evidence source has its own construction path, not a common `EvidenceCollector` |
| Normalize Evidence | the same adapters above already do this — e.g. `HistoryFileAdapter` converts `HistoryInput` DTOs into `HistoricalExecutionEvidence`, enforcing invariants (branch-mismatch handling, runId dedup) at conversion time | **already built**, just not unified under one collector abstraction |
| Derive Signals | `SignalExtractor` / `SignalType` / `Signal` (`com.axiom.correlation.signal`) | **already built** |
| Execute Rules | `CorrelationRule` / `CorrelationEngine` (`com.axiom.correlation.engine`) | **already built** |
| Generate Hypotheses | `RuleEvaluation`, scored into `ScoredEvaluation` via `HypothesisScorer` | **already built** |
| Rank & Aggregate | `AssessmentSelector.select(...)` → `RootCauseAssessment` (includes same-category aggregation, minimum-lead selection, abstention) | **already built** |
| Render Investigation | `AssessmentRenderer` (`TextAssessmentRenderer`, `MarkdownAssessmentRenderer`) via `AssessmentFacts` | **already built** |
| AI Explanation | `LLMProvider`/`ClaudeProvider` exist in `axiom-analyzer`, wired into the *classifier's* explanation path today; not yet wired into `RootCauseAssessment` | not yet built for this pipeline |

See §7 for what this mapping implies about `CorrelationEngine`'s role going forward.

## 6. Current state vs. future state

Making the "extend, don't replace" shape explicit as two pipelines, not just a table:

**Current state:**
```
CLI (file inputs: --report / --diff / --execution)
        |
Evidence adapters (per-source, ad hoc — §5's "Collect Evidence" row)
        |
CorrelationEngine
        |
RootCauseAssessment
        |
AssessmentRenderer
```

**Future state:**
```
Trigger
    |
Investigation (context + identity)
    |
EvidenceCollector(s) — file-based today, GitHub/GitLab/Jenkins later
    |
CorrelationEngine        (unchanged)
    |
RootCauseAssessment      (unchanged)
    |
AssessmentRenderer       (unchanged)
    |
AI Explanation           (new)
```

Everything marked `(unchanged)` is exactly that — the future state adds a coordinating layer in
front of and behind `CorrelationEngine`, it does not touch what `CorrelationEngine` already does.

## 7. Design principle: Investigation does not replace CorrelationEngine

> **Investigation does not replace CorrelationEngine.**
>
> `CorrelationEngine` remains the deterministic reasoning engine — the source of truth for
> signals, rules, hypotheses, and ranking. `Investigation` orchestrates evidence collection before
> it and presentation (and, eventually, AI explanation) after it. `CorrelationEngine.assess(List<
> CorrelationEvidence>)` already implements six of this lifecycle's ten stages (Normalize Evidence
> through Rank & Aggregate) in one method call. **Don't rewrite it — build around it.**

## 8. Guiding principles

- Deterministic reasoning remains the source of truth; AI explains, never decides (ADR-0001).
- Evidence is normalized before rule evaluation — collectors produce `CorrelationEvidence`
  (or its renamed equivalent, §13), never a raw provider payload passed straight to a rule.
- Evidence collectors never contain business rules — a collector fetches and normalizes facts; it
  does not decide what those facts mean.
- Rules never depend on a specific external system — a `CorrelationRule` reasons over `Signal`s
  and `CorrelationEvidence`, never a GitHub/GitLab/Jenkins-specific type directly.
- New evidence sources should be pluggable without modifying `CorrelationEngine`,
  `CorrelationRule` implementations, or `AssessmentSelector`.
- AI explains deterministic conclusions but never determines them.

## 9. Core domain objects — what's genuinely new vs. what already exists

The instinct to list `Investigation`, `InvestigationContext`, `EvidenceCollector`,
`InvestigationEvidence`, `Signal`, `Rule`, `Assessment`, `Recommendation` as if all eight were new
turns out to be wrong once checked against the actual codebase — the same kind of correction
`13-evidence-correlation-design.md` made when it found `FailureCategory` already covered most of a
proposed new `RootCauseCategory` enum.

| Proposed object | Reality | Notes |
| --- | --- | --- |
| `Investigation` | **genuinely new** | the proposed aggregate root discussed in §10 |
| `InvestigationContext` | **genuinely new**, but a close relative of today's three CLI file paths (`--report`/`--diff`/`--execution`) plus trigger metadata (repo, PR number, commit, branch) that nothing currently models as one object | needs a real shape — deferred to the Architecture doc |
| `EvidenceCollector` | **genuinely new abstraction** | today's evidence construction is ad hoc per source (§5's Collect Evidence row) — this would be the interface that unifies it, one implementation per source (file-based today, GitHub/GitLab/Jenkins later) |
| `InvestigationEvidence` | **already exists** as `CorrelationEvidence` (sealed interface, now four variants: `TestFailureEvidence`, `SourceChangeEvidence`, `ExecutionEvidence`, `HistoricalExecutionEvidence`) | naming collision worth resolving deliberately, not by default (see below) |
| `Signal` | **already exists** (`com.axiom.correlation.signal.Signal`) | no change needed |
| `Rule` | **already exists** (`CorrelationRule`) | no change needed |
| `Assessment` | **already exists** (`RootCauseAssessment`, `RootCauseHypothesis`, `AssessmentDisposition`) | no change needed |
| `Recommendation` | **partially exists** — `AssessmentFacts.recommendedAction()` returns a plain `String`, computed in the presentation layer, category-aware since an earlier fix (`recommendedActionForDetermined` was found hardcoded for `APPLICATION_BUG` only, then corrected) | not yet a first-class domain object with its own evidence trail or stable id; whether it needs to become one is a Product Decision (§13), not decided here |

**Naming collision, flagged rather than resolved**: `CorrelationEvidence` already means exactly
what `InvestigationEvidence` would mean — a normalized, source-agnostic evidence observation. Two
options exist: (a) keep the name `CorrelationEvidence` as-is, since it's already public API with
tests depending on it, and treat "investigation evidence" as domain *vocabulary* rather than a type
rename; or (b) rename it now, while only four variants and a handful of call sites exist, before
GitHub/GitLab/Jenkins collectors multiply the blast radius of a later rename. This mirrors the
`Evidence` vs. `CorrelationEvidence` collision `13-evidence-correlation-design.md` §5 already
resolved once — recorded here as an Architectural Decision (§13), your call before the Architecture
document picks one.

## 10. Proposed aggregate root

`Investigation` is not yet an aggregate root — it doesn't exist, nothing references it, whether
investigations are persisted is undecided, and identity hasn't been defined. What can be said now:

> **If Axiom evolves into an Engineering Investigation Platform, `Investigation` is expected to
> become the aggregate root** that coordinates evidence collection, deterministic reasoning, and
> presentation — the thing that has identity and a lifecycle, while evidence, signals, rule
> evaluations, and the assessment remain values computed *within* one investigation, not
> separately identified or persisted.

Concretely, an `Investigation` is expected to hold (shapes deferred to the Architecture doc, listed
here only to motivate the proposal):

- the `InvestigationContext` that started it (why it exists, what triggered it)
- the evidence collected for it (today: `List<CorrelationEvidence>`)
- the resulting `RootCauseAssessment`
- eventually, an AI explanation layered on top, same non-decision-making role AI already has
  elsewhere in Axiom
- some notion of when it happened / how long it took — not required by anything built today, but
  the natural place for it once it exists (see Architectural Decisions, §13)

This would sit a level above `FailureEvent`: `FailureEvent` was the root normalized *input* when
there was exactly one evidence source (a test report). `Investigation`, if adopted, would be the
root when there are several, plural sources feeding one conclusion. Whether that's the right shape
is exactly what `17-investigation-architecture.md` needs to validate, not something this document
should assume settled.

## 11. Responsibilities and non-responsibilities

Following the same Purpose/Responsibilities/Non-responsibilities template
`02-system-architecture.md` already uses per module — describing the proposed `Investigation`
concept's intended scope, not a built module:

- **Responsibilities** — owning investigation identity and the `InvestigationContext` that started
  it; holding the evidence collected for this investigation; delegating to `CorrelationEngine`
  (unchanged) for signal derivation, rule evaluation, and assessment; holding the final rendered
  output and, later, an AI explanation reference.
- **Non-responsibilities** — does not itself extract signals, evaluate rules, or score confidence
  (`CorrelationEngine`'s job, unchanged by this document); does not decide a category
  (`AssessmentSelector`'s job, unchanged); does not talk to GitHub/GitLab/Jenkins directly (a future
  `EvidenceCollector` implementation's job); does not call an LLM directly (`LLMProvider`'s job,
  unchanged). An `Investigation` is a coordinator and a container, not a new reasoning engine
  sitting beside the one that already exists (§7).

## 12. Non-goals of this document

- No Java interfaces or method signatures for `Investigation`, `InvestigationContext`, or
  `EvidenceCollector` — that's `17-investigation-architecture.md`.
- No security/permissions model (who can trigger an investigation, what credentials a collector
  needs) — Architecture document.
- No GitHub/GitLab/Jenkins-specific design — a later document, per the user's own ordering
  (Domain → Architecture → GitHub Integration).
- No resolution of the `CorrelationEvidence`/`InvestigationEvidence` naming question or the
  identity/persistence question (§13) — both flagged, neither decided unilaterally here.
- No production code changes. Per the instruction this document was written against: nothing gets
  implemented until this domain model is explicitly reviewed.

## 13. Open questions (not resolved by this document)

**Architectural decisions** — resolved by `17-investigation-architecture.md`, not this document:
- Rename `CorrelationEvidence` → `InvestigationEvidence` now, or keep the existing name and treat
  "investigation evidence" as vocabulary only? (§9)
- Does `Investigation` need a stable identity/ID scheme now (for future persistence, or for
  representing "one investigation, many related failures" — carried over from
  `13-evidence-correlation-design.md` §18's still-unresolved question), or is that premature before
  any consumer needs it? (§10)

**Product decisions** — depend on which evidence sources/consumers actually get built, not on
architecture:
- What triggers investigations, and does trigger type need any behavioral distinction, or is it
  just a descriptive tag on `InvestigationContext` with no rule/renderer depending on it today?
  (§4)
- What evidence sources come next, and in what order? (tracked in `07-roadmap.md`'s phased plan,
  not repeated here)
- Does `Recommendation` need to become a first-class domain object (stable id, priority, its own
  evidence references) before Phase 4 (PR comments, merge recommendations), or only once a concrete
  consumer needs more structure than today's plain string? (§9)

## 14. Next document

Once this is reviewed: `17-investigation-architecture.md` — `InvestigationContext`'s real shape,
resolution of the `CorrelationEvidence`/`InvestigationEvidence` question, the `EvidenceCollector`
interface and collector pipeline, how collectors register/compose, rule engine integration
(referencing `CorrelationEngine` as-is), AI integration, security, and extensibility. GitHub
integration (`06-github-integration.md` already exists as a stub) becomes the concrete first
`EvidenceCollector` implementation after that.
