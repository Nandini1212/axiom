package com.axiom.classifier.engine;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.classifier.model.RuleMatch;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ranks matches by priority descending, then confidence descending, then rule id ascending, and
 * returns the top-ranked match. Priority is absolute — a lower-priority match never wins over a
 * higher-priority one no matter its confidence; confidence only breaks ties within the same
 * priority; id only breaks ties where both priority and confidence are equal, purely for
 * deterministic output.
 * <p>
 * Ranks independently of input order rather than trusting {@code matches.get(0)} to already be
 * the winner — {@link RuleEngine} happens to preserve {@code RuleProcessor}'s priority order
 * today, but relying on that here would couple two components whose whole point is to have
 * separate responsibilities.
 */
public final class DeterministicStrategy implements ClassificationStrategy {

    private static final Comparator<RuleMatch> RANKING = Comparator
        .comparingInt(RuleMatch::priority).reversed()
        .thenComparing(Comparator.comparingDouble(RuleMatch::confidence).reversed())
        .thenComparing(RuleMatch::ruleId);

    @Override
    public ClassificationResult classify(List<RuleMatch> matches) {
        Objects.requireNonNull(matches, "matches is mandatory");

        if (matches.isEmpty()) {
            return new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of());
        }

        RuleMatch winner = matches.stream().min(RANKING).orElseThrow();
        return new ClassificationResult(
            winner.category(), winner.confidence(), winner.ruleId(), winner.evidence());
    }
}
