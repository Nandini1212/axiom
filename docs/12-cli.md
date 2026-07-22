# CLI

Deep-dive for the `axiom-cli` module. Short reference: `02-system-architecture.md`'s Module
Reference section.

## Status: built

`axiom <rules.yaml> <report.xml>` — the first runnable, demoable form of Axiom. Everything below
this module was already built and tested; this milestone proves the pieces actually work together
outside of unit tests, and gives a stable integration point before AI is introduced.

## Why before AI, not after

Deliberately sequenced ahead of the AI-enhancement milestone: a demo where the deterministic
system already works, with AI shown as adding value on top of it, is a stronger demonstration
than leading with AI — it also means an offline/no-AI mode is possible by construction, not as an
afterthought, and the pipeline is exercised as a real process before any LLM cost or latency
enters the picture.

## Command-line shape

`axiom [--ai] <rules.yaml> <report.xml>` — an optional leading `--ai` flag, then the same two
positional arguments as before. Not `axiom analyze <report.xml>` with an implied default ruleset —
Axiom cannot classify anything without rules, and there's no bundled default ruleset, so hiding
that requirement behind a single-argument UX would be dishonest about what the tool needs. No
subcommand, since there's exactly one command today; subcommand parsing is complexity with nothing
yet to justify it. `--ai` is a flag, not a subcommand, because it toggles a behavior of the same
single command rather than selecting a different one.

## Construction vs. execution

```java
static Analyzer createAnalyzer(Path rulesPath, boolean aiEnabled, Map<String, String> env) { ... }  // builds the concrete dependency graph
static int run(String[] args, PrintStream out, PrintStream err) { ... }                              // execution path (delegates to the env-parameterized overload below with System.getenv())
static int run(String[] args, PrintStream out, PrintStream err, Map<String, String> env) { ... }     // testable execution path
```

`createAnalyzer` is the only place that knows concrete types exist
(`YamlRuleSource`/`DefaultRuleProcessor`/`DefaultRuleEngine`/`DeterministicStrategy`/
`JUnitXmlParser`, plus `ClaudeProvider`/`AIEnhancedAnalyzer` when `--ai` is passed). It always
builds the deterministic pipeline first — so malformed rules fail before any AI-specific
validation runs — then wraps it in `AIEnhancedAnalyzer` only if `aiEnabled` is true. Once it
returns an `Analyzer`, `run`'s execution path — opening the report, calling `analyze()`, printing
the result — touches nothing from axiom-classifier, axiom-parser, or axiom-analyzer's AI types
directly. This split is also what makes `run()` unit-testable without a subprocess: it takes
`PrintStream`s for stdout/stderr instead of touching `System.out`/`System.err` directly, and a
`Map<String, String>` for environment variables instead of touching `System.getenv()` directly, so
tests call it with capturing streams and a controlled env map and assert on both the returned exit
code and captured text — without ever needing real credentials or touching the real process
environment.

## `--ai` flag and AI configuration

When `--ai` is passed, three environment variables configure the AI layer (never a CLI argument or
committed file, so a key never ends up in shell history or a rules file):

- **`AXIOM_LLM_PROVIDER`** (optional, defaults to `claude`) — provider-agnostic naming on purpose.
  An unsupported value is a fail-fast usage error, exit `1`.
- **`AXIOM_LLM_API_KEY`** (required when `--ai` is passed) — missing key is a fail-fast usage
  error, exit `1`, not a silent fallback to deterministic-only output.
- **`AXIOM_LLM_TIMEOUT_SECONDS`** (optional, defaults to `30`) — per-failure explanation timeout
  passed to `AIEnhancedAnalyzer`. A non-numeric value is a fail-fast usage error, exit `1`.

AI is always an explicit opt-in: these env vars being set with no `--ai` flag has no effect at
all — Axiom never silently starts making network calls because a variable happened to be present
in the environment. When AI is enabled and a failure gets an explanation, it's printed beneath
that failure's deterministic classification (AI Summary, AI Root Cause, AI Suggested Next Steps,
AI Confidence Note). A failure whose explanation timed out or errored prints the deterministic
classification unchanged, plus a line naming what went wrong — `AI explanation unavailable
(AI_TIMEOUT): ...` or `AI explanation unavailable (AI_EXPLANATION_FAILED): ...` — correlated back
to that specific failure via `AnalyzerWarning.failureEventId`. This wasn't always true: a live
smoke test (see below) originally found that `AnalysisResult.analyzerWarnings()` was computed but
never printed, so these cases silently showed no AI section at all alongside a misleading
"Warnings: none" — fixed the same day it was found.

