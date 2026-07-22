package com.axiom.correlation.engine;

import com.axiom.correlation.model.RootCauseHypothesis;

/**
 * Package-private: pairs a {@link RuleEvaluation} with its rule id and computed confidence, so
 * {@link AssessmentSelector} can consult {@code hasBlockingContradiction} — which
 * {@link RootCauseHypothesis} deliberately does not carry — without re-deriving it.
 */
record ScoredEvaluation(String ruleId, RuleEvaluation evaluation, double confidence) {

    RootCauseHypothesis toHypothesis() {
        return new RootCauseHypothesis(
            evaluation.category(),
            confidence,
            evaluation.supporting(),
            evaluation.contradicting(),
            evaluation.contributions(),
            ruleId);
    }
}
