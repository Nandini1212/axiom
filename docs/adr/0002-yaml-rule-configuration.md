# ADR-0002: YAML Rule Configuration

## Status
Accepted

## Context
Classification rules need to be authored and modified without recompiling or redeploying the
application, by people who may not be Java engineers. They also need structural validation (a
condition needs a field/operator/value; a match group needs exactly one of `any`/`all`) rather
than being free-form.

## Decision
Rules are authored in YAML files with a fixed schema (`RuleDefinition`, `MatchGroup`,
`Condition`, `ClassificationSpec`, `EvidenceSpec`), loaded via `RuleSource` /
`YamlRuleSource`. Parsing uses Jackson's YAML module (`jackson-dataformat-yaml`), reusing the
same Jackson stack already used for JSON in `axiom-common` rather than introducing a second
YAML library (e.g. SnakeYAML directly). `FAIL_ON_UNKNOWN_PROPERTIES` is enabled so a typo'd key
fails loudly at load time instead of being silently ignored.

## Consequences
- Rule authoring is a config change, not a code change.
- Structural correctness (blank ids, malformed match groups, unknown operators/fields, invalid
  regex) is caught at load/process time via `RuleSourceException`/`RuleProcessingException`,
  before any rule reaches the evaluation engine.
- The rule schema is a public contract: changing field names or semantics is a breaking change
  for every deployed rule file, not just a code refactor.
