package com.axiom.correlation.model;

/**
 * The wire-format shape an {@code execution.json} file deserializes into. {@code retryAttempted}
 * is separate from {@code retryPassed} rather than using a nullable {@code Boolean} for "no retry
 * happened" — a plain boolean pair keeps "not attempted" and "attempted and failed" unambiguous.
 */
public record ExecutionInput(boolean retryAttempted, boolean retryPassed, int relatedFailureCount) {
}
