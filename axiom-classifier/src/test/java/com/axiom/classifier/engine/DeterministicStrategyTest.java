package com.axiom.classifier.engine;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.Evidence;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.classifier.model.RuleMatch;
import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.RuleField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicStrategyTest {

    private final DeterministicStrategy strategy = new DeterministicStrategy();

    private static RuleMatch match(String id, int priority, FailureCategory category, double confidence) {
        Evidence evidence = new Evidence(RuleField.MESSAGE, Operator.CONTAINS, "x", "x happened", null);
        return new RuleMatch(id, priority, category, confidence, List.of(evidence));
    }

    @Test
    void emptyMatchesClassifiesAsUnknownWithZeroConfidence() {
        ClassificationResult result = strategy.classify(List.of());

        assertEquals(FailureCategory.UNKNOWN, result.category());
        assertEquals(0.0, result.confidence());
        assertNull(result.matchedRuleId());
        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void singleMatchWinsTrivially() {
        RuleMatch only = match("r1", 50, FailureCategory.FLAKY_TEST, 0.6);

        ClassificationResult result = strategy.classify(List.of(only));

        assertEquals("r1", result.matchedRuleId());
        assertEquals(FailureCategory.FLAKY_TEST, result.category());
        assertEquals(0.6, result.confidence());
    }

    @Test
    void higherPriorityWinsRegardlessOfLowerConfidence() {
        RuleMatch highPriorityLowConfidence = match("high", 100, FailureCategory.INFRASTRUCTURE_FAILURE, 0.5);
        RuleMatch lowPriorityHighConfidence = match("low", 10, FailureCategory.FLAKY_TEST, 0.99);

        ClassificationResult result = strategy.classify(
            List.of(highPriorityLowConfidence, lowPriorityHighConfidence));

        assertEquals("high", result.matchedRuleId());
    }

    @Test
    void confidenceBreaksPriorityTie() {
        RuleMatch a = match("a", 100, FailureCategory.INFRASTRUCTURE_FAILURE, 0.8);
        RuleMatch b = match("b", 100, FailureCategory.DEPENDENCY_FAILURE, 0.95);
        RuleMatch c = match("c", 90, FailureCategory.FLAKY_TEST, 0.99);

        // Worked example from design review: c has the highest confidence but loses on priority;
        // between a and b (tied priority), b wins on confidence.
        ClassificationResult result = strategy.classify(List.of(a, b, c));

        assertEquals("b", result.matchedRuleId());
        assertEquals(FailureCategory.DEPENDENCY_FAILURE, result.category());
        assertEquals(0.95, result.confidence());
    }

    @Test
    void idBreaksPriorityAndConfidenceTie() {
        RuleMatch zRule = match("z-rule", 50, FailureCategory.UNKNOWN, 0.7);
        RuleMatch aRule = match("a-rule", 50, FailureCategory.UNKNOWN, 0.7);

        ClassificationResult result = strategy.classify(List.of(zRule, aRule));

        assertEquals("a-rule", result.matchedRuleId());
    }

    @Test
    void winnerIsIndependentOfInputOrder() {
        RuleMatch high = match("high", 100, FailureCategory.INFRASTRUCTURE_FAILURE, 0.9);
        RuleMatch low = match("low", 10, FailureCategory.FLAKY_TEST, 0.99);

        // Deliberately scrambled order: strategy must not assume matches.get(0) is the winner.
        ClassificationResult firstOrder = strategy.classify(List.of(low, high));
        ClassificationResult secondOrder = strategy.classify(List.of(high, low));

        assertEquals("high", firstOrder.matchedRuleId());
        assertEquals("high", secondOrder.matchedRuleId());
    }

    @Test
    void losingMatchEvidenceIsNotIncludedInResult() {
        Evidence winnerEvidence = new Evidence(
            RuleField.MESSAGE, Operator.CONTAINS, "winner", "winner happened", null);
        Evidence loserEvidence = new Evidence(
            RuleField.MESSAGE, Operator.CONTAINS, "loser", "loser happened", null);

        RuleMatch winner = new RuleMatch(
            "winner", 100, FailureCategory.INFRASTRUCTURE_FAILURE, 0.9, List.of(winnerEvidence));
        RuleMatch loser = new RuleMatch(
            "loser", 10, FailureCategory.FLAKY_TEST, 0.99, List.of(loserEvidence));

        ClassificationResult result = strategy.classify(List.of(winner, loser));

        assertEquals(List.of(winnerEvidence), result.evidence());
    }

    @Test
    void throwsWhenMatchesIsNull() {
        assertThrows(NullPointerException.class, () -> strategy.classify(null));
    }
}
