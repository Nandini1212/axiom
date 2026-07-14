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
7. `axiom-analyzer` (Claude integration) — next
8. `axiom-reporting` (Markdown/HTML/JSON)
9. `axiom-github` (PR comments, workflow summary)

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

## Phase 2+ Ideas Retained From Earlier Product Exploration
Not yet scheduled, but worth revisiting once the deterministic core (through 1.0) is proven —
see `01-product-vision.md`'s Historical Strategy Context:
- Failure fingerprinting for historical pattern matching (normalized stack trace + error message
  + test name + service name + exception type + top stack frame)
- Flaky-vs-real confidence scoring, distinct from the deterministic `FLAKY_TEST` category
- Slack/Teams/Jira integrations pushing summaries into workflows beyond GitHub
- Lightweight GitHub Action / Marketplace distribution as an adoption wedge
