package com.axiom.correlation.model;

import java.time.Instant;

/**
 * A normalized input observation from one of four independent sources, collected before any
 * correlation rule runs.
 * <p>
 * Deliberately not named {@code Evidence} — {@code com.axiom.classifier.model.Evidence} already
 * exists and means something unrelated (why one rule condition matched). Reusing that name here
 * would put two unrelated types one import away from each other in any file that needs both.
 */
public sealed interface CorrelationEvidence
        permits TestFailureEvidence, SourceChangeEvidence, ExecutionEvidence, HistoricalExecutionEvidence {

    String evidenceId();

    EvidenceType type();

    Instant observedAt();
}
