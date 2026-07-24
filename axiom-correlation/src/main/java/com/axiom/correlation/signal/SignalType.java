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
    FAILURE_CLUSTER_PRESENT,

    // --- Historical execution signals (docs/15-historical-execution-evidence-design.md §9) ---
    // The first two are *context* signals — they describe the sample itself, not its shape.
    // The three after them describe *outcome shape* and are meaningless without knowing the
    // sample is non-trivial (see HISTORICAL_ALWAYS_PASSED/FAILED's vacuous-truth guard).

    /** At least one historical run exists at all — a pure context fact, no threshold involved. */
    HISTORICAL_EXECUTION_PRESENT,

    /**
     * Run count meets {@link HistoricalExecutionPolicy#MINIMUM_USABLE_RUNS} — the one signal in
     * this set that encodes a policy threshold rather than a raw observation. Kept as its own
     * signal (not folded into the others) so a rule can require a sufficient sample independently
     * of what that sample shows.
     */
    HISTORICAL_SAMPLE_SUFFICIENT,

    /** At least one PASSED and at least one FAILED run exist in the sample. */
    HISTORICAL_MIXED_OUTCOMES,

    /** Sample is non-empty and every run PASSED — never true for an empty sample. */
    HISTORICAL_ALWAYS_PASSED,

    /** Sample is non-empty and every run FAILED — never true for an empty sample. */
    HISTORICAL_ALWAYS_FAILED
}
