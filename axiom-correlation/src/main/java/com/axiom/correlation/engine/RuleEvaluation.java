package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.ConfidenceContribution;
import com.axiom.correlation.model.EvidenceReference;

import java.util.List;
import java.util.Objects;

/**
 * What one {@link CorrelationRule} concluded, before scoring/selection. {@code category} is
 * always a real {@link FailureCategory} — never an abstention marker (see
 * {@code AssessmentDisposition}).
 * <p>
 * {@code hasBlockingContradiction} is a hard veto distinct from the numeric confidence sum: a
 * rule-specific judgment that certain contradicting signals (e.g. the retry actually passed)
 * should prevent {@code DETERMINED} outright, not merely lower the score enough to fall under the
 * confidence threshold incidentally. Kept explicit rather than relying on the current weights
 * happening to always push a contradicted hypothesis below the threshold — that would be a
 * coincidence of today's specific numbers, not a guaranteed invariant future weight tuning could
 * quietly break.
 */
public record RuleEvaluation(
        FailureCategory category,
        List<ConfidenceContribution> contributions,
        List<EvidenceReference> supporting,
        List<EvidenceReference> contradicting,
        boolean hasBlockingContradiction
) {

    public RuleEvaluation {
        Objects.requireNonNull(category, "category is mandatory");
        Objects.requireNonNull(contributions, "contributions is mandatory");
        Objects.requireNonNull(supporting, "supporting is mandatory");
        Objects.requireNonNull(contradicting, "contradicting is mandatory");
        contributions = List.copyOf(contributions);
        supporting = List.copyOf(supporting);
        contradicting = List.copyOf(contradicting);
    }
}
