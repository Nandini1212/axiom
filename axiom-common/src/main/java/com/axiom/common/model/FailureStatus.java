package com.axiom.common.model;

/**
 * The outcome status of a single test, as reported by the source test framework.
 * <p>
 * This is deliberately narrow — it reflects only what a JUnit/TestNG report can tell us
 * directly. Root-cause classification (application bug, infra failure, flaky, etc.) is
 * a separate, downstream concern owned by the classifier module, not this enum.
 */
public enum FailureStatus {

    /** The test ran and one or more assertions failed. */
    FAILED,

    /** The test raised an unexpected exception/error rather than failing an assertion. */
    ERROR,

    /** The test was skipped/ignored and did not execute. */
    SKIPPED,

    /** The source report did not clearly indicate one of the above. */
    UNKNOWN
}
