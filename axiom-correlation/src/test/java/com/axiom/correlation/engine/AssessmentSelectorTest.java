package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.RootCauseHypothesis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct tests for {@link AssessmentSelector}'s threshold/blocking/lead logic — package-private,
 * so this lives in the same package rather than going through {@link CorrelationEngine} and two
 * real rules, whose specific weights make some of these boundary cases hard to reach naturally.
 */
class AssessmentSelectorTest {

    private static ScoredEvaluation scored(String ruleId, FailureCategory category, double confidence, boolean blocking) {
        RuleEvaluation evaluation = new RuleEvaluation(category, List.of(), List.of(), List.of(), blocking);
        return new ScoredEvaluation(List.of(ruleId), evaluation, confidence);
    }

    @Test
    void singleHypothesisBypassesLeadRequirement() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.70, false)), List.of());

        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.APPLICATION_BUG, assessment.selectedCategory().orElseThrow());
    }

    @Test
    void closeCompetitionWithinMinimumLeadAbstains() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.75, false),
                scored("infrastructure-failure-v1", FailureCategory.INFRASTRUCTURE_FAILURE, 0.65, false)),
            List.of());

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
        // Both hypotheses remain visible even though neither was selected.
        assertEquals(2, assessment.rankedHypotheses().size());
    }

    @Test
    void applicationBugLeadsBySufficientMarginAndIsSelected() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.80, false),
                scored("infrastructure-failure-v1", FailureCategory.INFRASTRUCTURE_FAILURE, 0.60, false)),
            List.of());

        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.APPLICATION_BUG, assessment.selectedCategory().orElseThrow());
    }

    @Test
    void infrastructureLeadsBySufficientMarginAndIsSelected() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.60, false),
                scored("infrastructure-failure-v1", FailureCategory.INFRASTRUCTURE_FAILURE, 0.85, false)),
            List.of());

        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, assessment.selectedCategory().orElseThrow());
    }

    @Test
    void exactTieAbstainsWithDeterministicHypothesisOrdering() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("infrastructure-failure-v1", FailureCategory.INFRASTRUCTURE_FAILURE, 0.75, false),
                scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.75, false)),
            List.of());

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        // Tie-break is rule id ascending, regardless of the order the rules were supplied in.
        assertEquals(List.of("application-bug-v1"), assessment.rankedHypotheses().get(0).matchedReasoningPaths());
        assertEquals(List.of("infrastructure-failure-v1"), assessment.rankedHypotheses().get(1).matchedReasoningPaths());
    }

    @Test
    void blockingContradictionOverridesEvenWithSufficientConfidenceAndLead() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.90, true)), List.of());

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
    }

    @Test
    void repeatedSelectionIsIdentical() {
        List<ScoredEvaluation> input = List.of(
            scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.80, false),
            scored("infrastructure-failure-v1", FailureCategory.INFRASTRUCTURE_FAILURE, 0.60, false));

        RootCauseAssessment first = AssessmentSelector.select(input, List.of());
        RootCauseAssessment second = AssessmentSelector.select(input, List.of());

        assertEquals(first, second);
    }

    @Test
    void aggregationIsANoOpWhenEveryCategoryHasExactlyOneRule() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("application-bug-v1", FailureCategory.APPLICATION_BUG, 0.55, false),
                scored("infrastructure-failure-v1", FailureCategory.INFRASTRUCTURE_FAILURE, 0.50, false),
                scored("transient-failure-v1", FailureCategory.FLAKY_TEST, 0.45, false)),
            List.of());

        assertEquals(3, assessment.rankedHypotheses().size());
        for (RootCauseHypothesis hypothesis : assessment.rankedHypotheses()) {
            assertEquals(1, hypothesis.matchedReasoningPaths().size());
        }
    }

    @Test
    void sameCategoryConfidenceAggregatesAsMaximumNotSum() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("transient-failure-v1", FailureCategory.FLAKY_TEST, 0.70, false),
                scored("historical-flaky-test-v1", FailureCategory.FLAKY_TEST, 0.80, false)),
            List.of());

        assertEquals(1, assessment.rankedHypotheses().size());
        assertEquals(0.80, assessment.rankedHypotheses().get(0).confidence(), 0.0001);
        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
    }

    @Test
    void sameCategoryAggregationCombinesReasoningPathsSorted() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("transient-failure-v1", FailureCategory.FLAKY_TEST, 0.70, false),
                scored("historical-flaky-test-v1", FailureCategory.FLAKY_TEST, 0.80, false)),
            List.of());

        assertEquals(
            List.of("historical-flaky-test-v1", "transient-failure-v1"),
            assessment.rankedHypotheses().get(0).matchedReasoningPaths());
    }

    @Test
    void blockedPathDoesNotInvalidateAnUnblockedSameCategoryPath() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("transient-failure-v1", FailureCategory.FLAKY_TEST, 0.90, true),
                scored("historical-flaky-test-v1", FailureCategory.FLAKY_TEST, 0.75, false)),
            List.of());

        // The blocked path's higher confidence (0.90) must never leak into the aggregate.
        assertEquals(0.75, assessment.rankedHypotheses().get(0).confidence(), 0.0001);
        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.FLAKY_TEST, assessment.selectedCategory().orElseThrow());
    }

    @Test
    void aggregateStaysBlockedOnlyWhenEveryPathIsBlocked() {
        RootCauseAssessment assessment = AssessmentSelector.select(
            List.of(
                scored("transient-failure-v1", FailureCategory.FLAKY_TEST, 0.90, true),
                scored("historical-flaky-test-v1", FailureCategory.FLAKY_TEST, 0.85, true)),
            List.of());

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
    }
}
