package com.axiom.correlation.model;

/**
 * The outcome of one prior execution of a test, as recorded in {@code history.json}. Deliberately
 * narrow — just what a completed run can tell us directly, same reasoning as
 * {@code com.axiom.common.model.FailureStatus} staying narrow rather than trying to also encode
 * root-cause judgments.
 */
public enum HistoricalOutcome {
    PASSED,
    FAILED
}
