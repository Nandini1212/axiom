package com.axiom.correlation.signal;

/**
 * A derived fact extracted from {@code CorrelationEvidence}, consumed by {@code CorrelationRule}s.
 * Signals represent facts only — which rule interprets a given signal as supporting or
 * contradicting (e.g. {@code RETRY_PASSED} is contradicting for
 * {@code ApplicationBugCorrelationRule} but supporting for {@code InfrastructureFailureRule}) is
 * entirely up to the rule, not encoded here.
 */
public enum SignalType {
    STACK_FRAME_MATCHES_CHANGED_FILE,
    TOP_FRAME_IS_TEST_CODE,
    RETRY_PASSED,
    RETRY_FAILED,
    CHANGE_SET_EVIDENCE_MISSING,

    /**
     * Present when {@code ExecutionEvidence.relatedFailureCount() > 0} — other failures occurred
     * alongside this one. Deliberately not named {@code MULTIPLE_UNRELATED_TESTS_FAILED}: the
     * evidence only proves other tests failed at the same time, not that they were unrelated to
     * each other or to this one. Keep the name honest about what's actually known; a stronger
     * signal (e.g. {@code UNRELATED_TESTS_SHARE_COMMON_INFRA_FAILURE}) can be derived later once
     * Axiom can actually compare services/packages/ownership across failures.
     */
    FAILURE_CLUSTER_PRESENT
}
