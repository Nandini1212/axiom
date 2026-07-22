package com.axiom.correlation.signal;

/**
 * A derived fact extracted from {@code CorrelationEvidence}, consumed by {@code CorrelationRule}s.
 * Exactly the five signals this v0.1 slice needs — not the full set a later milestone's remaining
 * rules will require.
 */
public enum SignalType {
    STACK_FRAME_MATCHES_CHANGED_FILE,
    TOP_FRAME_IS_TEST_CODE,
    RETRY_PASSED,
    RETRY_FAILED,
    CHANGE_SET_EVIDENCE_MISSING
}
