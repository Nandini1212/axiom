# AI-Enhanced Analysis

Status: **Full local AI pipeline implemented and locally verified.** `AIEnhancedAnalyzer`,
`FakeLLMProvider`, `ClaudeProvider`, and `axiom-cli`'s `--ai` flag are all built and tested.
`ClaudeProvider` itself is **implemented and locally verified. Pending live integration testing.**
— that is a deliberately precise distinction, not "done": compiling against the real SDK and
unit-testing its failure-wrapping path (against a guaranteed-unreachable local address) is not the
same claim as a successful live call to Anthropic's API, which has never happened in this
environment (see "ClaudeProvider" below). Reserve "AI flow verified end to end" for after that
live call succeeds — contrast with the deterministic pipeline, which is complete end to end (see
`11-analyzer.md`). The orchestration layer this builds on (`axiom-analyzer`'s
`Analyzer`/`AnalysisResult`/`AnalyzedFailure`, `DeterministicAnalyzer`) was built first; see
`11-analyzer.md` for that.

## Reframing note
`axiom-analyzer` was originally scoped as "the AI module." It's now the orchestration layer first
(`Parser` + classifier -> `AnalysisResult`), fully deterministic, with AI as an *enhancement* to
that same `Analyzer` interface. Keeping one interface true required `AnalyzedFailure` and
`AnalysisResult` to actually grow (see below) — not just conceptually, in code — since AI adds
genuinely new data (explanation, root cause, suggested steps), unlike
`ClassificationStrategy`/`DeterministicStrategy` where the AI variant would return the exact same
result shape.

## What's built

### Types
```java
public record AiExplanation(
    String summary, String rootCause, List<String> suggestedNextSteps, String confidenceExplanation
) {}
```
No `category`, no `confidence` — structurally, the AI cannot override the deterministic
classification even if it tried. `summary`/`rootCause`/`confidenceExplanation` reject blank
strings (a blank "explanation" is equivalent to no explanation); `suggestedNextSteps` may be empty.

