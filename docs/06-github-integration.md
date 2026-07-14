# GitHub Integration

Deep-dive for the `axiom-github` module. Short reference: `02-system-architecture.md`'s
Module Reference section.

Status: not yet implemented.

## Pipeline
```
GitHub Actions -> Tests -> XML -> Axiom -> PR Comment
```

## v1 Scope
- PR comment with failure summary, category, evidence, suggested next step
- Workflow run summary

## Future
- Checks API integration
- Inline review annotations on the failing test/assertion
- Historical trend reporting across runs
