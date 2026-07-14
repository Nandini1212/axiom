# Roadmap

## Version Milestones
- **0.1** — Parser, rule engine (deterministic classification working end to end)
- **0.2** — Claude integration (AI analyzer)
- **0.3** — GitHub PR comments
- **0.4** — Historical analysis
- **1.0** — Engineering Intelligence Platform

## Current Build Order
Build order was intentionally changed from the original parser-first sequence: the classifier
work was already underway and doesn't depend on the parser existing, so it's being completed
vertically before starting the parser.

1. `axiom-common` — done
2. `RuleSource` (`axiom-classifier`) — done
3. `RuleProcessor` / `PreparedRule` (`axiom-classifier`) — done
4. `RuleEngine` (`axiom-classifier`) — done
5. `DeterministicStrategy` (`axiom-classifier`) — done. Deterministic classification vertical
   (`FailureEvent` -> `ClassificationResult`) is now complete end to end.
6. `axiom-parser` (JUnit XML -> `FailureEvent`) — done. TestNG support is a separate future
   `Parser` implementation, not yet built.
7. `axiom-analyzer` orchestration (`Parser` + classifier -> `AnalysisResult`, `DeterministicAnalyzer`,
   no AI) — done. Full JUnit-XML-to-`AnalysisResult` path now exists end to end.
8. AI-enhanced analysis (`LLMProvider`, `AIEnhancedAnalyzer`) — next, builds on the `Analyzer`
   interface without changing it. See `05-ai-analyzer.md`.
9. `axiom-reporting` (Markdown/HTML/JSON)
10. `axiom-github` (PR comments, workflow summary)

## Phase 2+ (post-1.0)
From the current architecture's own long-term vision:
- Failure clustering across repositories
- AI-powered PR risk analysis
- Test impact analysis / intelligent test selection
- Root cause correlation
- Historical failure trends
- Pipeline health scoring
- Release readiness insights
- Engineering productivity analytics

## Backlog (not urgent, tracked so they aren't forgotten)
- **Rename `Parser`** before v1 — too generic once more parser-like things exist (`YamlParser`,
  a future `PromptParser`, etc.). Candidates: `FailureReportParser`, `TestReportParser`. Not
  worth a breaking rename today with only one implementation.
- **Add `WarningType.INVALID_ATTRIBUTE`** (or similar) when a concrete need shows up — today a
  malformed (non-numeric) `time` attribute silently becomes `null` rather than producing a
  warning, since it doesn't cleanly fit any of the four existing `WarningType` values (see
  `10-parser.md`'s "Known minor gap"). Low-stakes; add the value when it actually matters, not
  preemptively.
- ~~Watch `*Result` naming as more modules land~~ — **resolved**: formally adopted as a convention
  now that `ParserResult` and `AnalysisResult` are both real instances of "primary output +
  diagnostics." See `02-system-architecture.md`'s API Conventions section.
- **`AnalysisRequest` instead of bare `InputStream`** — promoted to ADR candidate, see
  `adr/0008-analysis-request-candidate.md`. Not for v1; evaluate once a second real ingestion
  source (most likely `axiom-github`) exists to inform its shape.

## Phase 2+ Ideas Retained From Earlier Product Exploration
Not yet scheduled, but worth revisiting once the deterministic core (through 1.0) is proven —
see `01-product-vision.md`'s Historical Strategy Context:
- Failure fingerprinting for historical pattern matching (normalized stack trace + error message
  + test name + service name + exception type + top stack frame)
- Flaky-vs-real confidence scoring, distinct from the deterministic `FLAKY_TEST` category
- Slack/Teams/Jira integrations pushing summaries into workflows beyond GitHub
- Lightweight GitHub Action / Marketplace distribution as an adoption wedge
