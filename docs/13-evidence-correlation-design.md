# Evidence Correlation and Root-Cause Assessment — Design (v0.1, proposed)

**Status: proposed, not approved, not committed, not pushed.** No production code exists yet.
First-round review (2026-07-21) preliminarily approved five of six proposed decisions and
corrected one (`NEEDS_INVESTIGATION`, §13) — incorporated below. Still pending: full-document
review before final design approval, and a separate decision on whether/when to commit this file
to the public repo at all (disclosure-timing question, not a technical one — see
`axiom-invention/08-public-disclosures.md`, kept outside this repo). Short reference once built:
`02-system-architecture.md`'s Module Reference section.

## 1. Context and current v0.1.0 state

Axiom today (tagged `v0.1.0`, verified end to end including a live Claude API call — see
`05-ai-analyzer.md`, `07-roadmap.md`) does exactly one thing per failure: parse it, run it through
a YAML-authored rule engine, and optionally ask Claude to explain the resulting classification. One
`FailureEvent` in, one `ClassificationResult` out, one optional `AiExplanation` alongside it. The
rule engine only ever looks at the failure itself — never at what changed in the code, never at
whether the test is flaky, never at what else failed around it.

## 2. Problem statement

Engineers investigating a CI failure routinely triangulate across several disconnected sources:
the test report, the stack trace, recent commits, whether a retry passed, and whether other tests
failed at the same time. They do this manually to decide whether the failure is likely an
application regression, a test defect, infrastructure instability, or noise (a flaky test) — and
today's Axiom only ever sees the first of those sources. A classifier that reasons from the failure
message alone will plateau well before it can answer "is this actually a regression," because that
question depends on evidence the message doesn't contain.

## 3. Goals

- Normalize evidence from three independent sources: the test failure itself, a supplied source
  change (git diff), and execution context (retries, neighboring failures).
- Produce one or more ranked root-cause hypotheses, each carrying its supporting evidence,
  contradicting evidence, and a deterministic confidence score with a visible per-rule breakdown.
- Abstain explicitly (rather than force a low-confidence guess) when evidence is insufficient or
  conflicting.
- Keep the LLM outside the decision path — it may explain a completed deterministic assessment, and
  may not change its category, confidence, evidence, or ranking. Same principle as ADR-0001,
  extended to a second deterministic engine.
- Stay testable without network access — every adapter, extractor, and rule must be independently
  unit-testable against fixtures, no live services required.

## 4. Non-goals (v0.1 of this engine)

- No GitHub/Jira/Kubernetes/ReportPortal connectors — all three evidence sources are local files
  supplied by the caller.
- No historical failure database or cross-run persistence.
- No autonomous rule learning or weight tuning from feedback.
- No new `LLMProvider` implementation — reuses `ClaudeProvider`/`LLMProvider` from
  `axiom-analyzer` once AI explanation is wired in (deliberately last, not first).
- No production code in this step — this document, reviewed and approved, is the only deliverable
  right now.

## 5. Proposed module placement

**New module: `axiom-correlation`.** Reasoning, after inspecting the existing five modules rather
than assuming:

- Every existing capability boundary (parse, classify, orchestrate, present) is already its own
  Gradle module. Correlation is a genuinely new capability — it consumes classification output as
  one input among three, so it sits beside `axiom-analyzer`, not inside it.
- **Naming collision, found during inspection, not anticipated in the original proposal**:
  `com.axiom.classifier.model.Evidence` already exists — it means "which rule condition matched
  and why" (field/operator/expected/actual/explanation), attached to a `RuleMatch`/
  `ClassificationResult`. The proposal's `Evidence` sealed interface means something entirely
  different: a raw, normalized *input observation* from an external source, collected before any
  rule runs. Reusing the name `Evidence` for both would put two unrelated types one import away
  from each other in any file that needs both (likely `axiom-cli` eventually). **Recommendation:
  name the new sealed interface `CorrelationEvidence`, not `Evidence`** — see open question in
  §18 if you'd rather resolve this a different way (e.g. renaming the classifier's `Evidence`
  instead, which is a wider blast radius since it's already public API with tests depending on it).
