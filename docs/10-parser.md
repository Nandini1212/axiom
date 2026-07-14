# Parser

Deep-dive for the `axiom-parser` module. Short reference: `02-system-architecture.md`'s Module
Reference section.

## Status: built (JUnit XML only)

`JUnitXmlParser implements Parser`, using DOM (`javax.xml.parsers`/`org.w3c.dom`, part of the
JDK — no external XML dependency). TestNG/pytest/NUnit/GitHub-native support are future,
independent `Parser` implementations (ADR-0003) — no shared base class exists yet, deliberately;
one will be extracted only if real duplication shows up once a second implementation exists.

## Public API

```java
public interface Parser {
    ParserResult parse(InputStream input);
}

public record ParserResult(List<FailureEvent> failures, List<ParserWarning> warnings) {}

public record ParserWarning(
    WarningType type, String testcaseName, String className, String suiteName, String detail
) {}

public enum WarningType { MISSING_ATTRIBUTE, AMBIGUOUS_STATUS, INVALID_TIMESTAMP, UNSUPPORTED_ELEMENT }
```

`ParserResult`, not bare `List<FailureEvent>` — a direct, load-bearing consequence of one
decision: malformed individual records must never disappear silently (see Error Handling below).
Once that's accepted, `List<FailureEvent>` has no channel to report what it couldn't parse
through, so the richer result type isn't speculative infrastructure, it's required by that
decision. `ParsingException` (unchecked) is separate and reserved for whole-document failures —
the document isn't parseable as XML at all, no partial result is possible.

`parse()` does not close the given `InputStream` — the caller owns its lifecycle. This needed an
explicit fix: the JDK's bundled Xerces DOM implementation closes the stream it's given as an
internal cleanup detail, which would have silently violated this contract. `JUnitXmlParser` wraps
the input in a stream whose `close()` is a no-op before handing it to the underlying parser,
guaranteeing the caller's stream survives regardless of that JDK internal.

## Responsibilities and boundaries

Converts one JUnit XML document into `FailureEvent`s for every test case that did **not** pass
(`FailureStatus` has no `PASSED` value — a passing test case produces nothing). Explicitly out of
scope: classification (`axiom-classifier`'s job), `pipelineContext` enrichment (no CI/repo/branch
info exists in the XML itself — always `null` from this parser), fetching the report from
anywhere, and aggregating multiple files (one `parse()` call handles one document; a caller with
several files calls it once per file).

## JUnit XML -> FailureEvent Mapping

