package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;

import java.util.Objects;

/**
 * A {@link RuleDefinition} after RuleProcessor normalization: runtime-ready for RuleEngine.
 * <p>
 * Flattened rather than nesting {@link ClassificationSpec}/{@link EvidenceSpec} — those are
 * YAML-binding shapes only, and downstream code (RuleEngine, ClassificationResult) reads
 * {@code category}/{@code confidence}/{@code evidenceMessage} directly rather than chaining
 * through {@code .classification().category()}.
 * <p>
 * {@code enabled} is deliberately absent: RuleProcessor filters out disabled rules entirely, so
 * every {@code PreparedRule} that exists is implicitly enabled. {@code priority} is resolved
 * (default {@code 0} when unspecified in the source {@link RuleDefinition}) and unbounded — it is
 * a pure ordering key, not a value with an intrinsic range like {@code confidence}.
 */
public record PreparedRule(
    String id,
    int priority,
    PreparedMatchGroup match,
    FailureCategory category,
    double confidence,
    String evidenceMessage
) {

    public PreparedRule {
        Objects.requireNonNull(id, "id is mandatory");
        Objects.requireNonNull(match, "match is mandatory");
        Objects.requireNonNull(category, "category is mandatory");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
