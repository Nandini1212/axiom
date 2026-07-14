# AI Analyzer

Deep-dive for the `axiom-analyzer` module. Short reference: `02-system-architecture.md`'s
Module Reference section.

Status: not yet implemented.

## Purpose
Explain deterministic results — not replace them. AI never classifies from scratch when
deterministic evidence exists (see `adr/0001-deterministic-before-ai.md`).

## Input
- `FailureEvent`
- `ClassificationResult`
- Historical context (future)

## Output
- Root cause explanation
- Suggested next steps
- Human-readable summary
- Confidence explanation

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
rather than guess. This is the same "deterministic before AI" principle applied to prompt design.
