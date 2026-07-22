package com.axiom.correlation.engine;

import com.axiom.correlation.model.ConfidenceContribution;

import java.util.List;

/**
 * Sums a rule's signed {@link ConfidenceContribution}s and clamps to {@code [0.0, 1.0]} — the same
 * range invariant {@code ClassificationResult.confidence} already enforces, so both engines share
 * one convention.
 */
final class HypothesisScorer {

    static double score(List<ConfidenceContribution> contributions) {
        double sum = contributions.stream().mapToDouble(ConfidenceContribution::weight).sum();
        return Math.max(0.0, Math.min(1.0, sum));
    }

    private HypothesisScorer() {
    }
}
