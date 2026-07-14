package com.axiom.classifier.rule;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A {@link Condition} after RuleProcessor normalization: {@code caseSensitive} is resolved to a
 * concrete value (default {@code false}), and {@code compiledPattern} holds the precompiled
 * regex when {@code operator} is {@link Operator#REGEX} — {@code null} otherwise.
 */
public record PreparedCondition(
    RuleField field,
    Operator operator,
    String value,
    boolean caseSensitive,
    Pattern compiledPattern
) {

    public PreparedCondition {
        Objects.requireNonNull(field, "field is mandatory");
        Objects.requireNonNull(operator, "operator is mandatory");
        Objects.requireNonNull(value, "value is mandatory");

        boolean isRegex = operator == Operator.REGEX;
        if (isRegex && compiledPattern == null) {
            throw new IllegalArgumentException("compiledPattern is mandatory when operator is REGEX");
        }
        if (!isRegex && compiledPattern != null) {
            throw new IllegalArgumentException("compiledPattern must be null when operator is not REGEX");
        }
    }
}
