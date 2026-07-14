# Analyzer

Deep-dive for the `axiom-analyzer` module. Short reference: `02-system-architecture.md`'s Module
Reference section. For the future AI-enhancement layer built on top of this, see
`05-ai-analyzer.md`.

## Status: built (orchestration only, no AI)

`axiom-analyzer` ties `axiom-parser` and `axiom-classifier` together into one call. It is the
first module depending on both — that's exactly its job.

## Public API

```java
public interface Analyzer {
    AnalysisResult analyze(InputStream report);
}

public record AnalyzedFailure(FailureEvent event, ClassificationResult classification) {}

public record AnalysisResult(List<AnalyzedFailure> analyses, List<ParserWarning> parserWarnings) {}
```

`DeterministicAnalyzer implements Analyzer`, constructed with an already-built `Parser`,
`RuleEngine`, and `ClassificationStrategy` — it owns none of their construction (no rule loading,
no format dispatch), purely composition of three independently-tested pieces:

```java
public AnalysisResult analyze(InputStream report) {
    ParserResult parsed = parser.parse(report);
    List<AnalyzedFailure> analyses = new ArrayList<>();
    for (FailureEvent event : parsed.failures()) {
        analyses.add(new AnalyzedFailure(event, strategy.classify(ruleEngine.evaluate(event))));
    }
    return new AnalysisResult(analyses, parsed.warnings());
}
```

**Naming**: `DeterministicAnalyzer` is deliberately parallel to `DeterministicStrategy` — a future
`AIEnhancedAnalyzer` (or a decorator wrapping this one) implements the same `Analyzer` interface
and adds LLM explanation on top, exactly like `AIEnhancedStrategy` was always the planned
counterpart to `DeterministicStrategy`. This is the seam that makes AI "an enhancement, not the
core workflow" — the `Analyzer` interface itself doesn't change when that milestone starts.

**`AnalyzedFailure`**, not `FailureAnalysis` — named for the *result* of analysis, not the
process, since it's expected to grow more fields over time (AI explanation, root cause,
suggested fix, confidence adjustment) without needing a second rename.

**No `Classifier` facade** bundling `RuleEngine`+`ClassificationStrategy` — nothing has needed
that bundling until now, and inventing it preemptively would repeat the same mistake
`ConditionMatch` was (see `04-rule-engine.md`).

## Error handling

`ParsingException` (from `axiom-parser`) propagates through `analyze()` unchanged — it's already
the correct exception at the correct level; `Analyzer` doesn't catch or rewrap it.

## The `*Result` convention (now formally adopted)

`ParserResult` (failures + warnings) and `AnalysisResult` (analyses + parserWarnings) share a
deliberate shape: **primary output, plus whatever diagnostics/warnings must not be silently
lost.** This is now the standing convention for every module's result type going forward, not
coincidence — two independent instances of the same pattern is enough evidence to formalize it
(same "wait for a second real case" reasoning used to defer a shared parser base class until
TestNG exists).

## A failure that matches no rule is still an `AnalyzedFailure`

`ClassificationResult`'s `UNKNOWN`/`0.0`/`null`-matchedRuleId case (from `DeterministicStrategy`)
is not filtered out — every parsed failure gets an `AnalyzedFailure`, whether or not any rule
matched it. Silently dropping unmatched failures would be exactly the kind of information loss
the project has consistently rejected at every other layer.

## Tests

17 tests across `DeterministicAnalyzerTest`/`AnalysisResultTest`/`AnalyzedFailureTest`: single and
multiple failures, a passed-only report producing empty results, an unmatched failure still
producing an `UNKNOWN` `AnalyzedFailure`, a parser warning propagating through to
`AnalysisResult.parserWarnings`, warnings staying empty when nothing is malformed (proving the
analyzer doesn't invent warnings), malformed XML propagating `ParsingException` unchanged, plus
construction/validation/defensive-copy tests for both new record types.