| Field | Source | Notes |
|---|---|---|
| `id` | SHA-256 hash of `(suiteName, className, testName, status, message, stackTrace, occurredAt, ordinal)` | Deterministic, not random: re-parsing the same document produces the same ids, since it's describing the same real-world occurrences. `ordinal` (the testcase's position in document order) is included specifically to avoid collisions when a report contains retried tests with otherwise-identical content (a real Surefire `rerunFailingTestsCount` behavior) — without it, two distinct retry occurrences of a non-flaky failure would hash identically. |
| `testName` | testcase `name` | |
| `className` | testcase `classname` | |
| `suiteName` | the **immediate enclosing** `<testsuite>`'s `name`, never the root or a concatenated ancestor path | `suiteName` is a single string field, not a list; the immediate parent is the most specific single value, consistent with how `className`/`testName` are also direct associations. Applies uniformly regardless of nesting depth — a `<testsuite>` nested inside another `<testsuite>` still reports its own immediate `name`. |
| `sourceFormat` | always `JUNIT` | |
| `status` | `ERROR` if `<error>` present, else `FAILED` if `<failure>` present, else `SKIPPED` if `<skipped>` present, else no event | If **both** `<failure>` and `<error>` are present (malformed/unusual): `ERROR` wins, and a `WarningType.AMBIGUOUS_STATUS` warning is emitted — the `<failure>` content is not silently discarded from view, even though only the `<error>` payload becomes the `FailureEvent`'s `message`/`stackTrace`. |
| `message` | the matched status element's `message` attribute | nullable |
| `stackTrace` | the matched status element's text content | `null` for `SKIPPED` |
| `durationMillis` | testcase `time` attribute × 1000, rounded | JUnit XML's `time` is in **seconds**, not milliseconds — this conversion is load-bearing and has an explicit test (`time="0.34"` -> `340`). `null` if `time` absent, or if present but not numeric (no warning emitted for the non-numeric case — a known minor gap, see below). |
| `occurredAt` | enclosing `<testsuite>`'s `timestamp`, parsed as `Instant` | JUnit XML's `timestamp` conventionally has no zone offset; assumed UTC (a `Z` is appended before parsing if the raw value doesn't parse as-is). `null` plus a `WarningType.INVALID_TIMESTAMP` warning if unparseable even with that assumption. All testcases in the same suite share the same `occurredAt` (JUnit XML has no per-testcase timestamp) — a stated approximation, not a hidden one. |
| `pipelineContext` | always `null` | Out of scope for this module. |
| `metadata` | always empty | Deliberately deferred — `<properties>`/`system-out` content isn't mapped in v1. |

**Known minor gap**: a non-numeric `time` attribute is silently treated as absent (`durationMillis
= null`) rather than producing a warning — `WarningType`'s four values (as reviewed and approved)
don't cleanly cover "attribute present but malformed" for duration specifically the way
`INVALID_TIMESTAMP` does for timestamps. Low-stakes; revisit if it turns out to matter in practice.

## Error Handling

- **Document is not well-formed XML at all** -> `ParsingException`, no partial result. No recovery
  is possible from a document that can't be parsed as XML in the first place.
- **A single testcase can't produce a valid `FailureEvent`** (e.g. missing enough of
  `name`/`classname`, with the enclosing suite also missing `name`, so none of
  `testName`/`className`/`suiteName` can be populated — `FailureEvent`'s own constructor rejects
  this combination) -> that one testcase is skipped, a `WarningType.MISSING_ATTRIBUTE` warning is
  recorded, and parsing continues. A CI-generated test report is data, not hand-authored config
  like the rule YAML (ADR-0002) — a single corrupted record (partial disk write, crashed writer)
  shouldn't hide legitimate failures sitting next to it in the same file.

## Security

`DocumentBuilderFactory` is configured with `disallow-doctype-decl=true` (plus
`setXIncludeAware(false)`/`setExpandEntityReferences(false)`) to harden against XXE (XML External
Entity) injection — a well-known vulnerability class when parsing untrusted XML. JUnit reports
never legitimately need a `DOCTYPE`, so any document containing one is rejected outright rather
than attempting to selectively permit "safe" external entities. This wasn't part of the reviewed
design discussion; it was added proactively during implementation and is covered by an explicit
test (`xxeAttemptIsRejected`) proving a `DOCTYPE`-bearing document is rejected regardless of what
the entity would have resolved to.

## Why DOM, not StAX/SAX

Typical CI JUnit XML reports (even several thousand test cases) run low-to-mid single-digit
megabytes — comfortably within DOM's memory footprint. DOM's random-access tree is a much better
fit for this specific mapping, where a testcase needs data from its parent (`suiteName`,
`timestamp`) — exactly the case a single-pass streaming parser handles worst. Same "don't optimize
before measuring" reasoning as ADR-0007's rule-engine linear-scan decision; if a genuinely
huge-report case ever appears, swapping `JUnitXmlParser`'s internals to StAX doesn't require
changing the `Parser` interface.

## Tests

31 tests across `JUnitXmlParserTest`/`ParserResultTest`/`ParserWarningTest`, covering: every
status type, passed-test exclusion, empty suites, multiple failures in document order, sibling
suites under `<testsuites>`, nested suites (proving immediate-parent naming), missing optional
attributes, the both-failure-and-error precedence + warning, the missing-identifying-attributes
skip + warning (with a second valid testcase in the same file proving the rest still parses),
invalid timestamp handling, malformed XML, the XXE rejection, retried-test id uniqueness via the
ordinal, same-document-twice id determinism, a 5,000-testcase large-report case, and the
input-stream-not-closed guarantee.
