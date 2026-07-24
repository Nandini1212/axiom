package com.axiom.correlation.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Prior, completed executions of the same logical test (see {@link TestIdentity}), as supplied by
 * a {@code history.json} file. Raw and immutable, same discipline as the other three evidence
 * types: no {@code flakeRate}, no {@code isFlaky}, no "insufficient sample" judgment stored here —
 * those are signal-extraction's job (not built in this commit; see
 * {@code docs/15-historical-execution-evidence-design.md} §§7-9). This type alone introduces no
 * behavior change anywhere else in the engine — no extractor, rule, or renderer consumes it yet.
 * <p>
 * <b>{@link #runs()} is always newest-first</b> — enforced in this record's own compact
 * constructor (timestamp descending, {@code runId} ascending as a deterministic tie-break for
 * equal timestamps), not left to whichever caller happens to construct this type. Stronger than
 * relying on an adapter as the only legitimate construction path: this record is public, and
 * tests, future adapters, or other programmatic callers can construct it directly. Deduplication
 * of repeated {@code runId}s is deliberately <i>not</i> done here — this type cannot know whether
 * a duplicate is invalid input, a repeated source entry, or evidence intentionally preserved
 * as-is; that judgment belongs to the adapter that has the context to make it.
 */
public record HistoricalExecutionEvidence(
        String evidenceId,
        Instant observedAt,
        TestIdentity testIdentity,
        Optional<String> branch,
        List<HistoricalTestRun> runs
) implements CorrelationEvidence {

    public HistoricalExecutionEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is mandatory");
        if (evidenceId.isBlank()) {
            throw new IllegalArgumentException("evidenceId must not be blank");
        }
        Objects.requireNonNull(observedAt, "observedAt is mandatory");
        Objects.requireNonNull(testIdentity, "testIdentity is mandatory");
        Objects.requireNonNull(branch, "branch is mandatory (use Optional.empty(), not null)");
        Objects.requireNonNull(runs, "runs is mandatory");
        runs = runs.stream()
            .sorted(Comparator.comparing(HistoricalTestRun::timestamp).reversed()
                .thenComparing(HistoricalTestRun::runId))
            .toList();
    }

    @Override
    public EvidenceType type() {
        return EvidenceType.HISTORICAL_EXECUTION;
    }
}
