package com.axiom.correlation.model;

/**
 * Which of the three independent sources a {@link CorrelationEvidence} instance came from.
 */
public enum EvidenceType {
    TEST_FAILURE,
    SOURCE_CHANGE,
    EXECUTION
}
