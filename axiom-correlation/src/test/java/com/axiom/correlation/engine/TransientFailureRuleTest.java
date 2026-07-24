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
 * Direct unit tests for {@link TransientFailureRule} — constructed with a bare
 * {@link CorrelationContext}, no {@link CorrelationEngine}.
 */
class TransientFailureRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");
    private final TransientFailureRule rule = new TransientFailureRule();

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
        assertEquals("transient-failure-v1", rule.id());
    }

    @Test
    void notEligibleWithoutRetryPassed() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.UNKNOWN));
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.FAILURE_CLUSTER_PRESENT, false, List.of())), evidence);

        assertTrue(rule.evaluate(context).isEmpty());
    }

    @Test
    void maximumConfidenceIsEightyFivePercent() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.FLAKY_TEST));
        CorrelationContext context = new CorrelationContext(
            List.of(
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1")),
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, false, List.of()),
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, false, List.of())),
            evidence);

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();
        double confidence = HypothesisScorer.score(evaluation.contributions());

        assertEquals(0.85, confidence, 0.0001);
        assertFalse(evaluation.hasBlockingContradiction());
    }

    @Test
    void stackFrameMatchIsABlockingVeto() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.UNKNOWN));
        CorrelationContext context = new CorrelationContext(
            List.of(
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1")),
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, true, List.of("ev-1", "ev-2")),
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, false, List.of())),
            evidence);

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertTrue(evaluation.hasBlockingContradiction(),
            "a real intermittent bug can still pass on retry — a code correlation must veto a flaky call");
    }

    @Test
    void failureClusterIsNonBlockingButSubstantiallyLowersConfidence() {
        List<CorrelationEvidence> withoutCluster = List.of(testFailureEvidence(FailureCategory.UNKNOWN));
        CorrelationContext contextWithoutCluster = new CorrelationContext(
            List.of(
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1")),
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, false, List.of()),
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, false, List.of())),
            withoutCluster);
        CorrelationContext contextWithCluster = new CorrelationContext(
            List.of(
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1")),
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, false, List.of()),
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, true, List.of("ev-3"))),
            withoutCluster);

        RuleEvaluation withoutClusterEval = rule.evaluate(contextWithoutCluster).orElseThrow();
        RuleEvaluation withClusterEval = rule.evaluate(contextWithCluster).orElseThrow();

        assertFalse(withClusterEval.hasBlockingContradiction(),
            "isolated flaky tests and infrastructure incidents can coexist — not a hard veto");
        assertTrue(
            HypothesisScorer.score(withClusterEval.contributions())
                < HypothesisScorer.score(withoutClusterEval.contributions()));
    }

    @Test
    void neverReturnsAnAbstentionCategory() {
        List<CorrelationEvidence> evidence = List.of(testFailureEvidence(FailureCategory.UNKNOWN));
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1"))), evidence);

        assertEquals(FailureCategory.FLAKY_TEST, rule.evaluate(context).orElseThrow().category());
    }

    @Test
    void competingClassificationsContributeNegativeWeightWithoutBlocking() {
        List<CorrelationEvidence> appBugEvidence = List.of(testFailureEvidence(FailureCategory.APPLICATION_BUG));
        CorrelationContext context = new CorrelationContext(
            List.of(
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1")),
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, false, List.of()),
                new Signal(SignalType.FAILURE_CLUSTER_PRESENT, false, List.of())),
            appBugEvidence);

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertFalse(evaluation.hasBlockingContradiction());
        assertTrue(evaluation.contributions().stream()
            .anyMatch(c -> c.weight() == TransientFailureRule.WEIGHT_ALREADY_APPLICATION_BUG));
    }
}
