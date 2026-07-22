package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.RootCauseHypothesis;

import java.util.List;
import java.util.Optional;

/**
 * Decides {@code DETERMINED} vs. {@code NEEDS_INVESTIGATION}. Selects the top-ranked hypothesis
 * only when its confidence clears the threshold <b>and</b> it carries no blocking contradiction —
 * both conditions checked independently, not assuming one implies the other.
 * <p>
 * "Lead over second-ranked hypothesis" from the original design sketch is not implemented: with
 * exactly one rule in this slice, there is never a second hypothesis to compare against. Add that
 * comparison when a second rule actually exists, not speculatively now.
 */
final class AssessmentSelector {

    static final double CONFIDENCE_THRESHOLD = 0.70;

    static RootCauseAssessment select(List<ScoredEvaluation> scored, List<String> missingEvidence) {
        List<RootCauseHypothesis> ranked = scored.stream()
            .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
            .map(ScoredEvaluation::toHypothesis)
            .toList();

        Optional<ScoredEvaluation> top = scored.stream()
            .max((a, b) -> Double.compare(a.confidence(), b.confidence()));

        boolean determined = top.isPresent()
            && top.get().confidence() >= CONFIDENCE_THRESHOLD
            && !top.get().evaluation().hasBlockingContradiction();

        if (determined) {
            FailureCategory selected = top.get().evaluation().category();
            return new RootCauseAssessment(
                ranked, AssessmentDisposition.DETERMINED, Optional.of(selected), missingEvidence);
        }
        return new RootCauseAssessment(
            ranked, AssessmentDisposition.NEEDS_INVESTIGATION, Optional.empty(), missingEvidence);
    }

    private AssessmentSelector() {
    }
}
