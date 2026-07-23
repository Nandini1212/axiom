package com.axiom.correlation.presentation;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import com.axiom.correlation.engine.ApplicationBugCorrelationRule;
import com.axiom.correlation.engine.CorrelationEngine;
import com.axiom.correlation.engine.CorrelationRule;
import com.axiom.correlation.engine.FlakyTestRule;
import com.axiom.correlation.engine.InfrastructureFailureRule;
import com.axiom.correlation.model.ChangeSetInput;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.ExecutionEvidence;
import com.axiom.correlation.model.ExecutionInput;
import com.axiom.correlation.model.FailureAnalysisInput;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.correlation.signal.ChangeSetEvidenceMissingExtractor;
import com.axiom.correlation.signal.FailureClusterPresentExtractor;
import com.axiom.correlation.signal.RetryOutcomeExtractor;
import com.axiom.correlation.signal.SignalExtractor;
import com.axiom.correlation.signal.StackFrameMatchesChangedFileExtractor;
import com.axiom.correlation.signal.TopFrameIsTestCodeExtractor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Golden-output (exact-string) tests for {@link TextAssessmentRenderer} — the same discipline
 * {@code PromptBuilderTest} already applies to prompt text, so an accidental wording change is
 * caught immediately rather than silently shipped.
 */
class TextAssessmentRendererTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private final TextAssessmentRenderer renderer = new TextAssessmentRenderer();

    private static CorrelationEngine engine() {
        List<SignalExtractor> extractors = List.of(
            new StackFrameMatchesChangedFileExtractor(),
            new TopFrameIsTestCodeExtractor(),
            new RetryOutcomeExtractor(),
            new ChangeSetEvidenceMissingExtractor());
        List<CorrelationRule> rules = List.of(new ApplicationBugCorrelationRule());
        return new CorrelationEngine(extractors, rules);
    }

    /** Separate from {@link #engine()} so existing fixtures above stay unaffected. */
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
            "failure-1", "testCharge", "com.example.PaymentServiceTest", null,
            SourceFormat.JUNIT, FailureStatus.FAILED, "Payment failed", stackTrace,
            null, NOW, null, null);
    }

    private static TestFailureEvidence testFailureEvidence(String stackTrace, FailureCategory category) {
        FailureAnalysisInput input = new FailureAnalysisInput(
            failureEvent(stackTrace), new ClassificationResult(category, 0.5, null, List.of()));
        return TestFailureEvidence.from("evidence-test-failure", NOW, input);
    }

    private static SourceChangeEvidence sourceChangeEvidence(String... changedFiles) {
        return SourceChangeEvidence.from(
            "evidence-source-change", NOW, new ChangeSetInput("abc123", List.of(changedFiles)));
    }

    private static ExecutionEvidence executionEvidence(boolean retryAttempted, boolean retryPassed) {
        return executionEvidence(retryAttempted, retryPassed, 0);
    }

    private static ExecutionEvidence executionEvidence(
            boolean retryAttempted, boolean retryPassed, int relatedFailureCount) {
        return ExecutionEvidence.from(
            "evidence-execution", NOW, new ExecutionInput(retryAttempted, retryPassed, relatedFailureCount));
    }

    @Test
    void determinedApplicationBugSummary() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));
        RootCauseAssessment assessment = engine().assess(evidence);

        String expected = String.join("\n",
            "PaymentServiceTest.testCharge",
            "",
            "Likely cause: Application bug",
            "Confidence: Moderate - 80%",
            "",
            "Why Axiom thinks this:",
            "- Changed production file matches stack frame",
            "- Failure reproduced on retry",
            "- Existing deterministic classification is already APPLICATION_BUG",
            "",
            "Evidence against: none",
            "",
            "Recommended next step:",
            "Review the recent changes in src/main/java/com/example/PaymentService.java.",
            "",
            "Result: Root cause determined");

        assertEquals(expected, renderer.renderSummary(assessment, evidence));
    }

    @Test
    void retryPassedAbstentionSummary() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, true));
        RootCauseAssessment assessment = engine().assess(evidence);

        String expected = String.join("\n",
            "PaymentServiceTest.testCharge",
            "",
            "Axiom could not determine a reliable root cause.",
            "",
            "Closest hypothesis considered: Application bug (Low confidence - 20%)",
            "",
            "Why Axiom did not choose a cause:",
            "- Retry passed",
            "",
            "Recommended next step:",
            "Run the test again and check whether it fails consistently before treating this as an application bug.",
            "",
            "Result: More investigation needed");

        assertEquals(expected, renderer.renderSummary(assessment, evidence));
    }

    @Test
    void missingChangeSetAbstentionSummary() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            executionEvidence(true, false));
        RootCauseAssessment assessment = engine().assess(evidence);

        String expected = String.join("\n",
            "PaymentServiceTest.testCharge",
            "",
            "Axiom could not determine a reliable root cause.",
            "",
            "Closest hypothesis considered: Application bug (Low confidence - 25%)",
            "",
            "Why Axiom did not choose a cause:",
            "- No change-set evidence supplied",
            "",
            "Missing evidence:",
            "- source-change evidence not supplied",
            "",
            "Recommended next step:",
            "Supply source-change information (a changes.json diff) and re-run the investigation.",
            "",
            "Result: More investigation needed");

        assertEquals(expected, renderer.renderSummary(assessment, evidence));
    }

    @Test
    void determinedApplicationBugDetailed() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));
        RootCauseAssessment assessment = engine().assess(evidence);

        String expected = String.join("\n",
            "PaymentServiceTest.testCharge",
            "",
            "Disposition: DETERMINED",
            "Selected category: APPLICATION_BUG",
            "",
            "Hypothesis 1 [rule: application-bug-v1]",
            "  Category: APPLICATION_BUG",
            "  Confidence: 0.80",
            "  Contributions:",
            "    +0.40  Changed production file matches stack frame",
            "    +0.25  Failure reproduced on retry",
            "    +0.15  Existing deterministic classification is already APPLICATION_BUG",
            "  Supporting evidence:",
            "    - [evidence-test-failure] Changed production file matches stack frame",
            "    - [evidence-execution] Failure reproduced on retry",
            "    - [evidence-test-failure] Existing deterministic classification is already APPLICATION_BUG",
            "  Contradicting evidence:",
            "",
            "Missing evidence: none");

        assertEquals(expected, renderer.renderDetailed(assessment, evidence));
    }

    @Test
    void renderingIsDeterministicAcrossRepeatedCalls() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));
        RootCauseAssessment assessment = engine().assess(evidence);

        String firstSummary = renderer.renderSummary(assessment, evidence);
        String secondSummary = renderer.renderSummary(assessment, evidence);
        String firstDetailed = renderer.renderDetailed(assessment, evidence);
        String secondDetailed = renderer.renderDetailed(assessment, evidence);

        assertEquals(firstSummary, secondSummary);
        assertEquals(firstDetailed, secondDetailed);
    }

    @Test
    void determinedInfrastructureFailureUsesInfrastructureSpecificAction() {
        // Proves AssessmentFacts.recommendedActionForDetermined is category-aware: before this
        // fix, every DETERMINED verdict rendered an application-bug-shaped "review changes in X"
        // recommendation regardless of which category actually won.
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.INFRASTRUCTURE_FAILURE),
            executionEvidence(true, true, 1));
        RootCauseAssessment assessment = engineWithAllRules().assess(evidence);

        String expected = String.join("\n",
            "PaymentServiceTest.testCharge",
            "",
            "Likely cause: Infrastructure issue",
            "Confidence: High - 85%",
            "",
            "Why Axiom thinks this:",
            "- Existing deterministic classification is already INFRASTRUCTURE_FAILURE",
            "- Failure cluster present",
            "- Retry passed",
            "",
            "Evidence against: none",
            "",
            "Recommended next step:",
            "Check the health of dependent services and infrastructure around the time of this failure.",
            "",
            "Result: Root cause determined");

        assertEquals(expected, renderer.renderSummary(assessment, evidence));
    }

    @Test
    void determinedFlakyTestUsesCautiousWordingNotAHistoricalClaim() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.FLAKY_TEST),
            executionEvidence(true, true));
        RootCauseAssessment assessment = engineWithAllRules().assess(evidence);

        String expected = String.join("\n",
            "PaymentServiceTest.testCharge",
            "",
            "Likely cause: Possibly flaky (this run)",
            "Confidence: High - 85%",
            "",
            "Why Axiom thinks this:",
            "- Retry passed",
            "- Existing deterministic classification is already FLAKY_TEST",
            "- No stack frame correlates with a changed file",
            "",
            "Evidence against: none",
            "",
            "Recommended next step:",
            "This failure appears transient within this execution. Re-run the test to confirm, "
                + "and monitor for repeated occurrences before treating it as a stable defect.",
            "",
            "Result: Root cause determined");

        String actual = renderer.renderSummary(assessment, evidence);
        assertEquals(expected, actual);

        // The one wording constraint this rule/renderer combination must never violate.
        assertFalse(actual.toLowerCase(java.util.Locale.ROOT).contains("this test is flaky"));
        assertFalse(actual.toLowerCase(java.util.Locale.ROOT).contains("known to be flaky"));
    }
}
