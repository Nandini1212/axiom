package com.axiom.correlation.model;

/**
 * The wire-format shape an {@code execution.json} file deserializes into. {@code retryAttempted}
 * is separate from {@code retryPassed} rather than using a nullable {@code Boolean} for "no retry
 * happened" — a plain boolean pair keeps "not attempted" and "attempted and failed" unambiguous.
 * <p>
 * {@code relatedFailureCount} is the number of <b>additional</b> failures observed alongside this
 * one — it does not include the current test itself. A value of {@code 0} means this failure was
 * the only one in its execution window; {@code > 0} means it failed alongside others.
 */
public record ExecutionInput(boolean retryAttempted, boolean retryPassed, int relatedFailureCount) {
}