This flag is the concrete last piece needed to run the complete pipeline — JUnit XML -> Parser ->
Rule Engine -> Deterministic Classification -> Claude Explanation -> CLI Output — and **the AI flow
is now verified end to end (2026-07-21)**: a live `axiom --ai` run against a real key produced a
real `AiExplanation`, with deterministic classification unchanged, and the invalid-key/timeout
failure paths were separately confirmed live after the printing fix above. Not yet
instrumented: exact API latency and which model version the server returned (see
`05-ai-analyzer.md`'s "Provider metadata" backlog note), retry/backoff behavior, and
large-stack-trace token-limit behavior.

## Exit codes

- **`0`** — analysis completed, regardless of how many failures were found. A report with 50
  failures, all successfully analyzed, is still `0` — finding failures is the normal, expected
  outcome of running an analysis tool, not a tool error.
- **`1`** — execution failure: missing/unreadable file, malformed rule YAML
  (`RuleSourceException`/`RuleProcessingException`), malformed report XML (`ParsingException`),
  or any other unexpected runtime exception. Caught broadly (`catch (RuntimeException e)`) rather
  than enumerating each known exception type, specifically so an exception type nobody anticipated
  still maps to a clean `1` with a message instead of an uncaught stack trace.
- **`2`** — invalid CLI usage (wrong argument count).

**CI-gating behavior (e.g. "fail the build if any `APPLICATION_BUG` was found") is explicitly out
of scope for this milestone** and must not be introduced by silently changing the default exit
code later — it belongs in a future explicit flag (`--fail-on <category>`) or subcommand (`axiom
gate ...`), so gating is something a caller opts into, not a default that quietly starts mixing
"did the tool run" with "do I like what it found."

## Output

Deliberately temporary, inline presentation logic in a private method — not a preview of
`axiom-reporting`'s eventual `Reporter` design, which doesn't exist yet and shouldn't be designed
by accident through this module's throwaway console formatting.

**This output is optimized for a human reading a terminal, not for machine consumption** — there
is no `--json`/structured-output flag. A future flag can be added without redesigning
`Analyzer`/`AnalysisResult`, since `AnalysisResult` is already the structured data; only this
module's rendering would need a second code path.

```
Detected 1 failure(s)

FAILED  shouldLogin (com.example.LoginTest)
  Category:   INFRASTRUCTURE_FAILURE
  Confidence: 0.95
  Rule:       connection-refused

Warnings: none
```

An unmatched failure prints `UNKNOWN`/`0.0`/`(no rule matched)` rather than being hidden; a
passed-only report prints `Detected 0 failure(s)` and exits `0`, exercising the normal clean-build
case, not just the failing-report path.

## Verified end to end, not just unit-tested

Beyond the automated tests, the CLI was actually run via `./gradlew :axiom-cli:run` against real
rule/report files for three cases (matched failure, unmatched failure, passed-only report),
confirming the real console output and exit codes match what the tests assert — not just that the
assertions pass in isolation.

## Tests

13 tests in `AxiomCliTest`. The original 8: wrong argument count -> exit `2`; a matched failure ->
exit `0` with category/confidence/rule id in the output; an unmatched failure ->
`UNKNOWN`/`(no rule matched)`; a passed-only report -> `Detected 0 failure(s)`, exit `0` (the
normal clean-build case); a nonexistent report path -> exit `1`; malformed rule YAML -> exit `1`;
malformed report XML -> exit `1`; a report producing a parser warning -> the warning appears in
the output rather than `Warnings: none`. Plus 5 for the `--ai` flag: missing `AXIOM_LLM_API_KEY`
-> exit `1` naming the missing variable; an unsupported `AXIOM_LLM_PROVIDER` -> exit `1` naming
the bad value; a non-numeric `AXIOM_LLM_TIMEOUT_SECONDS` -> exit `1`; wrong argument count with
`--ai` still present -> exit `2` (flag parsing doesn't bypass usage validation); and a present
API key -> `createAnalyzer` constructs successfully without throwing. That last test deliberately
stops at construction and never calls `.analyze()` on the resulting `Analyzer`, since doing so
would attempt a real network call to Anthropic's API — not permitted in this test suite.
