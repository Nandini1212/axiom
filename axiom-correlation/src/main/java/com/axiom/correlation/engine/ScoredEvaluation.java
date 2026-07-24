package com.axiom.correlation.engine;

import com.axiom.correlation.model.RootCauseHypothesis;

import java.util.List;

/**
 * Package-private: pairs a {@link RuleEvaluation} with its contributing rule id(s) and computed
 * confidence, so {@link AssessmentSelector} can consult {@code hasBlockingContradiction} — which
 * {@link RootCauseHypothesis} deliberately does not carry — without re-deriving it.
 * <p>
 * {@code ruleIds} is a list, not a single id, because {@link AssessmentSelector}'s same-category
 * aggregation can combine more than one rule's evaluation into a single {@code ScoredEvaluation}
 * — one per contributing rule before aggregation, more than one after.
 */
record ScoredEvaluation(List<String> ruleIds, RuleEvaluation evaluation, double confidence) {

    RootCauseHypothesis toHypothesis() {
        return new RootCauseHypothesis(
            evaluation.category(),
            confidence,
            evaluation.supporting(),
            evaluation.contradicting(),
            evaluation.contributions(),
            ruleIds);
    }
}
