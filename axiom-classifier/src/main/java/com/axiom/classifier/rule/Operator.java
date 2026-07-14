package com.axiom.classifier.rule;

/**
 * The comparison a {@link Condition} applies between a {@link RuleField}'s actual value and
 * the condition's configured {@code value}.
 */
public enum Operator {
    CONTAINS,
    EQUALS,
    REGEX,
    STARTS_WITH,
    ENDS_WITH
}
