package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.ExecutionEvidence;

import java.util.List;
import java.util.Optional;

/**
 * Present when {@code ExecutionEvidence.relatedFailureCount() > 0} — other failures occurred
 * alongside this one. See {@link SignalType#FAILURE_CLUSTER_PRESENT} for why this is named for
 * exactly what's known, not a stronger claim about the failures being related.
 */
public final class FailureClusterPresentExtractor implements SignalExtractor {

    @Override
    public List<Signal> extract(List<CorrelationEvidence> evidence) {
        Optional<ExecutionEvidence> execution =
            EvidenceLookup.find(evidence, EvidenceType.EXECUTION, ExecutionEvidence.class);

        boolean present = execution.isPresent() && execution.get().relatedFailureCount() > 0;
        return List.of(new Signal(
            SignalType.FAILURE_CLUSTER_PRESENT,
            present,
            present ? List.of(execution.get().evidenceId()) : List.of()));
    }
}
