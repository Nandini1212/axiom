package com.axiom.correlation.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The domain evidence type built from an {@link ExecutionInput}. {@code relatedFailureCount} is
 * the number of <i>additional</i> failures observed alongside this one — not including the
 * current test itself; see {@link ExecutionInput} for the exact semantics. Consumed by
 * {@code FailureClusterPresentExtractor}.
 */
public record ExecutionEvidence(
        String evidenceId,
        Instant observedAt,
        boolean retryAttempted,
        boolean retryPassed,
        int relatedFailureCount
) implements CorrelationEvidence {

    public ExecutionEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is mandatory");
        Objects.requireNonNull(observedAt, "observedAt is mandatory");
    }

    @Override
    public EvidenceType type() {
        return EvidenceType.EXECUTION;
    }

    public static ExecutionEvidence from(String evidenceId, Instant observedAt, ExecutionInput input) {
        Objects.requireNonNull(input, "input is mandatory");
        return new ExecutionEvidence(
            evidenceId, observedAt, input.retryAttempted(), input.retryPassed(), input.relatedFailureCount());
    }
}
