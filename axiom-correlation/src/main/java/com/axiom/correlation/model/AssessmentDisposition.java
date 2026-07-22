package com.axiom.correlation.model;

/**
 * Whether {@code RootCauseAssessment} accepted a hypothesis or abstained. Deliberately separate
 * from {@code FailureCategory} — {@code NEEDS_INVESTIGATION} is an assessment outcome, not a
 * root-cause category; a hypothesis is always a real candidate cause, never "needs investigation."
 */
public enum AssessmentDisposition {
    DETERMINED,
    NEEDS_INVESTIGATION
}
