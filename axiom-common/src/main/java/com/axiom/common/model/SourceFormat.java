package com.axiom.common.model;

/**
 * The report format that a {@link FailureEvent} was normalized from.
 * <p>
 * Retained on the normalized event (rather than discarded after parsing) because
 * downstream classification rules and AI prompts may reasonably care where a
 * failure report originated — for example, TestNG's retry/data-provider semantics
 * differ from plain JUnit.
 */
public enum SourceFormat {
    JUNIT,
    TESTNG,
    UNKNOWN
}
