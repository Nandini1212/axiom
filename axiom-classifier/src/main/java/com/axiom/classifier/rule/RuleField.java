package com.axiom.classifier.rule;

/**
 * The failure-event fields a {@link Condition} may match against (mirrors the
 * {@code message}/{@code stackTrace}/{@code testName}/{@code className}/{@code suiteName}
 * fields axiom-common's {@code FailureEvent} exposes). Deliberately closed rather than a
 * free-form string: an invalid field name fails at YAML deserialization instead of silently
 * no-op'ing during rule evaluation.
 */
public enum RuleField {
    MESSAGE,
    STACK_TRACE,
    TEST_NAME,
    CLASS_NAME,
    SUITE_NAME
}