```java
public enum AnalyzerWarningType { AI_TIMEOUT, AI_EXPLANATION_FAILED }
public record AnalyzerWarning(AnalyzerWarningType type, String failureEventId, String detail) {}
```
Mirrors `ParserWarning`'s "no silent data loss" principle at this layer — kept as a separate list
from `parserWarnings` on `AnalysisResult` since a parser-level problem and an AI-layer failure are
different kinds of diagnostic. (`AI_NOT_ENABLED` was considered and deliberately not added — there's
no concrete per-failure emission site for it: `DeterministicAnalyzer`'s empty `explanation` isn't
an error, it's simply "wasn't requested," which needs no warning at all.)

```java
public interface LLMProvider {
    AiExplanation explain(FailureEvent event, ClassificationResult classification);
}
```
Implementations own their own request/response mechanics (including building their own prompt,
typically via `PromptBuilder`, and getting structured output from their underlying API) —
`AIEnhancedAnalyzer` never sees provider-specific shapes.

```java
public final class PromptBuilder {
    public String build(FailureEvent event, ClassificationResult classification) { ... }
}
```
A standalone, directly-testable class — not called by `AIEnhancedAnalyzer` at all, since prompt
construction is provider-specific request mechanics, not orchestration. Consumed internally by
`LLMProvider` implementations (a future Claude-backed one). Includes `PipelineContext` (repository,
branch, commit, workflow, job) in the prompt when present on the `FailureEvent`, since the same
failure message means something different on `main` vs. a feature branch. Stack traces are
truncated to 2000 characters for the prompt only — `FailureEvent.stackTrace` itself stays full.
Covered by "golden prompt" tests (exact-string assertions), specifically so an accidental wording
change that silently drops evidence or breaks truncation gets caught.

```java
public final class FakeLLMProvider implements LLMProvider { ... }
```
A test/demo provider with three behaviors (`new FakeLLMProvider(fixedExplanation)`,
`FakeLLMProvider.alwaysTimeout()`, `FakeLLMProvider.alwaysThrows()`) — lets everything except the
real network call be built and tested deterministically.

```java
public final class AIEnhancedAnalyzer implements Analyzer { ... }
```
Wraps a `DeterministicAnalyzer` (composition, not re-orchestration). Computes the full
deterministic `AnalysisResult` first, then attempts one `LLMProvider.explain()` call per failure,
each wrapped in its own single-thread executor with a `Future.get(timeout)` — sequential, not
parallelized (correctness over throughput for v1; same "measure before optimizing" reasoning as
ADR-0007). A timeout or `LlmExplanationException` for one failure leaves that failure's
`explanation` as `Optional.empty()` (unchanged from the deterministic result) plus one
`AnalyzerWarning` — never affects any other failure, and never touches the classification itself.

### `AnalyzedFailure`/`AnalysisResult` — the two records that had to grow
```java
public record AnalyzedFailure(FailureEvent event, ClassificationResult classification, Optional<AiExplanation> explanation) {
    public AnalyzedFailure(FailureEvent event, ClassificationResult classification) { this(event, classification, Optional.empty()); }
}
public record AnalysisResult(List<AnalyzedFailure> analyses, List<ParserWarning> parserWarnings, List<AnalyzerWarning> analyzerWarnings) {
    public AnalysisResult(List<AnalyzedFailure> analyses, List<ParserWarning> parserWarnings) { this(analyses, parserWarnings, List.of()); }
}
```
`explanation` is `Optional<AiExplanation>`, not a nullable field — a bare `null` would have to mean
several different states at once (AI not attempted / timed out / errored); `Optional.empty()`
means only "no explanation attached," and the *why* lives in `AnalyzerWarning` instead. Both
records kept their existing shorter constructors as secondary, delegating constructors, so no
existing call site (`DeterministicAnalyzer`, every pre-existing test) needed to change.

## ClaudeProvider

```java
public final class ClaudeProvider implements LLMProvider {
    public static ClaudeProvider fromEnv() { ... }   // reads ANTHROPIC_API_KEY / an ant auth profile
    @Override
    public AiExplanation explain(FailureEvent event, ClassificationResult classification) { ... }
}
```

Uses the Java SDK's **structured outputs** feature — `outputConfig(AiExplanation.class)` — so
Claude's response deserializes directly into `AiExplanation`, running its own validation
(non-blank summary/rootCause/confidenceExplanation) automatically. No manual JSON parsing.
Model is passed as the plain string `"claude-opus-4-8"`, not the SDK's `Model` enum constant —
the pinned SDK version (`com.anthropic:anthropic-java:2.34.0`) predates that model as a typed
constant, but the API itself accepts the string regardless (Stainless-generated SDKs are
forward-compatible this way — confirmed by reading the SDK jar's actual class members with
`javap`, not by guessing). Catches the common `AnthropicException` base (covers both
`AnthropicServiceException` — HTTP 4xx/5xx — and `AnthropicIoException` — network failure before
any response), wrapping either into `LlmExplanationException` — this call site doesn't
differentiate retry policy, so one catch is correct.

**Status: implemented and locally verified. Pending live integration testing.** No real API call
has been made in this environment — no `ANTHROPIC_API_KEY` and no `ant` CLI were available when
this was built. Verified instead by: (1) compiling against the real SDK (`javap` was used to find
the exact generic types — `StructuredMessage<T>`, `StructuredContentBlock<T>.text()` returning
`Optional<StructuredTextBlock<T>>`, `StructuredTextBlock<T>.text()` returning `T` directly —
rather than guessing signatures), and (2) a unit test that points the client at
`http://127.0.0.1:1` (a reserved, immediately-refused port) to deterministically and quickly
exercise the network-failure -> `LlmExplanationException` path without any real network access.
Authentication against the real API, structured-output correctness against a live model, rate
limits, retries/backoff, and large-stack-trace token-limit behavior are all still unverified —
none of that is implied by "locally verified," and this should not be described as
production-ready until a live call has actually succeeded.

## `axiom-cli`'s `--ai` flag

Built. `axiom [--ai] <rules.yaml> <report.xml>` — `--ai`, when present, must be the first
argument. Config is read only from the environment, never a CLI argument or committed file:
- `AXIOM_LLM_PROVIDER` (optional, defaults to `claude`) — provider-agnostic naming, not
  `ANTHROPIC_API_KEY`, so the abstraction holds all the way through configuration, not just the
  interface. An unsupported value is a fail-fast usage error (exit `1`), not a silent fallback.
- `AXIOM_LLM_API_KEY` (required when `--ai` is passed) — missing key is a fail-fast usage error
  (exit `1`), not a silent fallback to deterministic-only. AI is always an explicit opt-in; it is
  never auto-triggered just because these env vars happen to be set without `--ai`.
- `AXIOM_LLM_TIMEOUT_SECONDS` (optional, defaults to 30) — a non-numeric value is also a fail-fast
  usage error (exit `1`).

When enabled, each `AnalyzedFailure`'s AI explanation (if present) is printed beneath its
deterministic classification: summary, root cause, suggested next steps (if any), and the
confidence explanation. This is the concrete piece needed to run the full pipeline — JUnit XML ->
Parser -> Rule Engine -> Deterministic Classification -> Claude Explanation -> CLI Output — end to
end once real credentials are supplied; that live run is still pending (see above).

## Tests
49 tests across `AiExplanationTest`, `AnalyzerWarningTest`, `PromptBuilderTest`,
`FakeLLMProviderTest`, `AIEnhancedAnalyzerTest`, `ClaudeProviderTest`, `AnalyzedFailureTest`,
`AnalysisResultTest`, plus 5 `--ai`-flag cases in `AxiomCliTest`. Covers: explanation attached
without altering classification, timeout and provider-failure fallback (both leaving
classification untouched), per-failure isolation, a passed-only report, parser warnings still
propagating through the AI-enhanced path, the golden-prompt/pipeline-context/truncation behaviors
in `PromptBuilder`, `ClaudeProvider`'s network-failure wrapping (no real API call), and
`axiom-cli`'s `--ai` flag config-validation paths (missing key, unsupported provider, invalid
timeout, successful construction without triggering a network call).
