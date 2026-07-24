package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.ConfidenceContribution;
import com.axiom.correlation.model.EvidenceReference;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.RootCauseHypothesis;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Decides {@code DETERMINED} vs. {@code NEEDS_INVESTIGATION}. Selects the top-ranked hypothesis
 * only when three independent conditions all hold:
 * <ol>
 *   <li>confidence clears {@link #CONFIDENCE_THRESHOLD}</li>
 *   <li>it carries no blocking contradiction</li>
 *   <li>it leads the second-ranked hypothesis by at least {@link #MINIMUM_HYPOTHESIS_LEAD}</li>
 * </ol>
 * A close competition between two plausible hypotheses (e.g. application bug vs. infrastructure
 * failure) must produce {@code NEEDS_INVESTIGATION}, not an arbitrary winner —
 * {@code rankedHypotheses} still preserves both, so the ambiguity itself is visible, not just the
 * fact that one was (arbitrarily) picked.
 * <p>
 * <b>Same-category aggregation happens first</b> (see {@link #aggregate}), before any of the
 * above: two rules that agree on a category (e.g. a future {@code TransientFailureRule} and
 * {@code HistoricalFlakyTestRule} both concluding {@code FLAKY_TEST}) must not be forced into
 * competition with each other under the minimum-lead rule — that would be an artifact of rule
 * count, not a real evidentiary conflict. With today's rules (each targeting a distinct category),
 * every category group has exactly one member and {@link #aggregate} is a no-op.
 * <p>
 * Ranking sorts by confidence descending, then rule ids ascending as a stable tie-break — this
 * controls display order only. An exact tie always fails the lead requirement (a 0.0 lead is
 * never {@code >= MINIMUM_HYPOTHESIS_LEAD}), so the tie-break can never turn a tie into a
 * {@code DETERMINED} result.
 */
final class AssessmentSelector {

    static final double CONFIDENCE_THRESHOLD = 0.70;
    static final double MINIMUM_HYPOTHESIS_LEAD = 0.15;

    static RootCauseAssessment select(List<ScoredEvaluation> scored, List<String> missingEvidence) {
        List<ScoredEvaluation> aggregated = scored.stream()
            .collect(Collectors.groupingBy(s -> s.evaluation().category()))
            .values().stream()
            .map(AssessmentSelector::aggregate)
            .toList();

        List<ScoredEvaluation> sorted = aggregated.stream()
            .sorted(Comparator.comparingDouble(ScoredEvaluation::confidence).reversed()
                .thenComparing(s -> String.join(",", s.ruleIds())))
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

    /**
     * Trivial (returns its single element unchanged) when {@code sameCategory} has one member —
     * today's case for every existing rule. For more than one:
     * <p>
     * <b>Confidence is the maximum across paths, never a sum</b> — {@code 0.70 + 0.80} capped at
     * {@code 1.0} would double-count correlated evidence and make a category "stronger" merely for
     * having more rules behind it. The maximum is taken only among <i>unblocked</i> paths — a
     * blocked path's own confidence number must never leak into the aggregate, since it was already
     * vetoed; if every path is blocked, the maximum falls back to all paths purely for the evidence
     * trail's honesty (selection will abstain regardless, since the aggregate's own blocking flag
     * is then {@code true}).
     * <p>
     * <b>Blocking is path-local, not flattened into one category-wide boolean up front</b> — a
     * blocker belonging to one rule must not automatically invalidate a different rule's
     * independent support for the same category. The aggregate is blocked only when {@code every}
     * contributing path is blocked.
     * <p>
     * Every path's contributions/evidence are concatenated, never summed away — full transparency,
     * and per-path provenance stays reconstructable via {@link ConfidenceContribution#ruleId()} on
     * each entry without a separate {@code ReasoningPath} type.
     */
    private static ScoredEvaluation aggregate(List<ScoredEvaluation> sameCategory) {
        if (sameCategory.size() == 1) {
            return sameCategory.get(0);
        }

        List<ScoredEvaluation> unblocked = sameCategory.stream()
            .filter(s -> !s.evaluation().hasBlockingContradiction())
            .toList();
        boolean allBlocked = unblocked.isEmpty();
        List<ScoredEvaluation> confidenceCandidates = allBlocked ? sameCategory : unblocked;
        double maxConfidence = confidenceCandidates.stream()
            .mapToDouble(ScoredEvaluation::confidence)
            .max()
            .orElse(0.0);

        List<String> ruleIds = sameCategory.stream()
            .flatMap(s -> s.ruleIds().stream())
            .sorted()
            .toList();
        List<ConfidenceContribution> contributions = sameCategory.stream()
            .flatMap(s -> s.evaluation().contributions().stream())
            .toList();
        List<EvidenceReference> supporting = sameCategory.stream()
            .flatMap(s -> s.evaluation().supporting().stream())
            .toList();
        List<EvidenceReference> contradicting = sameCategory.stream()
            .flatMap(s -> s.evaluation().contradicting().stream())
            .toList();
        FailureCategory category = sameCategory.get(0).evaluation().category();

        RuleEvaluation aggregatedEvaluation =
            new RuleEvaluation(category, contributions, supporting, contradicting, allBlocked);
        return new ScoredEvaluation(ruleIds, aggregatedEvaluation, maxConfidence);
    }

    private AssessmentSelector() {
    }
}
