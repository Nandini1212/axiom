# End-to-End Walkthrough: One Failure Through Axiom

This traces a single test failure through the entire intended pipeline, start to finish. Steps
1-2 and 6 are **illustrative** — `axiom-parser`, `axiom-reporting`, and `axiom-github` don't exist
yet, so those shapes show the *intended* design, not verified behavior. Steps 3-5 are **real** —
exactly what the built and tested `axiom-classifier` code does today.

## 1. A JUnit test fails in CI (illustrative — no parser yet)

A test run produces JUnit XML like:

```xml
<testcase name="shouldReturnUser" classname="com.example.UserServiceTest" time="0.34">
  <failure message="Connection refused: could not connect to database">
    java.net.ConnectException: Connection refused
        at com.example.UserService.connect(UserService.java:42)
  </failure>
</testcase>
```

## 2. The (not-yet-built) parser normalizes it into a FailureEvent

`axiom-parser`'s job, once built, is to turn that XML into the one shape every other module
understands (ADR-0003):

```
FailureEvent(
  id            = "evt-1"
  testName      = "shouldReturnUser"
  className     = "com.example.UserServiceTest"
  sourceFormat  = JUNIT
  status        = FAILED
  message       = "Connection refused: could not connect to database"
  stackTrace    = "java.net.ConnectException: Connection refused\n\tat ..."
  durationMillis = 340
)
```

## 3. RuleSource + RuleProcessor have already loaded the rulebook (real, built)

Separately, at startup, a rule file like this was loaded and prepared:

```yaml
rules:
  - id: connection-refused
    priority: 100
    match:
      any:
        - field: message
          operator: contains
          value: "Connection refused"
    classification:
      category: INFRASTRUCTURE_FAILURE
      confidence: 0.95
    evidence:
      message: "Dependent service unavailable"
```

`YamlRuleSource` parsed it into a `RuleDefinition`; `DefaultRuleProcessor` resolved its defaults
(`enabled` -> `true`) and produced a `PreparedRule` ready for evaluation.

## 4. RuleEngine evaluates the FailureEvent against every PreparedRule (real, built)

`DefaultRuleEngine.evaluate(event)` checks `event.message()` against the `connection-refused`
rule's condition. `"Connection refused: could not connect to database"` contains
`"Connection refused"` (case-insensitively), so it matches. The engine produces:

```
RuleMatch(
  ruleId     = "connection-refused"
  priority   = 100
  category   = INFRASTRUCTURE_FAILURE
  confidence = 0.95
  evidence   = [ Evidence(
      field         = MESSAGE
      operator      = CONTAINS
      expectedValue = "Connection refused"
      actualValue   = "Connection refused: could not connect to database"
      explanation   = "Dependent service unavailable"
  ) ]
)
```

If other rules also matched this same failure, they'd each produce their own `RuleMatch` in this
same list — the engine reports every match, it never picks a winner itself.

## 5. DeterministicStrategy picks the winner (real, built)

Suppose this was the only match (or the highest-ranked one by priority/confidence/id —
see `04-rule-engine.md`'s worked example for how ties resolve). `DeterministicStrategy.classify`
returns:

```
ClassificationResult(
  category      = INFRASTRUCTURE_FAILURE
  confidence    = 0.95
  matchedRuleId = "connection-refused"
  evidence      = [ Evidence(MESSAGE, CONTAINS, "Connection refused",
                              "Connection refused: could not connect to database",
                              "Dependent service unavailable") ]
)
```

This is Axiom's final, deterministic answer for this one failure — no AI involved yet.

## 6. Reporting and GitHub post it back to the developer (illustrative — not built yet)

Once `axiom-reporting`/`axiom-github` exist, this `ClassificationResult` (optionally elaborated by
`axiom-analyzer`'s AI explanation layer) becomes a PR comment along these lines:

```
CI Failure Analysis

Category: Infrastructure Failure
Confidence: 95%
Evidence: message contains "Connection refused"
  -> "Connection refused: could not connect to database"
Note: Dependent service unavailable
```

## Where the boundary actually is today

Everything from step 3 onward is real, tested code (134 passing tests across
`axiom-common`/`axiom-classifier`). Steps 1, 2, and 6 are the shape the architecture already
commits to (`FailureEvent`'s fields, `ClassificationResult`'s fields) but nothing yet produces or
consumes them at those ends — that's exactly the `axiom-parser` and `axiom-reporting`/
`axiom-github` work still ahead.
