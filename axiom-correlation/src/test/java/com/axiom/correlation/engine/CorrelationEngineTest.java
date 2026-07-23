package com.axiom.correlation.engine;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.ChangeSetInput;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.ExecutionInput;
import com.axiom.correlation.model.FailureAnalysisInput;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.ExecutionEvidence;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.correlation.signal.ChangeSetEvidenceMissingExtractor;
import com.axiom.correlation.signal.FailureClusterPresentExtractor;
import com.axiom.correlation.signal.RetryOutcomeExtractor;
import com.axiom.correlation.signal.SignalExtractor;
import com.axiom.correlation.signal.StackFrameMatchesChangedFileExtractor;
import com.axiom.correlation.signal.TopFrameIsTestCodeExtractor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden fixture tests for the correlation vertical slice: both rules
 * ({@code ApplicationBugCorrelationRule}, {@code InfrastructureFailureRule}), five signal
 * extractors, deterministic scoring, and the DETERMINED/NEEDS_INVESTIGATION abstention boundary
 * (including the minimum-lead requirement between two competing hypotheses). All evidence is
 * constructed directly in Java — no files, no JSON, no network access.
 */
class CorrelationEngineTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");

    private static CorrelationEngine engine() {
        List<SignalExtractor> extractors = List.of(
            new StackFrameMatchesChangedFileExtractor(),
            new TopFrameIsTestCodeExtractor(),
            new RetryOutcomeExtractor(),
            new ChangeSetEvidenceMissingExtractor(),
            new FailureClusterPresentExtractor());
        List<CorrelationRule> rules = List.of(new ApplicationBugCorrelationRule(), new InfrastructureFailureRule());
        return new CorrelationEngine(extractors, rules);
    }

    /**
     * Separate from {@link #engine()} deliberately: several pre-existing fixtures above have
     * {@code retryPassed=true}, which is exactly {@code FlakyTestRule}'s eligibility trigger.
     * Adding it to the shared two-rule engine would introduce a third competing hypothesis into
     * already-reviewed fixtures and could silently change their asserted outcomes (confirmed by
     * hand-tracing before writing this comment — one existing test's exact hypothesis count would
     * break). New tests that specifically want all three rules use this helper instead.
     */
    private static CorrelationEngine engineWithAllRules() {
        List<SignalExtractor> extractors = List.of(
            new StackFrameMatchesChangedFileExtractor(),
            new TopFrameIsTestCodeExtractor(),
            new RetryOutcomeExtractor(),
            new ChangeSetEvidenceMissingExtractor(),
            new FailureClusterPresentExtractor());
        List<CorrelationRule> rules = List.of(
            new ApplicationBugCorrelationRule(), new InfrastructureFailureRule(), new FlakyTestRule());
        return new CorrelationEngine(extractors, rules);
    }

    private static FailureEvent failureEvent(String stackTrace) {
        return new FailureEvent(
            "failure-1",
            "testCharge",
            "com.example.PaymentServiceTest",
            null,
            SourceFormat.JUNIT,
            FailureStatus.FAILED,
            "Payment failed",
            stackTrace,
            null,
            NOW,
            null,
            null);
    }

    private static ClassificationResult classification(FailureCategory category) {
        return new ClassificationResult(category, 0.5, null, List.of());
    }

    private static TestFailureEvidence testFailureEvidence(String stackTrace, FailureCategory category) {
        FailureAnalysisInput input = new FailureAnalysisInput(failureEvent(stackTrace), classification(category));
        return TestFailureEvidence.from("evidence-test-failure", NOW, input);
    }

    private static SourceChangeEvidence sourceChangeEvidence(String... changedFiles) {
        ChangeSetInput input = new ChangeSetInput("abc123", List.of(changedFiles));
        return SourceChangeEvidence.from("evidence-source-change", NOW, input);
    }

    private static ExecutionEvidence executionEvidence(boolean retryAttempted, boolean retryPassed) {
        return executionEvidence(retryAttempted, retryPassed, 0);
    }

    private static ExecutionEvidence executionEvidence(
            boolean retryAttempted, boolean retryPassed, int relatedFailureCount) {
        ExecutionInput input = new ExecutionInput(retryAttempted, retryPassed, relatedFailureCount);
        return ExecutionEvidence.from("evidence-execution", NOW, input);
    }

    @Test
    void changedProductionFileAndRepeatFailureSelectsApplicationBug() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.APPLICATION_BUG, assessment.selectedCategory().orElseThrow());
        assertEquals(1, assessment.rankedHypotheses().size());
        assertEquals(0.80, assessment.rankedHypotheses().get(0).confidence(), 0.0001);
        assertTrue(assessment.missingEvidence().isEmpty());
    }

    @Test
    void changedTestFileOnlyAbstains() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentServiceTest.testCharge(PaymentServiceTest.java:20)",
                FailureCategory.UNKNOWN),
            sourceChangeEvidence("src/test/java/com/example/PaymentServiceTest.java"),
            executionEvidence(true, false));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
    }

    @Test
    void retryPassesAbstains() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, true));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
    }

    @Test
    void missingChangeSetEvidenceAbstains() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            executionEvidence(true, false));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
        assertTrue(assessment.missingEvidence().contains("source-change evidence not supplied"));
    }

    @Test
    void conflictingEvidenceAbstainsDespiteStrongPositiveContributions() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentServiceTest.testCharge(PaymentServiceTest.java:20)\n"
                    + "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
        // The contradiction is retained, not silently netted away.
        assertFalse(assessment.rankedHypotheses().get(0).contradictingEvidence().isEmpty());
        // The strong positive contributions are still honestly recorded despite the abstention.
        assertTrue(assessment.rankedHypotheses().get(0).confidence() > 0.0);
    }

    @Test
    void repeatedEvaluationIsIdenticalEveryTime() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));

        CorrelationEngine engine = engine();
        RootCauseAssessment first = engine.assess(evidence);
        RootCauseAssessment second = engine.assess(new ArrayList<>(evidence));

        assertEquals(first.disposition(), second.disposition());
        assertEquals(first.selectedCategory(), second.selectedCategory());
        assertEquals(first.rankedHypotheses(), second.rankedHypotheses());
        assertEquals(first.missingEvidence(), second.missingEvidence());
    }

    @Test
    void infrastructureClassificationClusterAndRetryPassSelectsInfrastructureFailure() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.INFRASTRUCTURE_FAILURE),
            executionEvidence(true, true, 1));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, assessment.selectedCategory().orElseThrow());
    }

    @Test
    void clusterAndRetryPassWithoutClassifierSupportAbstains() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.UNKNOWN),
            executionEvidence(true, true, 1));

        RootCauseAssessment assessment = engine().assess(evidence);

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
        // ApplicationBugCorrelationRule also fires here (retry-passed + missing-changeset are both
        // non-empty contributions, even though they clamp its confidence to 0.0) — both hypotheses
        // are ranked, infrastructure's 0.45 ahead of application-bug's 0.0.
        assertEquals(2, assessment.rankedHypotheses().size());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, assessment.rankedHypotheses().get(0).category());
        assertEquals(0.45, assessment.rankedHypotheses().get(0).confidence(), 0.0001);
    }

    @Test
    void repeatedEvaluationWithBothRulesIsIdenticalEveryTime() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.INFRASTRUCTURE_FAILURE),
            executionEvidence(true, true, 1));

        CorrelationEngine engine = engine();
        RootCauseAssessment first = engine.assess(evidence);
        RootCauseAssessment second = engine.assess(new ArrayList<>(evidence));

        assertEquals(first, second);
    }

    @Test
    void flakyTestWinsWhenNoCodeOrInfrastructureCorrelationExists() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.FLAKY_TEST),
            executionEvidence(true, true, 0));

        RootCauseAssessment assessment = engineWithAllRules().assess(evidence);

        assertEquals(AssessmentDisposition.DETERMINED, assessment.disposition());
        assertEquals(FailureCategory.FLAKY_TEST, assessment.selectedCategory().orElseThrow());
        assertEquals(0.85, assessment.rankedHypotheses().get(0).confidence(), 0.0001);
    }

    @Test
    void stackFrameMatchBlocksBothApplicationBugAndFlakyHypothesesSimultaneously() {
        // Retry passed contradicts application-bug; the same changed-file correlation blocks
        // flaky. Neither rule should be allowed to win just because the other was vetoed.
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, true, 0));

        RootCauseAssessment assessment = engineWithAllRules().assess(evidence);

        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, assessment.disposition());
        assertTrue(assessment.selectedCategory().isEmpty());
    }

    @Test
    void repeatedEvaluationWithAllThreeRulesIsIdenticalEveryTime() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.FLAKY_TEST),
            executionEvidence(true, true, 0));

        CorrelationEngine engine = engineWithAllRules();
        RootCauseAssessment first = engine.assess(evidence);
        RootCauseAssessment second = engine.assess(new ArrayList<>(evidence));

        assertEquals(first, second);
    }
}
