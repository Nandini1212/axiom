package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.ExecutionEvidence;

import java.util.List;
import java.util.Optional;

/**
 * Emits the complementary {@code RETRY_PASSED}/{@code RETRY_FAILED} pair from one
 * {@link ExecutionEvidence} instance. Both are absent (present = false) when no retry was
 * attempted or no execution evidence was supplied at all — "no retry information" is not the same
 * as either outcome.
 */
public final class RetryOutcomeExtractor implements SignalExtractor {

    @Override
    public List<Signal> extract(List<CorrelationEvidence> evidence) {
        Optional<ExecutionEvidence> execution =
            EvidenceLookup.find(evidence, EvidenceType.EXECUTION, ExecutionEvidence.class);

        if (execution.isEmpty() || !execution.get().retryAttempted()) {
            return List.of(
                new Signal(SignalType.RETRY_PASSED, false, List.of()),
                new Signal(SignalType.RETRY_FAILED, false, List.of()));
        }

        ExecutionEvidence exec = execution.get();
        List<String> evidenceIds = List.of(exec.evidenceId());
        return List.of(
            new Signal(SignalType.RETRY_PASSED, exec.retryPassed(), exec.retryPassed() ? evidenceIds : List.of()),
            new Signal(SignalType.RETRY_FAILED, !exec.retryPassed(), !exec.retryPassed() ? evidenceIds : List.of()));
    }
}
