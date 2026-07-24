# Roadmap

## Version Milestones
- **0.1** — Parser, rule engine (deterministic classification working end to end)
- **0.2** — Claude integration (AI analyzer)
- **0.3** — GitHub PR comments
- **0.4** — Historical analysis
- **1.0** — Engineering Intelligence Platform

**Tagged `v0.1.0` (2026-07-21)**: covers deterministic classification through the AI-enhanced
`--ai` flag, both verified end to end (deterministic via real rule/report files, AI via a live
Anthropic API call). Freezes this milestone before starting any proposed follow-on work (e.g. an
Evidence Correlation Engine, discussed but not yet designed or approved as of this tag).

**Since `v0.1.0` (not yet tagged)**: the Evidence Correlation Engine — `axiom-correlation`'s v0.1
slice (`ApplicationBugCorrelationRule`), v0.2 slice (`InfrastructureFailureRule`, competing
hypotheses, `AssessmentSelector`'s minimum-lead requirement), and v0.3 slice (`TransientFailureRule` —
explicitly scoped as single-run transience, not historical flakiness, since no historical-evidence
source exists yet) plus a text/Markdown presentation layer — are all built, tested, and pushed to
GitHub. Not yet wired into `axiom-cli` (no `axiom investigate` command exists). Next planned rule:
test-automation (see "Current Build Order" item 11 below).

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
8. `axiom-cli` (thin entry point invoking `Analyzer`, printing `AnalysisResult`) — done.
   `axiom <rules.yaml> <report.xml>`, exit `0` on successful analysis regardless of failures
   found, `1` on execution failure, `2` on invalid usage. Axiom is now a runnable product, not
   just tested infrastructure — verified end to end with real rule/report files, not just unit
   tests (matched failure, unmatched failure, and passed-only-report cases all confirmed).
