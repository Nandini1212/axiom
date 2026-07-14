# Product Vision

## Mission
Reduce engineering time spent triaging CI/CD failures.

## Vision
Axiom is an Engineering Intelligence Platform: the layer that sits between CI systems and
developers, explaining *why* something failed instead of just reporting *what* failed.

## Core Principles
- Deterministic before AI
- Evidence over guesses
- Workflow-native — insights land where developers already work (PR comments, workflow
  summaries), not a separate dashboard
- Extensible architecture
- Developer productivity first

## Target Users
- Software Engineers
- SDETs
- DevOps
- QA Engineers
- Engineering Managers

## What Axiom Is Not
- A CI/CD replacement (not Jenkins, GitHub Actions, GitLab CI)
- A Jira replacement
- A generic chatbot
- A full test automation framework
- A tool that auto-fixes failures

## Success Metrics
See `08-success-metrics.md`.

## Historical Strategy Context
Earlier product exploration (pre-v1.0 architecture) considered a broader "AI-Powered QA Failure
Intelligence Platform" positioning: a GitHub-Action-first go-to-market wedge, failure
fingerprinting, and flaky-vs-real confidence scoring for historical intelligence. That
exploration's *implementation* choices — Spring Boot, SQLite/Postgres, a local-upload-first
MVP — are superseded by the architecture in this doc set and must not drive current code
structure. Its *strategic* ideas remain live candidates for Phase 2+:

- Workflow-native adoption wedge (meet developers where failures already surface)
- Failure fingerprinting for historical pattern matching
- Flaky-vs-real confidence scoring
- Lightweight GitHub Action / Marketplace distribution model

See `07-roadmap.md` for how these map onto current phases. The full original exploration document
is retained outside this repository (`~/Downloads/Axiom_Recovered_Documentation/`) for deeper
context if needed.
