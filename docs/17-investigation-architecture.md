# Investigation Architecture — Design (v0.1, proposed)

**Status: proposed, not approved, no production code exists yet.** Answers exactly one question:
**how is an Investigation executed?** Built on top of `16-investigation-domain-model.md` — every
component introduced here exists to support a domain concept already defined there
(`Investigation`, `InvestigationContext`, `EvidenceCollector`, `CorrelationEvidence`, `Signal`,
`CorrelationRule`, `RootCauseAssessment`). No new domain concepts are introduced in this document.
`Recommendation` is deliberately absent from that list — doc 16 §9 left it a partially-built,
undecided product concept, and this document doesn't architect it either. Explicitly not in scope:
how GitHub, GitLab, Jenkins, or any other specific system integrates — those get their own
documents once this orchestration layer exists (§12).

## 1. Purpose

This document describes the execution mechanics of an Investigation: how one is created, how
`InvestigationContext` flows through the system, how `EvidenceCollector`s participate, how
evidence is normalized, how `CorrelationEngine` is invoked, how the result is rendered, where AI
explanation plugs in, and what the extension points are. It does not describe *how GitHub will
work*, *how Jenkins will work*, or any other integration's specifics — those are separate documents
by design (§12), so this one stays readable as Axiom grows.

## 2. Architectural principles

- **`CorrelationEngine` remains unchanged.** This document orchestrates it; it does not replace,
  wrap in new reasoning logic, or duplicate any part of it — the design principle
  `16-investigation-domain-model.md` §7 already established.
- **`EvidenceCollector` is the only extension point for external systems.** No
  `GitHubCollector`/`GitLabCollector`/`JenkinsCollector` or other provider-specific interface is
  defined here — only the generic shape every collector implements (§5).
- **Every component here has a domain reason to exist**, traceable to a concept
  `16-investigation-domain-model.md` already named. If a component doesn't map to something in
  that document, it doesn't belong in this one.