- A new module keeps this large domain addition (comparable in size to `axiom-classifier` itself)
  from bloating `axiom-analyzer`, which is already "orchestration + AI explanation" — a third,
  unrelated responsibility doesn't belong there.

Dependency shape, following the existing pattern exactly:
```
axiom-correlation
  implementation project(':axiom-common')      // FailureEvent, PipelineContext
  implementation project(':axiom-classifier')  // FailureCategory, ClassificationResult (reused, see §13)
  implementation project(':axiom-analyzer')    // AnalyzedFailure — see §6's TestFailureEvidence.from(...)
  implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.1'  // --diff/--execution JSON, same stack as elsewhere
```
`axiom-cli` would depend on `axiom-correlation` the same way it depends on `axiom-analyzer` today.
**Correction from an earlier draft of this section**: `axiom-correlation` does need
`axiom-analyzer`, not just `axiom-common`/`axiom-classifier` — `TestFailureEvidence` is built from
an `AnalyzedFailure` (§6), not a separately-supplied `FailureEvent`/`ClassificationResult` pair, so
that dependency is required, not optional. It still does **not** depend on `axiom-parser` — parsing
happens upstream, before either `axiom-analyzer` or `axiom-correlation` sees the failure.

**Dependency direction, confirmed against the actual build files (not assumed):**
`axiom-common/build.gradle` declares no `project(...)` dependency at all — it depends only on
Jackson. `axiom-classifier/build.gradle` declares `implementation project(':axiom-common')`. So
the direction is `axiom-classifier -> axiom-common`, never the reverse; `axiom-correlation`
depending on both is additive and doesn't change that direction. Equally important: **no existing
module gains a dependency on `axiom-correlation`.** `axiom-common`, `axiom-classifier`,
`axiom-parser`, and `axiom-analyzer` are all unmodified by this milestone; only `axiom-cli`'s
`build.gradle` gains one new line, the same way it gained `axiom-analyzer` earlier.

