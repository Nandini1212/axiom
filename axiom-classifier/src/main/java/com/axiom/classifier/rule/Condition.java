package com.axiom.classifier.rule;

import java.util.Objects;

/**
 * A single field/operator/value comparison within a {@link MatchGroup}.
 * <p>
 * {@code caseSensitive} is nullable rather than defaulted here: {@code null} means the rule
 * author left it unspecified, and the RuleProcessor stage (not this record) decides the actual
 * default. This mirrors how {@code FailureEvent} in axiom-common leaves genuinely-unspecified
 * optional fields as {@code null} rather than a misleading sentinel.
 */
public record Condition(RuleField field, Operator operator, String value, Boolean caseSensitive) {

    public Condition {
        Objects.requireNonNull(field, "field is mandatory");
        Objects.requireNonNull(operator, "operator is mandatory");
        Objects.requireNonNull(value, "value is mandatory");
    }
}
