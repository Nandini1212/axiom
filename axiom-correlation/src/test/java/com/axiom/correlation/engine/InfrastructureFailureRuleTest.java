package com.axiom.correlation.engine;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.FailureAnalysisInput;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.correlation.signal.Signal;
import com.axiom.correlation.signal.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct unit tests for {@link InfrastructureFailureRule} — constructed with a bare
 * {@link CorrelationContext}, no {@link CorrelationEngine}.
 */
class InfrastructureFailureRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private final InfrastructureFailureRule rule = new InfrastructureFailureRule();

    private static TestFailureEvidence testFailureEvidence(FailureCategory category) {
        FailureEvent event = new FailureEvent(
            "failure-1", "testCharge", "com.example.PaymentServiceTest", null,
            SourceFormat.JUNIT, FailureStatus.FAILED, "Payment failed", "at com.example.X.y(X.java:1)",
            null, NOW, null, null);
        FailureAnalysisInput input = new FailureAnalysisInput(
            event, new ClassificationResult(category, 0.5, null, List.of()));
        return TestFailureEvidence.from("evidence-test-failure", NOW, input);
    }

    @Test
    void hasStableId() {
        assertEquals("infrastructure-failure-v1", rule.id());
    }

    @Test
    void notEligibleWhenNeitherClassifiedNorClusterPresent() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.UNKNOWN));
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1"))), evidence);

        assertTrue(rule.evaluate(context).isEmpty());
    }

    @Test
    void eligibleWhenClassifiedAsInfrastructureAloneWithNoCluster() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.INFRASTRUCTURE_FAILURE));
        CorrelationContext context = new CorrelationContext(List.of(), evidence);

        Optional<RuleEvaluation> evaluation = rule.evaluate(context);

        assertTrue(evaluation.isPresent());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, evaluation.get().category());
        assertEquals(1, evaluation.get().contributions().size());
        assertEquals(
            InfrastructureFailureRule.WEIGHT_ALREADY_INFRASTRUCTURE_FAILURE,
            evaluation.get().contributions().get(0).weight());
    }

    @Test
    void eligibleWhenClusterPresentAloneWithNoClassification() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.UNKNOWN));
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.FAILURE_CLUSTER_PRESENT, true, List.of("ev-1"))), evidence);

        Optional<RuleEvaluation> evaluation = rule.evaluate(context);

        assertTrue(evaluation.isPresent());
        assertEquals(1, evaluation.get().contributions().size());
        assertEquals(
            InfrastructureFailureRule.WEIGHT_FAILURE_CLUSTER_PRESENT,
            evaluation.get().contributions().get(0).weight());
    }

    @Test
    void stackFrameMatchLowersConfidenceRelativeToWithoutIt() {
        List<CorrelationEvidence> evidenceBase = List.of(testFailureEvidence(FailureCategory.INFRASTRUCTURE_FAILURE));
        CorrelationContext withoutStackMatch = new CorrelationContext(
            List.of(
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, true, List.of("ev-1")),
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-2"))),
            evidenceBase);
        CorrelationContext withStackMatch = new CorrelationContext(
            List.of(
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, true, List.of("ev-1")),
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-2")),
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, true, List.of("ev-1", "ev-3"))),
            evidenceBase);

        double confidenceWithout = HypothesisScorer.score(rule.evaluate(withoutStackMatch).orElseThrow().contributions());
        double confidenceWith = HypothesisScorer.score(rule.evaluate(withStackMatch).orElseThrow().contributions());

        assertTrue(confidenceWith < confidenceWithout,
            "stack frame matching a changed file should lower infrastructure confidence, not raise or ignore it");
        assertFalse(rule.evaluate(withStackMatch).orElseThrow().hasBlockingContradiction(),
            "the contradiction lowers confidence arithmetically; it is not a hard veto for this rule");
    }

    @Test
    void missingChangeSetEvidenceContributesNoNegativeWeight() {
        // No STACK_FRAME_MATCHES_CHANGED_FILE and no CHANGE_SET_EVIDENCE_MISSING signal consulted
        // at all by this rule — unlike ApplicationBugCorrelationRule, absence of change-set
        // evidence does not make an infrastructure hypothesis less likely.
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.INFRASTRUCTURE_FAILURE));
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.CHANGE_SET_EVIDENCE_MISSING, true, List.of())), evidence);

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertTrue(evaluation.contributions().stream().allMatch(c -> c.weight() > 0),
            "no contribution should be negative when only CHANGE_SET_EVIDENCE_MISSING is present");
    }

    @Test
    void neverReturnsAnAbstentionCategory() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.INFRASTRUCTURE_FAILURE));
        CorrelationContext context = new CorrelationContext(List.of(), evidence);

        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, rule.evaluate(context).orElseThrow().category());
    }
}