**Scope discipline within the new module**: `axiom-correlation` owns domain types, signal
extraction, deterministic rules, scoring, ranking, and abstention. It does **not** own CLI argument
parsing or AI explanation — those stay in `axiom-cli` and `axiom-analyzer` respectively, wired
together at the composition root exactly as `--ai` is today. Input adapters (§8) are the one piece
of file/JSON handling that lives inside this module, kept deliberately thin and separate from the
engine itself (see §8's note on `CorrelationEngine` never seeing a file path).

Proposed package layout, mirroring `axiom-classifier`'s `.model`/`.rule`/`.engine` split:
```
com.axiom.correlation.model    — CorrelationEvidence + variants, EvidenceReference,
                                  ConfidenceContribution, RootCauseHypothesis, RootCauseAssessment,
                                  AssessmentDisposition
com.axiom.correlation.adapter  — ChangeSetInput/ExecutionInput (wire-format DTOs) + the three
                                  evidence adapters; the only place file/JSON I/O happens
com.axiom.correlation.signal   — SignalExtractor + concrete extractors
com.axiom.correlation.engine   — CorrelationRule, CorrelationContext, CorrelationEngine,
                                  HypothesisScorer, AssessmentSelector
```

## 6. Domain model (proposed shapes, for review — not final)

```java
public sealed interface CorrelationEvidence permits
        TestFailureEvidence, SourceChangeEvidence, ExecutionEvidence {
    String evidenceId();
    EvidenceType type();
    Instant observedAt();
}
```

**Deviation from the original proposal, found during inspection**: `TestFailureEvidence` should
not re-derive `testName`/`exceptionType`/`message`/`stackFrames` as new fields. `FailureEvent`
(already validated, already the whole pipeline's normalized model) has `testName`, `message`,
`stackTrace` today. Flattening those into a second, parallel record risks the two drifting out of
sync — the same failure described two different ways depending which type you're looking at.
Proposed instead:

```java
public record TestFailureEvidence(
        String evidenceId,
        Instant observedAt,
        FailureEvent failureEvent,
        ClassificationResult classification
) implements CorrelationEvidence {
    @Override public EvidenceType type() { return EvidenceType.TEST_FAILURE; }
}
```
**Correspondence between `failureEvent` and `classification`, addressed structurally rather than
by runtime validation**: `ClassificationResult` carries no back-reference to a `FailureEvent.id`
today, so a compact-constructor check inside `TestFailureEvidence` has nothing to validate against
— there's no shared identifier to compare. Adding one would mean changing
`axiom-classifier`'s public `ClassificationResult`, a wider change than this milestone should make
unilaterally. Instead, **`TestFailureEvidence` should only ever be constructed from an existing
`AnalyzedFailure`** (`axiom-analyzer`'s `event`+`classification` pair, already correctly matched by
`DeterministicAnalyzer` at the point they were produced together):
```java
public static TestFailureEvidence from(String evidenceId, Instant observedAt, AnalyzedFailure failure) {
    return new TestFailureEvidence(evidenceId, observedAt, failure.event(), failure.classification());
}
```
This makes mismatching them structurally impossible at the correlation engine's boundary, rather
than something a runtime check has to catch — the pairing was already guaranteed one layer
upstream. (This does mean `axiom-correlation` needs `axiom-analyzer` as a dependency after all,
for `AnalyzedFailure` — noted as a correction to the dependency list above.)

`stackFrames` as a `List<String>` (rather than `FailureEvent.stackTrace`'s single blob) becomes a
`SignalExtractor`-internal concern (splitting on newlines) rather than a stored field — derived
data belongs at the point it's used, not duplicated into the evidence model.

```java
public record SourceChangeEvidence(
        String evidenceId,
        Instant observedAt,
        String commitSha,
        List<String> changedFiles,
        Set<String> affectedModules
) implements CorrelationEvidence {
    @Override public EvidenceType type() { return EvidenceType.SOURCE_CHANGE; }
}
```
This is the **domain** evidence type — distinct from the raw wire-format DTO it's built from (see
§7's `ChangeSetInput`), the same split `axiom-classifier` already has between `RuleDefinition`
(as-authored YAML shape) and `PreparedRule` (processed domain shape).

**Note**: `PipelineContext.commitSha()` already exists on `FailureEvent` when pipeline context is
present. If both are supplied and disagree, that's itself a signal worth surfacing (stale diff
relative to the failure's actual commit) — flagged as an open question in §18, not decided here.

```java
public record ExecutionEvidence(
        String evidenceId,
        Instant observedAt,
        boolean retryPassed,
        List<String> neighboringFailureTestNames,
        String buildStageStatus,
        Double durationAnomalyScore
) implements CorrelationEvidence {
    @Override public EvidenceType type() { return EvidenceType.EXECUTION; }
}
```

```java
public enum EvidenceType { TEST_FAILURE, SOURCE_CHANGE, EXECUTION }

public record EvidenceReference(String evidenceId, String excerpt) {}

public record ConfidenceContribution(
        String ruleId, double weight, String reason, List<String> evidenceIds
) {}

public record RootCauseHypothesis(
        FailureCategory category,          // reused as-is, no new constant added — see §13 correction
        double confidence,
        List<EvidenceReference> supportingEvidence,
        List<EvidenceReference> contradictingEvidence,
        List<ConfidenceContribution> contributions,
        String matchedReasoningPath
) {}
```

**Correction from first-round review**: `NEEDS_INVESTIGATION` must not be a `FailureCategory`
value, and `RootCauseHypothesis.category` never holds it. `NEEDS_INVESTIGATION` is an *assessment
outcome* (abstention), not a root-cause category — a hypothesis is always a real, determined
category candidate; whether the overall assessment *accepts* one is a separate question, answered
by a disposition:

```java
public enum AssessmentDisposition { DETERMINED, NEEDS_INVESTIGATION }

public record RootCauseAssessment(
        List<RootCauseHypothesis> rankedHypotheses,   // every hypothesis considered, always a real FailureCategory
        AssessmentDisposition disposition,
        Optional<FailureCategory> selectedCategory,   // present iff disposition == DETERMINED
        List<String> missingEvidence
) {
    public RootCauseAssessment {
        if (disposition == AssessmentDisposition.DETERMINED && selectedCategory.isEmpty()) {
            throw new IllegalArgumentException("DETERMINED assessment must carry a selectedCategory");
        }
        if (disposition == AssessmentDisposition.NEEDS_INVESTIGATION && selectedCategory.isPresent()) {
            throw new IllegalArgumentException("NEEDS_INVESTIGATION assessment must not carry a selectedCategory");
        }
    }
}
```
This mirrors `AnalyzedFailure.explanation` being `Optional`, not overloading one field to mean
several states — `disposition` says *whether* a category was selected, `selectedCategory` carries
*which one*, and the compact constructor makes the two fields impossible to contradict each other.

## 7. Input formats

`--report` reuses the existing JUnit XML path unchanged (`JUnitXmlParser` -> `FailureEvent`).

`--diff` and `--execution`: the original proposal describes "a supplied Git diff" and an execution
JSON file without specifying either precisely. **Recommendation: keep both as simple,
Jackson-deserializable JSON for v0.1, not raw unified-diff text.** The first correlation rule
(§9, application regression) only needs file-level granularity — "did this file change" — not
hunk/line-level detail. Parsing real unified diff syntax is a nontrivial problem on its own and
buys nothing for the first implementation slice.

**Naming correction from first-round review**: the JSON deserialization target is not itself the
domain evidence type, and shouldn't be named after Git specifically — the correlation engine
should stay independent of any one VCS, with a future adapter able to convert `git diff
--name-only` output, a GitHub API diff response, or another VCS's equivalent into the same shape.
Two distinct types per source, matching `axiom-classifier`'s `RuleDefinition` (wire shape) ->
`PreparedRule` (domain shape) split:

```java
// Wire-format DTO — exactly what changes.json deserializes into. Lives in .adapter.
public record ChangeSetInput(String commitSha, List<String> changedFiles) {}

// Wire-format DTO — exactly what execution.json deserializes into. Lives in .adapter.
public record ExecutionInput(
        boolean retryPassed, List<String> neighboringFailureTestNames,
        String buildStageStatus, Double durationAnomalyScore
) {}
```
```json
// changes.json (--diff)
{ "commitSha": "abc123", "changedFiles": ["src/main/java/com/example/LoginService.java"] }
```
```json
// execution.json (--execution)
{
  "retryPassed": false,
  "neighboringFailureTestNames": [],
  "buildStageStatus": "test",
  "durationAnomalyScore": null
}
```
`SourceChangeEvidenceAdapter` deserializes `changes.json` into a `ChangeSetInput`, then builds the
domain `SourceChangeEvidence` from it (adding `evidenceId`/`observedAt`, and deriving
`affectedModules` from `changedFiles` by a module-path convention — exact convention left as an
implementation detail, not a design decision this document needs to fix). Same pattern for
`ExecutionInput` -> `ExecutionEvidence`.

Real unified-diff parsing (hunks, line ranges, rename detection) is deferred until a concrete rule
needs line-level precision — same reasoning ADR-0006 already applied to deferring a shared parser
base class until TestNG existed.

## 8. Evidence normalization

One adapter per source, converting source-specific input into the common `CorrelationEvidence`
model: a `TestFailureEvidenceAdapter` (wraps the existing `FailureEvent`/`ClassificationResult`,
no new parsing), a `SourceChangeEvidenceAdapter` (deserializes `changes.json`), and an
`ExecutionEvidenceAdapter` (deserializes `execution.json`). All three are pure, small, and testable
in isolation with no shared base class — mirrors `RuleSource`'s single-implementation-today
approach rather than introducing an `EvidenceAdapter` interface before a second implementation of
any one of them exists.

## 9. Signal extraction

```java
public interface SignalExtractor {
    List<Signal> extract(List<CorrelationEvidence> evidence);
}

public enum SignalType {
    STACK_FRAME_MATCHES_CHANGED_FILE,
    RETRY_PASSED,
    TOP_FRAME_IS_TEST_CODE,
    MULTIPLE_UNRELATED_TESTS_FAILED
}

public record Signal(SignalType type, boolean present, List<String> evidenceIds) {}
```
Four concrete extractors for v0.1, one per `SignalType` above — matching exactly the instruction's
scope, not the full five-rule signal set from the original proposal (that needs more signal types
than these four; deferred alongside the four remaining hypothesis categories, see §19).

## 10. Correlation rule evaluation

**Deviation from the original proposal, worth surfacing explicitly**: the classifier's rules are
YAML-authored (`RuleSource`/`RuleProcessor`) because failure categories and matching conditions are
inherently team-specific and meant to be edited without recompiling. The five correlation reasoning
paths, by contrast, involve cross-evidence-type boolean logic and weighted scoring that don't map
cleanly onto the classifier's `field`/`operator`/`value` condition shape — expressing "stack frame
matches a changed file AND failure reproduces on retry AND exception is not infrastructure-related"
declaratively would need a materially bigger DSL than today's rule YAML. **Recommendation: implement
correlation rules as Java code for v0.1**, not YAML, and revisit externalizing them only if a
concrete need for non-recompiled customization shows up (flagged in §18, not decided here).

```java
public record CorrelationContext(List<Signal> signals, List<CorrelationEvidence> evidence) {}

public interface CorrelationRule {
    String id();   // stable identifier, same reasoning RuleDefinition/PreparedRule already carry one —
                   // referenced by ConfidenceContribution.ruleId and by rule-specific test names
    Optional<RuleEvaluation> evaluate(CorrelationContext context);
}

public record RuleEvaluation(
        FailureCategory category, List<ConfidenceContribution> contributions,
        List<EvidenceReference> supporting, List<EvidenceReference> contradicting
) {}

public final class CorrelationEngine {
    // Evaluates every registered CorrelationRule (mirrors RuleEngine: evaluate all, never
    // pick a winner itself — that's AssessmentSelector's job, same split as
    // RuleEngine/ClassificationStrategy in axiom-classifier). Takes normalized CorrelationContext
    // only — never a file path or raw JSON; adapters (§8) run strictly before this point.
    public List<RuleEvaluation> evaluate(CorrelationContext context) { ... }
}
```
Each `CorrelationRule` is independently constructible and testable in isolation — a unit test can
build a `CorrelationContext` directly (no adapters, no files) and assert on one rule's
`RuleEvaluation` without the rest of the engine involved, the same isolation `PreparedRule`/
`RuleEngine` tests already have.

## 11. Confidence calculation

Deterministic, additive, per the original proposal: each matched rule contributes one or more
signed `ConfidenceContribution`s (supporting evidence adds, contradicting evidence subtracts,
missing required evidence penalizes), summed and clamped to `[0.0, 1.0]` — the same clamp
`ClassificationResult.confidence` already enforces, so both engines share one invariant. Every
contribution is retained on the resulting `RootCauseHypothesis`, not just the final number, so the
assessment can show exactly how it arrived at that score (§14's explainability requirement).

## 12. Contradiction handling

Contradicting evidence is never silently netted away — it reduces confidence *and* is recorded
verbatim in `RootCauseHypothesis.contradictingEvidence`, so a hypothesis with high supporting
evidence but one strong contradiction is visibly weaker, not just numerically lower. A hypothesis
whose contradicting evidence outweighs its support is simply a low-confidence hypothesis; it still
appears in `rankedHypotheses` (for transparency) even when the overall assessment's `disposition`
ends up `NEEDS_INVESTIGATION` and no category is selected.

## 13. Hypothesis ranking, abstention, and compatibility with the existing classifier

**The most important finding from inspecting the existing code**: `FailureCategory` already has
`APPLICATION_BUG`, `TEST_AUTOMATION_BUG`, `INFRASTRUCTURE_FAILURE`, `FLAKY_TEST`, and `UNKNOWN` —
nearly the exact taxonomy the original proposal wanted to introduce as a brand-new
`RootCauseCategory` enum (`APPLICATION_REGRESSION`, `TEST_AUTOMATION_DEFECT`,
`INFRASTRUCTURE_FAILURE`, `FLAKY_TEST`, `NEEDS_INVESTIGATION`). Two nearly-identical enums with
different names for the same concepts is exactly the kind of confusing duplication this project
has avoided elsewhere (see the removed `ConditionMatch`, the deferred `Parser` rename backlog
item). **Recommendation, corrected after first-round review: reuse
`com.axiom.classifier.model.FailureCategory` as-is, with no new constant added.**
`RootCauseHypothesis.category` is always one of the existing, real categories — a hypothesis is a
candidate root cause, never "needs investigation," which is not a cause at all but an admission
that no cause was determined. That distinction lives in `AssessmentDisposition` (§6), not in
`FailureCategory` — mixing an abstention outcome into a failure-cause taxonomy would let a caller
end up asking "what does `FailureCategory.NEEDS_INVESTIGATION` even mean as a cause?", which has no
good answer. Both engines still answer the same question — "what kind of failure is this" — and
still share one vocabulary for it; only the *decision to accept an answer at all* gets its own
type. If you'd rather keep hypothesis categories as a fully separate enum from `FailureCategory`
regardless, that's still a real alternative — recorded as an open question in §18, not decided
unilaterally here.

Selection logic, restated against the corrected `RootCauseAssessment` shape (§6):
```
Select the top-ranked hypothesis's category when:
  confidence >= 0.70
  AND lead over second-ranked hypothesis >= 0.15
  AND no blocking contradiction is present
  -> disposition = DETERMINED, selectedCategory = Optional.of(topHypothesis.category())
Otherwise:
  -> disposition = NEEDS_INVESTIGATION, selectedCategory = Optional.empty()
```
`rankedHypotheses` is populated either way — abstaining means no category is *selected*, not that
no hypotheses were considered; the ranked list plus `missingEvidence` is exactly what lets an
engineer see *why* the engine abstained. Thresholds are named constants, not hardcoded inline, so
they can be tuned from benchmark results without touching selection logic — configurable later if
a concrete need for runtime tuning shows up, hardcoded constants for v0.1 (same "don't build config
surface before it's needed" reasoning applied everywhere else in this codebase).

**Second finding: this does not need `Analyzer`/`AnalysisRequest` (ADR-0008) after all.**
ADR-0008 anticipated a second ingestion source eventually forcing `Analyzer.analyze(InputStream)`
to grow into `Analyzer.analyze(AnalysisRequest)`. This milestone is arguably that second source —
but the shape mismatch (one `InputStream` vs. three separate file inputs) is wide enough that
forcing it through the existing `Analyzer` interface would mean either bloating that interface
with correlation-specific parameters, or introducing a generic-enough `AnalysisRequest` that ADR-
0008 itself warns against guessing at prematurely. **Recommendation: `axiom-correlation` gets its
own entry point, entirely separate from `Analyzer`** (see §14) — it doesn't replace or extend the
existing deterministic-classify-then-explain pipeline, it sits beside it. ADR-0008 stays exactly as
written (still "Proposed," still waiting for a source that actually needs a shared shape) — this
isn't that source, since it doesn't touch `Analyzer` at all.

## 14. CLI proposal

New subcommand, not a modification of the existing `axiom [--ai] <rules.yaml> <report.xml>`:
```bash
axiom investigate --rules rules.yaml --report report.xml --diff changes.json --execution execution.json [--ai]
```
`--rules`/`--report` remain required (the correlation engine still needs a `ClassificationResult`
per failure as one of its three evidence inputs); `--diff`/`--execution` are each optional —
missing one just means that evidence source contributes nothing, reflected honestly in
`RootCauseAssessment.missingEvidence`, not an error. Exit codes follow the existing convention
(`0` = assessment completed regardless of outcome, including a `NEEDS_INVESTIGATION` disposition;
`1` = execution failure; `2` = bad usage) — same reasoning as the existing `axiom` command:
abstaining honestly is a correct outcome, not a tool failure.

## 15. Security and privacy

- No full source file contents are ever sent to an LLM — `SourceChangeEvidence` carries file paths
  and module names only, never file contents, matching the original proposal's constraint.
- The existing `--ai` opt-in convention extends unchanged: no AI processing happens unless
  explicitly requested, same as today's `axiom-cli`.
- Redaction of recognized secret patterns (API keys, tokens) from stack traces/diff content before
  any prompt construction — deferred to the point AI explanation is actually wired into this engine
  (§19, last slice), since no prompt exists yet to redact from.

## 16. Testing and benchmark strategy

Mirrors the existing project convention (fixture-based tests, golden end-to-end cases — see
`AxiomCliTest`'s resource fixtures, `PromptBuilderTest`'s golden-prompt tests): unit tests per
evidence adapter, per signal extractor, per correlation rule, confidence-scoring tests,
contradiction tests, abstention tests, and a repeatability test (same input twice, identical
output — same discipline as `JUnitXmlParserTest.parsingTheSameDocumentTwiceProducesIdenticalIds`).
Named benchmark fixtures for v0.1's single implemented rule: application regression (disposition
`DETERMINED`, `selectedCategory` = `APPLICATION_BUG`), missing diff (disposition
`NEEDS_INVESTIGATION`, `missingEvidence` non-empty), and a conflicting-evidence case (contradicting
evidence present, should not force a `DETERMINED` disposition). The remaining
benchmark scenarios from the original proposal (test automation defect, infrastructure outage,
retry-pass flake, malformed execution metadata, several failures sharing one cause) apply once
their corresponding rules exist (§19) — not written speculatively against rules that don't exist
yet.

## 17. Alternatives considered

- **Extend `Analyzer`/build `AnalysisRequest` now** — rejected for v1; ADR-0008 already argues
  against guessing that shape before a concrete need, and §13 above concludes this milestone's
  input shape is different enough to justify its own entry point rather than resolving ADR-0008.
- **New `RootCauseCategory` enum, fully separate from `FailureCategory`** — considered, not
  recommended (§13); rejected on first-round review in favor of reusing `FailureCategory` directly,
  with abstention modeled separately via `AssessmentDisposition` rather than folded into either
  enum.
- **YAML-authored correlation rules, mirroring `axiom-classifier`** — rejected for v0.1 (§10); the
  rule shape needed (weighted, cross-evidence-type, boolean-combination logic) doesn't fit the
  classifier's declarative condition model without a materially bigger DSL.
- **Full unified-diff parsing (hunks/line numbers) now** — rejected for v0.1 (§7); no rule in this
  slice needs line-level granularity.
- **LLM-driven correlation instead of deterministic rules** — rejected outright, not just for v0.1;
  directly violates the "deterministic decides, AI only explains" principle (ADR-0001) this whole
  project is built on.

## 18. Open questions (not resolved by this document)

**Resolved by first-round review, kept here only as a record**: `CorrelationEvidence` (§5) and
reusing `FailureCategory` for hypothesis categories (§13) are both approved; the remaining
questions below are still genuinely open.

- If both `SourceChangeEvidence.commitSha` and `FailureEvent.pipelineContext().commitSha()` are
  present and disagree, is that itself a signal worth extracting (stale/mismatched diff), or out
  of scope for v0.1? (§6)
- How should evidence age affect confidence? (carried over from the original proposal, unresolved)
- How should multiple failures sharing one cause be represented — one assessment referencing many
  failures, or per-failure assessments a caller correlates afterward? (carried over, unresolved)
- Should correlation rule weights ever become externally configurable (YAML, matching the
  classifier), and if so, at what point does that need actually arise? (§10)

## 19. Incremental implementation plan

Explicitly one complete vertical slice first, not all five hypothesis categories at once — same
"vertical milestone" discipline as ADR-0006:

```
1. CorrelationEvidence model (all three variants) + EvidenceReference + ConfidenceContribution +
   AssessmentDisposition + RootCauseAssessment
2. Four signal extractors listed in §9
3. One correlation rule: APPLICATION_BUG
   (stack frame matches changed file AND retry failed AND category isn't already INFRASTRUCTURE_FAILURE)
4. NEEDS_INVESTIGATION abstention path (AssessmentSelector sets disposition, never a hypothesis's category)
5. Golden fixture tests for both outcomes above
```
Everything else — the remaining four hypothesis categories, the `investigate` CLI subcommand
wiring, AI explanation integration — follows only after this one path is implemented, benchmarked,
and reviewed. Per the instruction this document was written against: **no production code changes
happen until this design is explicitly approved.**
