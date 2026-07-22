package com.axiom.correlation.model;

import com.axiom.classifier.model.FailureCategory;

import java.util.List;
import java.util.Objects;

/**
 * One candidate root cause a {@code CorrelationRule} produced, with its full evidence trail.
 * {@code category} is always a real {@link FailureCategory} value — never an abstention marker;
 * whether the overall assessment accepts this (or any) hypothesis is a separate question, answered
 * by {@code RootCauseAssessment.disposition}, not by anything stored here.
 */
public record RootCauseHypothesis(
        FailureCategory category,
        double confidence,
        List<EvidenceReference> supportingEvidence,
        List<EvidenceReference> contradictingEvidence,
        List<ConfidenceContribution> contributions,
        String matchedReasoningPath
) {

    public RootCauseHypothesis {
        Objects.requireNonNull(category, "category is mandatory");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(supportingEvidence, "supportingEvidence is mandatory");
        Objects.requireNonNull(contradictingEvidence, "contradictingEvidence is mandatory");
        Objects.requireNonNull(contributions, "contributions is mandatory");
        Objects.requireNonNull(matchedReasoningPath, "matchedReasoningPath is mandatory");
        supportingEvidence = List.copyOf(supportingEvidence);
        contradictingEvidence = List.copyOf(contradictingEvidence);
        contributions = List.copyOf(contributions);
    }
}
