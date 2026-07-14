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

Two positional arguments, no subcommand: `axiom <rules.yaml> <report.xml>`. Not `axiom analyze
<report.xml>` with an implied default ruleset — Axiom cannot classify anything without rules, and
there's no bundled default ruleset, so hiding that requirement behind a single-argument UX would
be dishonest about what the tool needs. No subcommand either, since there's exactly one command
today; subcommand parsing is complexity with nothing yet to justify it.

## Construction vs. execution

```java
static Analyzer createAnalyzer(Path rulesPath) { ... }   // builds the concrete dependency graph
static int run(String[] args, PrintStream out, PrintStream err) { ... }  // execution path
```

`createAnalyzer` is the only place that knows concrete types exist
(`YamlRuleSource`/`DefaultRuleProcessor`/`DefaultRuleEngine`/`DeterministicStrategy`/
`JUnitXmlParser`). Once it returns an `Analyzer`, `run`'s execution path — opening the report,
calling `analyze()`, printing the result — touches nothing from axiom-classifier or axiom-parser
directly. This split is also what makes `run()` unit-testable without a subprocess: it takes
`PrintStream`s for stdout/stderr instead of touching `System.out`/`System.err` directly, so tests
call it with capturing streams and assert on both the returned exit code and captured text.

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

Beyond the 8 automated tests, the CLI was actually run via `./gradlew :axiom-cli:run` against real
rule/report files for three cases (matched failure, unmatched failure, passed-only report),
confirming the real console output and exit codes match what the tests assert — not just that the
assertions pass in isolation.

## Tests

8 tests in `AxiomCliTest`: wrong argument count -> exit `2`; a matched failure -> exit `0` with
category/confidence/rule id in the output; an unmatched failure -> `UNKNOWN`/`(no rule matched)`;
a passed-only report -> `Detected 0 failure(s)`, exit `0` (the normal clean-build case); a
nonexistent report path -> exit `1`; malformed rule YAML -> exit `1`; malformed report XML ->
exit `1`; a report producing a parser warning -> the warning appears in the output rather than
`Warnings: none`.
