package com.axiom.classifier.model;

import java.util.List;
import java.util.Objects;

/**
 * The final answer {@code ClassificationStrategy} produces for one {@code FailureEvent}: the
 * winning {@link RuleMatch}'s fields, reshaped as the platform's classification result.
 * <p>
 * Carries only the winner — no list of runner-up matches. {@code ClassificationStrategy} receives
 * the complete set of matches and selects from it, so that full set remains available to the
 * caller before the strategy runs; a separate diagnostic/decision-trace model can wrap that later
 * if reporting or AI ever needs runner-up visibility, rather than this type carrying it
 * preemptively.
 */
public record ClassificationResult(
    FailureCategory category,
    double confidence,
    String matchedRuleId,
    List<Evidence> evidence
) {

    public ClassificationResult {
        Objects.requireNonNull(category, "category is mandatory");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(evidence, "evidence is mandatory");
        evidence = List.copyOf(evidence);
    }
}
