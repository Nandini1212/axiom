package com.axiom.classifier.model;

/**
 * The root-cause category a {@link com.axiom.classifier.rule.RuleDefinition} classifies a
 * failure into. Shared across rule authoring and the eventual classification result, not
 * specific to how rules are parsed.
 */
public enum FailureCategory {
    APPLICATION_BUG,
    TEST_AUTOMATION_BUG,
    INFRASTRUCTURE_FAILURE,
    DEPLOYMENT_FAILURE,
    ENVIRONMENT_FAILURE,
    CONFIGURATION_FAILURE,
    DATA_ISSUE,
    DEPENDENCY_FAILURE,
    FLAKY_TEST,
    UNKNOWN
}
