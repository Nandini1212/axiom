package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.RootCauseHypothesis;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides {@code DETERMINED} vs. {@code NEEDS_INVESTIGATION}. Selects the top-ranked hypothesis
 * only when three independent conditions all hold:
 * <ol>
 *   <li>confidence clears {@link #CONFIDENCE_THRESHOLD}</li>
 *   <li>it carries no blocking contradiction</li>
 *   <li>it leads the second-ranked hypothesis by at least {@link #MINIMUM_HYPOTHESIS_LEAD}</li>
 * </ol>
 * The third condition only matters once a second rule can fire on the same evidence — with one
 * rule (v0.1), there is never a second hypothesis, so it never blocks selection. A close
 * competition between two plausible hypotheses (e.g. application bug vs. infrastructure failure)
 * must produce {@code NEEDS_INVESTIGATION}, not an arbitrary winner — {@code rankedHypotheses}
 * still preserves both, so the ambiguity itself is visible, not just the fact that one was
 * (arbitrarily) picked.
 * <p>
 * Ranking sorts by confidence descending, then rule id ascending as a stable tie-break — this
 * controls display order only. An exact tie always fails the lead requirement (a 0.0 lead is
 * never {@code >= MINIMUM_HYPOTHESIS_LEAD}), so the tie-break can never turn a tie into a
 * {@code DETERMINED} result.
 */
final class AssessmentSelector {

    static final double CONFIDENCE_THRESHOLD = 0.70;
    static final double MINIMUM_HYPOTHESIS_LEAD = 0.15;

    static RootCauseAssessment select(List<ScoredEvaluation> scored, List<String> missingEvidence) {
        List<ScoredEvaluation> sorted = scored.stream()
            .sorted(Comparator.comparingDouble(ScoredEvaluation::confidence).reversed()
                .thenComparing(ScoredEvaluation::ruleId))
            .toList();

        List<RootCauseHypothesis> ranked = sorted.stream().map(ScoredEvaluation::toHypothesis).toList();

        Optional<ScoredEvaluation> top = sorted.stream().findFirst();
        Optional<ScoredEvaluation> second = sorted.size() > 1 ? Optional.of(sorted.get(1)) : Optional.empty();

        boolean hasSufficientLead = second.isEmpty()
            || top.get().confidence() - second.get().confidence() >= MINIMUM_HYPOTHESIS_LEAD;

        boolean determined = top.isPresent()
            && top.get().confidence() >= CONFIDENCE_THRESHOLD
            && !top.get().evaluation().hasBlockingContradiction()
            && hasSufficientLead;

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
