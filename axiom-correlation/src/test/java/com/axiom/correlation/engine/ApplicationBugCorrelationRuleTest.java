package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.ConfidenceContribution;
import com.axiom.correlation.signal.Signal;
import com.axiom.correlation.signal.SignalType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct unit tests for {@link ApplicationBugCorrelationRule} — constructed with a bare
 * {@link CorrelationContext}, no {@link CorrelationEngine}, no extractors, no evidence adapters.
 * Concretely demonstrates a rule is independently testable, rather than just asserting it
 * architecturally.
 */
class ApplicationBugCorrelationRuleTest {

    private final ApplicationBugCorrelationRule rule = new ApplicationBugCorrelationRule();

    @Test
    void hasStableId() {
        assertEquals("application-bug-v1", rule.id());
    }

    @Test
    void noMatchingSignalsProducesNoEvaluation() {
        CorrelationContext context = new CorrelationContext(List.of(), List.of());

        assertTrue(rule.evaluate(context).isEmpty());
    }

    @Test
    void stackFrameMatchAloneContributesExpectedWeight() {
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, true, List.of("ev-1"))),
            List.of());

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertEquals(FailureCategory.APPLICATION_BUG, evaluation.category());
        assertEquals(1, evaluation.contributions().size());
        ConfidenceContribution contribution = evaluation.contributions().get(0);
        assertEquals(ApplicationBugCorrelationRule.WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE, contribution.weight());
        assertEquals("application-bug-v1", contribution.ruleId());
        assertFalse(evaluation.hasBlockingContradiction());
    }

    @Test
    void retryPassedSignalIsMarkedBlocking() {
        CorrelationContext context = new CorrelationContext(
            List.of(
                new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, true, List.of("ev-1")),
                new Signal(SignalType.RETRY_PASSED, true, List.of("ev-2"))),
            List.of());

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertTrue(evaluation.hasBlockingContradiction());
        assertEquals(1, evaluation.contradicting().size());
    }

    @Test
    void topFrameIsTestCodeSignalIsMarkedBlocking() {
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.TOP_FRAME_IS_TEST_CODE, true, List.of("ev-1"))),
            List.of());

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertTrue(evaluation.hasBlockingContradiction());
    }

    @Test
    void changeSetMissingContributesNegativeWeightWithoutBlocking() {
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.CHANGE_SET_EVIDENCE_MISSING, true, List.of())),
            List.of());

        RuleEvaluation evaluation = rule.evaluate(context).orElseThrow();

        assertFalse(evaluation.hasBlockingContradiction());
        assertEquals(
            ApplicationBugCorrelationRule.WEIGHT_CHANGE_SET_EVIDENCE_MISSING,
            evaluation.contributions().get(0).weight());
    }

    @Test
    void neverReturnsAnAbstentionCategory() {
        CorrelationContext context = new CorrelationContext(
            List.of(new Signal(SignalType.RETRY_PASSED, true, List.of("ev-1"))),
            List.of());

        Optional<RuleEvaluation> evaluation = rule.evaluate(context);

        // Even when the rule's own signals are entirely contradicting (blocking = true), it still
        // reports the real category it evaluated against — abstention is AssessmentSelector's
        // decision, never something a rule encodes into FailureCategory itself.
        assertTrue(evaluation.isPresent());
        assertEquals(FailureCategory.APPLICATION_BUG, evaluation.get().category());
    }
}
