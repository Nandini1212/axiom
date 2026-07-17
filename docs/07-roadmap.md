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
8. `axiom-cli` (thin entry point invoking `Analyzer`, printing `AnalysisResult`) — done.
   `axiom <rules.yaml> <report.xml>`, exit `0` on successful analysis regardless of failures
   found, `1` on execution failure, `2` on invalid usage. Axiom is now a runnable product, not
   just tested infrastructure — verified end to end with real rule/report files, not just unit
   tests (matched failure, unmatched failure, and passed-only-report cases all confirmed).
9. AI-enhanced analysis — **full local AI pipeline implemented and locally verified, including
   `axiom-cli`'s `--ai` flag.** `AiExplanation`, `AnalyzerWarning`, `LLMProvider`, `PromptBuilder`,
   `FakeLLMProvider`, `AIEnhancedAnalyzer`, a real `ClaudeProvider`, and `axiom-cli`'s `--ai` flag
   are all built and tested. `ClaudeProvider` is **implemented and locally verified. Pending live
   integration testing.** — it compiles against the actual `com.anthropic:anthropic-java` SDK and
   its failure-wrapping path is unit-tested, but it has never made a real call to Anthropic's API —
   no credentials were available in this environment (see `05-ai-analyzer.md`). Builds on the
   `Analyzer` interface without changing its method signature, though
   `AnalyzedFailure`/`AnalysisResult` did grow new fields (via secondary constructors, so no
   existing call site broke). Do not describe this as "AI flow verified end to end" until that
   live run below actually succeeds — reserve that phrase, in contrast to the deterministic
   pipeline above which already is complete end to end. Remaining: an actual live run of the full
   pipeline (JUnit XML -> Parser -> Rule Engine -> Deterministic Classification -> Claude
   Explanation -> CLI Output) against the real API once credentials are available — the
   top-priority remaining risk before any AI-related claim of "production-ready."
10. `axiom-reporting` (Markdown/HTML/JSON)
11. `axiom-github` (PR comments, workflow summary)

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

## Phase 2+ Ideas Retained From Earlier Product Exploration
Not yet scheduled, but worth revisiting once the deterministic core (through 1.0) is proven —
see `01-product-vision.md`'s Historical Strategy Context:
- Failure fingerprinting for historical pattern matching (normalized stack trace + error message
  + test name + service name + exception type + top stack frame)
- Flaky-vs-real confidence scoring, distinct from the deterministic `FLAKY_TEST` category
- Slack/Teams/Jira integrations pushing summaries into workflows beyond GitHub
- Lightweight GitHub Action / Marketplace distribution as an adoption wedge
