# AI-Enhanced Analysis (future)

Status: not yet implemented. This doc now covers only the *future* AI layer — the orchestration
layer it builds on top of (`axiom-analyzer`'s `Analyzer`/`AnalysisResult`/`AnalyzedFailure`,
`DeterministicAnalyzer`) is already built; see `11-analyzer.md` for that.

## Reframing note (as of this milestone)
`axiom-analyzer` was originally scoped as "the AI module." It's now scoped as the orchestration
layer first (`Parser` + classifier -> `AnalysisResult`), fully deterministic, with AI as a future
*enhancement* to that same `Analyzer` interface — not a replacement for it, and not something the
orchestration layer depends on. This mirrors `ClassificationStrategy`/`DeterministicStrategy`:
the deterministic path exists and works completely on its own; AI is an alternative/additional
implementation of the same interface, added later without the interface or its existing callers
changing.

## Purpose
Explain a `DeterministicAnalyzer`'s results — not replace them. AI never classifies from scratch
when deterministic evidence exists (ADR-0001). Concretely: a future `AIEnhancedAnalyzer`
(implementing the same `Analyzer` interface) would wrap or extend `DeterministicAnalyzer`'s
output, adding explanation to each `AnalyzedFailure` rather than deciding categories itself.

## Input
- `AnalyzedFailure` (the `FailureEvent` + `ClassificationResult` pairing that already exists)
- Historical context (future)

## Output
- Root cause explanation
- Suggested next steps
- Human-readable summary
- Confidence explanation

Exact output shape (e.g. whether `AnalyzedFailure` gains a nullable AI-explanation field, or a
richer wrapping type is introduced) is not decided yet — deliberately deferred until this
milestone actually starts, per the project's standing discipline against speculative fields.

## LLM Abstraction
`LLMProvider` interface, implementations pluggable:
- Claude
- OpenAI
- Gemini
- Bedrock

Only one provider needs to be wired up initially; the abstraction exists so a second provider is
a new implementation, not a rewrite.

## Prompting Discipline
The AI receives structured input (failure metadata, classification, evidence, stack trace), not
raw messy logs alone, and is instructed to summarize/ground its explanation in that evidence
rather than guess. Same "deterministic before AI" principle applied to prompt design.
