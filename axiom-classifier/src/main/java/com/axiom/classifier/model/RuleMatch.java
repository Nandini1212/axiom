package com.axiom.classifier.model;

import java.util.List;
import java.util.Objects;

/**
 * One rule that matched a given {@code com.axiom.common.model.FailureEvent}, as produced by
 * {@code com.axiom.classifier.engine.RuleEngine}.
 * <p>
 * {@code priority} is carried here rather than requiring callers to look back at the original
 * {@code PreparedRule} — this is the engine's self-contained output contract, same reasoning as
 * why {@code PreparedRule} itself flattens {@code ClassificationSpec}/{@code EvidenceSpec}.
 * <p>
 * {@code evidence} is a list, not a single value: a matched rule can have more than one
 * contributing condition (e.g. every condition in an {@code all} group, or several satisfied
 * conditions in an {@code any} group), and each deserves its own record rather than collapsing
 * to just the first one found.
 */
public record RuleMatch(
    String ruleId,
    int priority,
    FailureCategory category,
    double confidence,
    List<Evidence> evidence
) {

    public RuleMatch {
        Objects.requireNonNull(ruleId, "ruleId is mandatory");
        Objects.requireNonNull(category, "category is mandatory");
        Objects.requireNonNull(evidence, "evidence is mandatory");
        evidence = List.copyOf(evidence);
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("evidence must not be empty for a matched rule");
        }
    }
}
