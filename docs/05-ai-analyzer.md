# AI-Enhanced Analysis

Status: **`AIEnhancedAnalyzer`, `FakeLLMProvider`, and a real `ClaudeProvider` are all built and
tested.** `ClaudeProvider` is compiled and unit-tested (its network-failure path verified against
a guaranteed-unreachable local address) but has never made a real call to Anthropic's API in this
environment — no credentials were available (see "ClaudeProvider" below). `axiom-cli`'s `--ai`
flag is not built yet. The orchestration layer this builds on (`axiom-analyzer`'s
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

**No real API call has been made in this environment** — no `ANTHROPIC_API_KEY` and no `ant` CLI
were available when this was built. Verified instead by: (1) compiling against the real SDK
(`javap` was used to find the exact generic types — `StructuredMessage<T>`,
`StructuredContentBlock<T>.text()` returning `Optional<StructuredTextBlock<T>>`,
`StructuredTextBlock<T>.text()` returning `T` directly — rather than guessing signatures), and
(2) a unit test that points the client at `http://127.0.0.1:1` (a reserved, immediately-refused
port) to deterministically and quickly exercise the network-failure -> `LlmExplanationException`
path without any real network access. A live end-to-end call against the real API is still
unverified and should happen before this is considered production-ready.

## `axiom-cli`'s `--ai` flag — not built yet

Not meaningfully usable without confidence the provider actually works end-to-end (see above).
Planned config naming: `AXIOM_LLM_PROVIDER` + `AXIOM_LLM_API_KEY` env vars (provider-agnostic,
not `ANTHROPIC_API_KEY` — the abstraction should hold all the way through configuration, not just
the interface), read only from the environment, never a CLI argument or committed file. AI must
be an explicit `--ai` opt-in, never auto-triggered by an env var happening to be set; if `--ai`
is passed but the key is missing, that's a fail-fast usage error (exit `1`), not a silent
fallback to deterministic-only.

## Tests
32 tests across `AiExplanationTest`, `AnalyzerWarningTest`, `PromptBuilderTest`,
`FakeLLMProviderTest`, `AIEnhancedAnalyzerTest`, `ClaudeProviderTest`, plus updated
`AnalyzedFailureTest`/`AnalysisResultTest`. Covers: explanation attached without altering
classification, timeout and provider-failure fallback (both leaving classification untouched),
per-failure isolation, a passed-only report, parser warnings still propagating through the
AI-enhanced path, the golden-prompt/pipeline-context/truncation behaviors in `PromptBuilder`, and
`ClaudeProvider`'s network-failure wrapping (no real API call).
