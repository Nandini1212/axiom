package com.axiom.classifier.model;

import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.RuleField;

import java.util.Objects;

/**
 * The concrete reason one condition contributed to a {@link RuleMatch}: which field was checked,
 * how, what value was expected, what value the failure actually had, and the rule author's
 * explanation for what that means.
 * <p>
 * Named {@code actualValue}, not {@code actualExcerpt}: this is a domain object describing what
 * matched, not a presentation-layer concern about how much of a long value to display.
 */
public record Evidence(
    RuleField field,
    Operator operator,
    String expectedValue,
    String actualValue,
    String explanation
) {

    public Evidence {
        Objects.requireNonNull(field, "field is mandatory");
        Objects.requireNonNull(operator, "operator is mandatory");
        Objects.requireNonNull(expectedValue, "expectedValue is mandatory");
        Objects.requireNonNull(actualValue, "actualValue is mandatory");
    }
}
