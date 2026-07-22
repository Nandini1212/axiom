package com.axiom.correlation.model;

import com.axiom.classifier.model.FailureCategory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The final ranked collection of hypotheses plus the disposition of whether one was accepted.
 * {@code disposition}/{@code selectedCategory} mirror {@code AnalyzedFailure.explanation} being
 * {@code Optional}, not overloading one field to mean several states — the compact constructor
 * makes the two fields impossible to contradict each other.
 */
public record RootCauseAssessment(
        List<RootCauseHypothesis> rankedHypotheses,
        AssessmentDisposition disposition,
        Optional<FailureCategory> selectedCategory,
        List<String> missingEvidence
) {

    public RootCauseAssessment {
        Objects.requireNonNull(rankedHypotheses, "rankedHypotheses is mandatory");
        Objects.requireNonNull(disposition, "disposition is mandatory");
        Objects.requireNonNull(selectedCategory, "selectedCategory is mandatory (use Optional.empty(), not null)");
        Objects.requireNonNull(missingEvidence, "missingEvidence is mandatory");
        rankedHypotheses = List.copyOf(rankedHypotheses);
        missingEvidence = List.copyOf(missingEvidence);

        if (disposition == AssessmentDisposition.DETERMINED && selectedCategory.isEmpty()) {
            throw new IllegalArgumentException("DETERMINED assessment must carry a selectedCategory");
        }
        if (disposition == AssessmentDisposition.NEEDS_INVESTIGATION && selectedCategory.isPresent()) {
            throw new IllegalArgumentException("NEEDS_INVESTIGATION assessment must not carry a selectedCategory");
        }
    }
}
