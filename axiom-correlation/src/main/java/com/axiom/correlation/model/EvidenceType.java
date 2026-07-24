package com.axiom.correlation.model;

/**
 * Which of the four independent sources a {@link CorrelationEvidence} instance came from.
 */
public enum EvidenceType {
    TEST_FAILURE,
    SOURCE_CHANGE,
    EXECUTION,
    HISTORICAL_EXECUTION
}
