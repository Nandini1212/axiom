package com.axiom.correlation.model;

import com.axiom.classifier.model.FailureCategory;

import java.util.List;
import java.util.Objects;

/**
 * One candidate root cause a {@code CorrelationRule} (or several, aggregated — see
 * {@code AssessmentSelector}) produced, with its full evidence trail. {@code category} is always a
 * real {@link FailureCategory} value — never an abstention marker; whether the overall assessment
 * accepts this (or any) hypothesis is a separate question, answered by
 * {@code RootCauseAssessment.disposition}, not by anything stored here.
 * <p>
 * {@code matchedReasoningPaths} holds stable rule ids only — never renderer-ready text. Usually
 * one id; more than one when {@code AssessmentSelector} aggregates multiple same-category rules
 * into a single hypothesis (e.g. {@code TransientFailureRule} and {@code HistoricalFlakyTestRule}
 * both concluding {@code FLAKY_TEST}). Formatting a display label from this list (singular vs.
 * plural wording, separators) is the renderer's job, not this type's.
 */
public record RootCauseHypothesis(
        FailureCategory category,
        double confidence,
        List<EvidenceReference> supportingEvidence,
        List<EvidenceReference> contradictingEvidence,
        List<ConfidenceContribution> contributions,
        List<String> matchedReasoningPaths
) {

    public RootCauseHypothesis {
        Objects.requireNonNull(category, "category is mandatory");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(supportingEvidence, "supportingEvidence is mandatory");
        Objects.requireNonNull(contradictingEvidence, "contradictingEvidence is mandatory");
        Objects.requireNonNull(contributions, "contributions is mandatory");
        Objects.requireNonNull(matchedReasoningPaths, "matchedReasoningPaths is mandatory");
        if (matchedReasoningPaths.isEmpty()) {
            throw new IllegalArgumentException("matchedReasoningPaths must not be empty");
        }
        supportingEvidence = List.copyOf(supportingEvidence);
        contradictingEvidence = List.copyOf(contradictingEvidence);
        contributions = List.copyOf(contributions);
        matchedReasoningPaths = List.copyOf(matchedReasoningPaths);
    }
}
