package com.axiom.correlation.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The domain evidence type built from an {@link ExecutionInput}. {@code relatedFailureCount} is
 * carried even though no signal in this slice consumes it yet — honest input modeling for a field
 * a future rule (e.g. infrastructure-outage correlation) will need, not a used signal today.
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