9. AI-enhanced analysis — **done. AI flow verified end to end against the real Anthropic API
   (2026-07-21).** `AiExplanation`, `AnalyzerWarning`, `LLMProvider`, `PromptBuilder`,
   `FakeLLMProvider`, `AIEnhancedAnalyzer`, a real `ClaudeProvider`, and `axiom-cli`'s `--ai` flag
   are all built, tested, and now confirmed live: `axiom --ai <rules.yaml> <report.xml>` against a
   real key produced a real `AiExplanation` via structured outputs, with the deterministic
   category/confidence/rule unchanged. Builds on the `Analyzer` interface without changing its
   method signature, though `AnalyzedFailure`/`AnalysisResult` did grow new fields (via secondary
   constructors, so no existing call site broke). The live test also surfaced and fixed a real
   defect — `axiom-cli` computed `AnalyzerWarning`s but never printed them, so an invalid key or
   short timeout silently showed no AI section and a misleading "Warnings: none"; fixed and
   re-verified live (see `05-ai-analyzer.md`). Still not instrumented: exact API latency and which
   model version the server returned (no telemetry on `AiExplanation` today — see the "Provider
   metadata" backlog item below), retry/backoff behavior, and large-stack-trace token-limit
   behavior.
10. Evidence Correlation Engine (`axiom-correlation`) — **v0.1, v0.2, and v0.3 done, not yet wired
    into `axiom-cli`.** Multi-signal root-cause assessment (test-failure + source-change +
    execution evidence), deterministic and separate from the single-event classifier above. v0.1:
    `ApplicationBugCorrelationRule`, evidence model, signal extraction, abstention
    (`NEEDS_INVESTIGATION`). v0.2: `InfrastructureFailureRule` — the first rule reusing a signal
    with the opposite interpretation (`RETRY_PASSED` supports infrastructure, contradicts
    application-bug), and an `AssessmentSelector` upgrade requiring a minimum confidence lead over
    the runner-up hypothesis, not just clearing the threshold, so two competing plausible
    hypotheses abstain rather than picking an arbitrary winner. v0.3: `TransientFailureRule` — explicitly
    scoped as a single-run transient-failure hypothesis, not proof of historical flakiness (no
    evidence source for behavior across multiple runs exists yet); the first rule to score an
    *absence* of correlation as a (modest) positive contribution, and to treat a real code
    correlation as a blocking veto against calling a failure flaky. Also built: a text/Markdown
    presentation layer (`TextAssessmentRenderer`, `MarkdownAssessmentRenderer`) sharing derived
    reasoning via `AssessmentFacts`, deliberately excluding any assigned owner or time estimate
    (no evidence source for either exists), and category-aware recommended actions (a gap found
    while adding `TransientFailureRule`: the original action text only ever made sense for
    `APPLICATION_BUG`). See `docs/13-evidence-correlation-design.md` and
    `docs/14-correlation-signal-weights.md`. Remaining before this is a usable product feature
    (not just a tested library): CLI wiring (an `axiom investigate` command).
11. Test Automation Rule (`axiom-correlation`) — **next, not started.** Fourth correlation rule.
    Suggested order after this: Deployment Failure Rule, Dependency Failure Rule (see
    `14-correlation-signal-weights.md`'s "Adding a new rule to this matrix"). Each additional rule
    should be validated against real failure examples, not just fixtures, before being considered
    done — the same discipline already applied to the first three.
12. `axiom-reporting` (Markdown/HTML/JSON)
13. `axiom-github` (PR comments, workflow summary)

## Phase 2+ (post-1.0)
From the current architecture's own long-term vision:
- Failure clustering across repositories
- AI-powered PR risk analysis
- Test impact analysis / intelligent test selection
- Root cause correlation — **partially built**, see item 10 above; "Phase 2+" here now refers to
  the more advanced form (cross-repository, historical) this section originally meant, not the
  single-run correlation that already exists
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
- **A tiny internal bootstrap/factory for dependency construction** — `AxiomCli.createAnalyzer`
  wires everything manually today, which is fine at 5 concrete dependencies
  (`YamlRuleSource`/`DefaultRuleProcessor`/`DefaultRuleEngine`/`DeterministicStrategy`/
  `JUnitXmlParser`). Revisit once more parsers/providers/integrations exist and manual wiring
  gets unwieldy — roughly "around the fifth or sixth new concrete dependency," not a fixed
  number, and not Spring/Guice; something small and internal.
- **A `--json`/structured-output flag for `axiom-cli`** — today's console output is explicitly
  human-oriented (see `12-cli.md`). `AnalysisResult` is already structured data, so a future flag
  only needs a second rendering path in the CLI, not a change to `Analyzer`/`AnalysisResult`.
- **A bundled `default-rules.yaml`** so `axiom <report.xml>` (one argument) becomes possible —
  today's two-argument shape is the honest interface while no default ruleset is shipped with the
  application (see `12-cli.md`). Not for v1.
- **LICENSE** — deliberately skipped for now (asked the user directly; deferred, not forgotten).
  Revisit before any public sharing of the repo.
- **CI build-status badge in the README** — deferred alongside the LICENSE decision: there's no
  GitHub remote yet, so a badge URL would have to point at a repo path that doesn't exist. The
  workflow itself (`.github/workflows/test.yml`, running `./gradlew test`) is already added and
  will work once the repo is pushed somewhere; only the badge is waiting.
- **An actual architecture diagram image** (not just the ASCII pipeline already in the README) —
  explicitly a "later" polish item, not urgent.
- **`PromptBuilder` as an interface** (with today's implementation renamed `DefaultPromptBuilder`)
  — revisit once a second `LLMProvider` actually exists. Most of the prompt content (failure,
  stack trace, evidence, pipeline context) is provider-independent already; only structured-output
  transport mechanics would differ per provider, so a second provider may simply reuse
  `PromptBuilder` unchanged rather than needing its own variant — don't assume the interface split
  is needed until a real second provider proves whether it is.
- **`AIEnhancedAnalyzer` owning a shared `ExecutorService`** instead of creating one per failure —
  revisit if/when calls are ever parallelized. Not needed for today's sequential-per-failure calls.
- **`LLMProviderFactory`** — `AxiomCli.wrapWithAi` currently reads env config and constructs
  `ClaudeProvider` directly in one contained private method. Extract a factory once a second
  provider actually exists to inform what varies (API key vs. region/role-based auth, different
  timeout semantics, etc.) — guessing that shape today would be speculative, same reasoning as the
  `PromptBuilder`-as-interface deferral above.
- **`LLMConfiguration` object** instead of passing `Map<String, String> env` around — revisit
  alongside `LLMProviderFactory` above; today it's three env lookups in one place, not sprawl.
- **Prompt versioning** (e.g. "Prompt v3" surfaced somewhere) so a prompt-wording change that
  shifts explanation quality is traceable later. Not built yet — no concrete consumer for a
  version number until reporting/debugging actually needs to distinguish explanations by prompt
  version.
- **Provider metadata on `AiExplanation`** (provider, model, latency, tokens) — not needed by
  `AIEnhancedAnalyzer` itself, but `axiom-reporting` will likely want it later. Deliberately kept
  off `AiExplanation` for now; add when a concrete reporting need exists, not preemptively.
- **Rename `SignalType.NO_STACK_FRAME_MATCH`-style names to describe the semantic fact, not
  today's implementation** (e.g. `NO_CODE_CORRELATION` instead) — `TransientFailureRule` doesn't
  actually care about stack frames specifically; it cares whether anything connects the failure
  to a changed production file, which today happens to be implemented via stack-frame matching
  but could later come from blame analysis, ownership data, or execution tracing. Not blocking
  anything; revisit if/when a second way of establishing that same fact is actually implemented.
- **Confidence ceilings as a property of `HypothesisScorer`/the hypothesis type**, rather than
  emerging implicitly from each rule's chosen weights summing to some maximum — e.g.
  `ApplicationBugCorrelationRule` topping out near 1.0 while `TransientFailureRule` tops out at 0.85 is
  true today only because someone can add up the constants. Worth formalizing once
  hypothesis-specific scoring rules grow past what's easy to verify by eye (see
  `14-correlation-signal-weights.md`, which exists for exactly this reason). Not needed at three
  rules.
- ~~Split `FlakyTestRule` into a rule-per-evidence-source pattern before v1.0~~ — **done**:
  renamed to `TransientFailureRule` (this execution's retry evidence) ahead of the new
  `HistoricalFlakyTestRule` (historical mixed-outcome evidence), both mapping to the same
  `FailureCategory.FLAKY_TEST` for taxonomy compatibility while each rule name honestly describes
  which evidence produced the hypothesis. See `docs/15-historical-execution-evidence-design.md`
  §10 for the rationale and sequencing.
- **Phase X — Multi-Failure Investigation**: today, `ReportEvidenceReader`
  (`axiom-investigation-file`) requires exactly one failure per report — a report with zero or
  more than one failure produces a warning rather than a guess at which one to investigate. This
  is a capability boundary, not a domain rule that a multi-failure report is invalid: a PR failing
  three unrelated tests is legitimately still one engineering event
  (`16-investigation-domain-model.md` §3). Reasoning across several simultaneous failures needs
  its own design effort before it can be built, since every existing `CorrelationRule` currently
  finds its test failure via `.findFirst()` — an explicitly unresolved question since
  `13-evidence-correlation-design.md` §18. Goals for that future design, not decided now:
  - Support one Investigation containing multiple `FailureEvent`s
  - Introduce cross-failure signals (e.g. "N unrelated tests failed together")
  - Detect shared root causes across those failures
  - Aggregate individual per-failure assessments into one conclusion
  - Preserve deterministic reasoning throughout — no LLM-driven correlation, same as today

## Phase 2+ Ideas Retained From Earlier Product Exploration
Not yet scheduled, but worth revisiting once the deterministic core (through 1.0) is proven —
see `01-product-vision.md`'s Historical Strategy Context:
- Failure fingerprinting for historical pattern matching (normalized stack trace + error message
  + test name + service name + exception type + top stack frame)
- Flaky-vs-real confidence scoring, distinct from the deterministic `FLAKY_TEST` category
- Slack/Teams/Jira integrations pushing summaries into workflows beyond GitHub
- Lightweight GitHub Action / Marketplace distribution as an adoption wedge
