package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;

import java.util.Objects;

/**
 * The classification a {@link RuleDefinition} produces when its {@link MatchGroup} matches.
 * <p>
 * {@code confidence} is validated here, not deferred to the RuleProcessor stage: an
 * out-of-range confidence is an invariant of the value itself (like a blank id or an
 * any/all-both-empty {@link MatchGroup}), not a rule-set-level default to be resolved later.
 */
public record ClassificationSpec(FailureCategory category, double confidence) {

    public ClassificationSpec {
        Objects.requireNonNull(category, "category is mandatory");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