- **Avoid speculative abstractions.** Where no concrete consumer exists today (AI explanation
  integration, §9; new trigger types beyond what's reachable today), the extension point is
  described conceptually, not as a committed interface — matching how this codebase has always
  preferred a working single implementation over an interface with no second implementation yet
  (e.g. `RuleSource`, `EvidenceCollector` itself today).
- **Composition-root wiring, not a plugin system.** Collectors are supplied to the orchestrator at
  construction time, exactly like `CorrelationEngine` already receives its `SignalExtractor`s and
  `CorrelationRule`s as constructor arguments — configuration is code, not a runtime
  discovery/registry mechanism (no concrete need for one exists yet).

## 3. Investigation execution pipeline

```
Caller
  |
InvestigationRunner.run(InvestigationContext)
  |
  |-- for each EvidenceCollector: collect(context) -> CollectedEvidence
  |-- merge all evidence + warnings
  |
CorrelationEngine.assess(evidence)   <-- unchanged, existing method
  |
RootCauseAssessment
  |
Investigation (context + evidence + warnings + assessment, returned to caller)
  |
(caller) AssessmentRenderer.renderSummary/renderDetailed  <-- unchanged, existing, invoked separately
  |
(future) AI explanation decorator                          <-- conceptual only, see §9
```

`InvestigationRunner` is a concrete `final class`, not an interface — same choice `CorrelationEngine`
itself already made (it isn't an interface either). Introducing an interface here without a second
implementation would be exactly the speculative abstraction §2 says to avoid; if a second
implementation appears (§9), that's when an interface gets extracted, not before.

**Collector failure contract**: collectors must convert expected operational failures (a
rate-limited API call, a malformed input file, a timeout) into `CollectedEvidence.warnings()` (§5)
— they must not throw for those cases. An unexpected exception (a programming error) is allowed to
propagate and fail the whole `run(...)` call; the runner does not wrap `collector.collect(...)` in
a try/catch, deliberately, so a real bug surfaces immediately rather than being silently absorbed
as a warning. Concretely:

```text
Test collector succeeds
Git collector times out        -> converts the timeout to a warning, returns what it has (possibly none)
History collector succeeds

-> CorrelationEngine runs with test + history evidence
-> the Git timeout warning is preserved on Investigation.collectionWarnings()
```

**Ordering invariant**: collectors execute in the order they were supplied to `InvestigationRunner`
(composition-root registration order); evidence and warnings preserve that order in
`Investigation.evidence()`/`collectionWarnings()`. Ordering affects presentation only — it must
never affect hypothesis scores or the selected category, since `CorrelationEngine`'s scoring
(`HypothesisScorer`, `AssessmentSelector`) has no dependency on input order today and this document
introduces none. Stated explicitly now so a future move to concurrent collector execution has a
clear invariant to preserve (or an explicit, visible decision to relax).

**Evidence identity across collectors**: each `CorrelationEvidence` already carries an
`evidenceId()`. Two different collectors could plausibly describe the same underlying fact (e.g. a
file-based collector and a future GitHub collector both exposing the same commit). Within one
Investigation, evidence ids must be unique — the runner detects a duplicate id during the merge
step and discards the later occurrence, replacing it with a `CollectionWarning` rather than
silently retaining two conflicting observations under one id. This mirrors `HistoryFileAdapter`'s
existing runId-deduplication behavior (`15-historical-execution-evidence-design.md`) applied one
layer up, not a new pattern.

The first occurrence is retained only to guarantee deterministic behavior, not because it is more
trustworthy. A duplicate evidence id is an integrity warning about the collectors that produced it
(two sources disagreeing about, or redundantly reporting, the same fact) — the retained item is not
more authoritative for having been collected first; it was simply first in registration order (see
the ordering invariant above). Nothing in this document should be read as "earlier collectors win"
as a matter of trust.

```java
public final class InvestigationRunner {

    private final List<EvidenceCollector> collectors;
    private final CorrelationEngine engine;
    private final Supplier<String> investigationIdGenerator;
    private final Clock clock;

    public InvestigationRunner(
            List<EvidenceCollector> collectors,
            CorrelationEngine engine,
            Supplier<String> investigationIdGenerator,
            Clock clock) {
        this.collectors = List.copyOf(collectors);
        this.engine = engine;
        this.investigationIdGenerator = investigationIdGenerator;
        this.clock = clock;
    }

    public Investigation run(InvestigationContext context) {
        List<CorrelationEvidence> evidence = new ArrayList<>();
        List<CollectionWarning> warnings = new ArrayList<>();
        Set<String> seenEvidenceIds = new HashSet<>();

        for (EvidenceCollector collector : collectors) {
            CollectedEvidence collected = collector.collect(context);
            warnings.addAll(collected.warnings());
            for (CorrelationEvidence item : collected.evidence()) {
                if (!seenEvidenceIds.add(item.evidenceId())) {
                    warnings.add(new CollectionWarning(collector.id(),
                        CollectionWarningType.DUPLICATE_EVIDENCE_ID,
                        "Duplicate evidenceId " + item.evidenceId() + ", first occurrence retained"));
                    continue;
                }
                evidence.add(item);
            }
        }

        RootCauseAssessment assessment = engine.assess(evidence);
        return new Investigation(
            investigationIdGenerator.get(), context, clock.instant(), evidence, warnings, assessment);
    }
}
```

Production wiring supplies `() -> UUID.randomUUID().toString()` (a bound method reference on an
already-evaluated `UUID.randomUUID()` would capture one fixed value and return the same string on
every call — the lambda form is used specifically to avoid that) and `Clock.systemUTC()`; tests
supply a fixed string and a fixed `Clock` — the same testability discipline as everywhere else in
Axiom (e.g. `JUnitXmlParserTest`'s deterministic-id assertions), not new ceremony introduced for
this one class.

This mirrors `CorrelationEngine.assess(List<CorrelationEvidence>)` exactly one level up:
collectors/engine/id-generator/clock are configuration supplied once (constructor), `context` is
the only thing that varies per call — the same split `CorrelationEngine` already has between its
constructor (`extractors`, `rules`) and its per-call argument (`evidence`).

```java
public record Investigation(
    String investigationId,
    InvestigationContext context,
    Instant startedAt,
    List<CorrelationEvidence> evidence,
    List<CollectionWarning> collectionWarnings,
    RootCauseAssessment assessment
) {}
```

**Resolving two open questions from `16-investigation-domain-model.md` §13:**
- **`investigationId` is a plain `String`, generated via an injected `Supplier<String>`** (not a
  direct `UUID.randomUUID()` call — see the collector-failure/testability note above). Every other
  domain entity in this codebase already carries a stable id (`CorrelationRule.id()`,
  `TestFailureEvidence.evidenceId()`, `RuleDefinition.id()`) purely so it can be referenced and
  logged — this isn't new machinery, it's the existing convention applied to `Investigation`. No
  persistence layer is implied by this field existing (§12).
- **`startedAt` is an `Instant`, sourced from an injected `Clock`**, same convention
  `CorrelationEvidence.observedAt()` already established on every evidence variant, made
  deterministically testable the same way. Answers doc 16 §9's "some notion of when it happened"
  without inventing new machinery.

## 4. InvestigationContext

```java
public record InvestigationContext(TriggerType triggerType, PipelineContext pipelineContext) {}

public enum TriggerType { PR_BUILD_FAILURE, NIGHTLY_REGRESSION, MANUAL }
```

**Finding, not assumed**: `InvestigationContext`'s identifying fields — repository, branch, commit
SHA, pull request number — already exist as `axiom-common`'s `PipelineContext`
(`03-domain-model.md`). Re-deriving those fields into a new record would duplicate exactly the kind
of information `TestFailureEvidence` was corrected to *not* duplicate from `FailureEvent`
(`13-evidence-correlation-design.md` §6). `InvestigationContext` wraps `PipelineContext` rather than
re-declaring its fields; `TriggerType` is the one genuinely new field doc 16 §4 called for.

**`TriggerType` deliberately excludes `DEPLOYMENT_FAILURE` and any production-alert trigger.** Doc
16 §4 found no evidence source exists for either today — adding the enum constant ahead of its
evidence source would itself be a speculative abstraction (§2). Add the constant when the
corresponding `EvidenceCollector` actually exists, not before.

**What `InvestigationContext` deliberately does *not* carry**: file paths, API endpoints, or any
other collector-specific configuration. A collector that needs a file path or an API token is
constructed with it directly (dependency injection, same as `CorrelationEngine`'s extractors/rules
are configured once, not re-read from the per-call argument) — `InvestigationContext` only carries
facts that identify *what* is being investigated, universally true regardless of which collector
consumes them.

## 5. EvidenceCollector

```java
public interface EvidenceCollector {
    String id();   // stable identifier, same reasoning CorrelationRule.id() already carries one —
                   // referenced by CollectionWarning.collectorId() below
    CollectedEvidence collect(InvestigationContext context);
}

public record CollectedEvidence(List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {}

public record CollectionWarning(String collectorId, CollectionWarningType type, String message) {}

public enum CollectionWarningType { OPERATIONAL_FAILURE, DUPLICATE_EVIDENCE_ID }
```

**Typed warning model, not a plain `String`.** This codebase has already relearned this lesson
twice: `axiom-parser`'s `ParserWarning` was typed (`type`, `detail`) from the start, and
`axiom-correlation`'s historical-evidence work explicitly replaced a raw `String` warning with the
structured `HistoryWarning` record (`15-historical-execution-evidence-design.md`) once it needed to
express *which run* and *what kind* of problem, not just a sentence. `CollectionWarning` applies the
same, already-proven shape one layer up — `collectorId` says which collector, `type` says what kind
of problem (`OPERATIONAL_FAILURE` for the rate-limit/timeout/malformed-input case §3 describes,
`DUPLICATE_EVIDENCE_ID` for the case §3's runner code handles), `message` stays human-readable. This
isn't introduced speculatively — both existing precedents show plain strings get replaced here
anyway once a second dimension (which source, what kind) is needed, so this document starts with
the shape the codebase already converges on rather than repeating the string-first-then-refactor
cycle a third time.

The `*Result`-shaped return (`ParserResult`, `AnalysisResult`, and now `CollectedEvidence`) is the
established project convention (`02-system-architecture.md`'s API Conventions section) — a
collector's primary output is the evidence it gathered, but a partial failure must surface as a
warning, never silently disappear (§3's fail-soft contract).

**Only this interface is defined here.** No `GitHubEvidenceCollector`, `GitLabEvidenceCollector`,
or `JenkinsEvidenceCollector` — those are concrete implementations documented separately, once this
interface is approved and something needs to implement it beyond the one below.

**Today's one implementation, described conceptually rather than fully specified** (a second
speculative-abstraction guard: this document names it, it does not design its internals): a
file-based collector wrapping the adapters that already exist —
`TestFailureEvidence.from(AnalyzedFailure)`, `HistoryFileAdapter`, and the `ChangeSetInput`/
`ExecutionInput` adapters (`13-evidence-correlation-design.md` §8) — behind the `EvidenceCollector`
shape. This is the same "orchestrate, don't rewrite" principle applied one layer down: the existing
adapters keep doing exactly what they do today; only the interface wrapping them is new.

**Distinguishing two different notions of "missing"**, worth stating explicitly since both exist in
this pipeline now: `CollectedEvidence.warnings()` reports operational problems a collector hit while
trying to gather evidence (a timeout, a malformed input file — `CollectionWarningType
.OPERATIONAL_FAILURE`). `RootCauseAssessment.missingEvidence` (computed downstream, inside
`CorrelationEngine.assess`, unchanged) reports which evidence *types* never showed up at all,
regardless of why. A collector reports *what went wrong*; the engine reports *what's absent* —
conflating the two would lose information either side needs.

## 6. Evidence normalization

Normalization is not a new pipeline stage — it already happens inside each collector's existing
adapter (`HistoryFileAdapter` converting `HistoryInput` DTOs into `HistoricalExecutionEvidence` with
its invariants enforced at conversion time, `TestFailureEvidence.from` building from an already-
correct `AnalyzedFailure` pairing). `EvidenceCollector.collect(...)` returns already-normalized
`CorrelationEvidence`; `InvestigationRunner` never sees a wire format, a file path, or raw JSON —
same boundary `CorrelationEngine` itself already enforces (`CorrelationEngine`'s own javadoc: "never
a file path or raw JSON").

**Naming decision, deferred from `16-investigation-domain-model.md` §13**: keep the name
`CorrelationEvidence`, do not rename it to `InvestigationEvidence`. Reasoning: it is already public
API with four variants and dozens of tests depending on it across `axiom-correlation`; a rename here
would be a mechanical exercise of the same shape as the recent `FlakyTestRule` →
`TransientFailureRule` rename, but with a much wider blast radius (every evidence type, every rule,
every extractor, every test). It also still describes exactly what it is — evidence consumed for
correlation — and nothing about `Investigation` changes what that evidence *is*, only who
orchestrates its collection. "Investigation evidence" remains accurate domain vocabulary; it does
not need to become a type name. Flagged here as a recommendation, not a unilateral final call — say
so if you'd rather rename it while the surface area is still comparatively small.

## 7. CorrelationEngine integration

No changes. `InvestigationRunner` holds one `CorrelationEngine` instance, constructed exactly as it
is today (with its configured `SignalExtractor`s and `CorrelationRule`s), and calls
`engine.assess(allCollectedEvidence)` once, after every collector has run. `CorrelationEngine`
itself never becomes aware that an `Investigation` exists — its input is still `List<
CorrelationEvidence>`, its output is still `RootCauseAssessment`, unchanged from
`13-evidence-correlation-design.md` through `15-historical-execution-evidence-design.md`.

## 8. Assessment rendering

Not owned by `InvestigationRunner`. Rendering stays a caller-level concern, exactly as it is today:
`AssessmentRenderer.renderSummary(investigation.assessment(), investigation.evidence())` (or
`renderDetailed`) is called by whatever invokes `InvestigationRunner`, not by
`InvestigationRunner` itself. This keeps the choice of renderer (text, Markdown, and later formats)
independent of investigation execution, the same separation `CorrelationEngine` already has from
`AssessmentRenderer` today — `Investigation` simply carries what a renderer needs
(`assessment()`, `evidence()`), it does not pick a renderer for the caller.

## 9. AI explanation integration

**Conceptual only — no interface is introduced in this document.** The natural shape, if and when
AI explanation is wired into this pipeline, mirrors the existing `Analyzer`/`AIEnhancedAnalyzer`
decorator (`02-system-architecture.md`'s `axiom-analyzer` entry): a wrapper that delegates to
`InvestigationRunner` for the deterministic `Investigation`, then layers an explanation on top
without altering `context`, `evidence`, or `assessment` — same "deterministic decides, AI only
explains" boundary ADR-0001 already enforces everywhere else. Whether that means extracting an
`InvestigationExecutor` interface (so a decorator can implement it alongside `InvestigationRunner`)
or something else entirely is deliberately left unresolved here: per §2, no interface gets
introduced before there's a second implementation that actually needs one. This mirrors
`13-evidence-correlation-design.md`'s own sequencing — AI wired in last, not first — applied to this
pipeline too.

## 10. Extension points

- **New evidence sources**: implement `EvidenceCollector`, add it to the list
  `InvestigationRunner` is constructed with. No change to `InvestigationRunner`,
  `CorrelationEngine`, or any existing collector.
- **New trigger types**: add a `TriggerType` constant once a real evidence source exists for it
  (§4) — additive, no change to `InvestigationContext`'s shape.
- **AI explanation**: a future decorator, per §9 — additive once designed, not built here.
- **Rendering**: already extensible via `AssessmentRenderer` (existing, unchanged) — a new output
  format is a new `AssessmentRenderer` implementation, nothing in this document changes that.

No plugin registry, no runtime discovery, no configuration-driven collector loading — every
extension point above is exercised by writing a new class and wiring it at the composition root, the
same way every existing extension point in Axiom already works (a new `Parser`, a new
`ClassificationStrategy`, a new `CorrelationRule`).

## 11. Sequence diagram

```
Caller           InvestigationRunner      EvidenceCollector(s)      CorrelationEngine
  |                     |                         |                       |
  |-- run(context) ---->|                         |                       |
  |                     |-- collect(context) ---->|  (once per collector) |
  |                     |<-- CollectedEvidence ----|                       |
  |                     |   (repeat per collector, evidence+warnings merged)
  |                     |-- assess(evidence) ------------------------------>|
  |                     |<-- RootCauseAssessment --------------------------|
  |<-- Investigation ---|                         |                       |
  |                     |                         |                       |
  |-- renderer.renderSummary(investigation.assessment(), investigation.evidence())
  |   (caller's own step — AssessmentRenderer, existing, unchanged)
```

## 12. Non-goals

- No `GitHubCollector`, `GitLabCollector`, `JenkinsCollector`, or any provider-specific
  `EvidenceCollector` implementation — future integration documents, starting with
  `06-github-integration.md`'s existing stub.
- No security or permissions model (who may trigger an investigation, what credentials a collector
  needs to hold) — a later document's concern, not resolved here.
- No collector registration, discovery, or plugin mechanism — composition-root wiring only (§2,
  §10).
- No persistence layer for `Investigation` — it carries an id and a timestamp (§3) so it *can* be
  logged or referenced, but no storage mechanism is designed here.
- No concrete AI decorator implementation or interface — conceptual only (§9).
- No changes to `CorrelationEngine`, `CorrelationRule`, `SignalExtractor`, or `AssessmentSelector`
  — all reused exactly as built.
- No `axiom investigate` CLI subcommand wiring — this document describes the library-level
  orchestration; CLI wiring remains a separate, later increment, same status it already had in
  `13-evidence-correlation-design.md` §14.
- No production code changes. Per the same discipline this whole design-doc sequence has followed:
  nothing gets implemented until this document is explicitly reviewed.
