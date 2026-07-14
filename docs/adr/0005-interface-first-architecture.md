# ADR-0005: Interface-First Architecture

## Status
Accepted

## Context
Axiom's modules (parser, classifier, analyzer, reporting, GitHub integration) each have multiple
plausible implementations over the platform's life (multiple XML formats, multiple LLM
providers, multiple report formats, multiple CI systems). Coupling callers directly to concrete
implementations would make each of those extension points a breaking change instead of an
addition.

## Decision
Every cross-module capability is defined as an interface before any implementation is written:
`Parser`, `RuleSource`, `RuleProcessor`, `RuleEngine`, `ClassificationStrategy`, `LLMProvider`,
`Reporter`. Concrete implementations (`YamlRuleSource`, `DefaultRuleProcessor`, a future
`ClaudeProvider`, etc.) depend on nothing but the interface's contract and are wired in via
constructor injection — no static state, no service-locator lookups.

## Consequences
- New implementations (a second `RuleSource` backed by a directory or database, a second
  `LLMProvider`) are additive, not modifications to existing callers.
- Every module is independently unit-testable against the interface, without standing up the
  concrete dependencies it will eventually run with in production (e.g. `RuleEngine` tests can
  use hand-built `PreparedRule`/`FailureEvent` fixtures without a real YAML file or a real parser
  existing yet).
- Design conversations for each milestone start with "what interface, what shape" before any
  implementation code — this discipline is what caught the `PreparedRule` flattening decision and
  the any/all mutual-exclusivity invariant before they were embedded in code.
