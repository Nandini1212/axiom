package com.axiom.correlation.presentation;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import com.axiom.correlation.engine.ApplicationBugCorrelationRule;
import com.axiom.correlation.engine.CorrelationEngine;
import com.axiom.correlation.engine.CorrelationRule;
import com.axiom.correlation.model.ChangeSetInput;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.ExecutionEvidence;
import com.axiom.correlation.model.ExecutionInput;
import com.axiom.correlation.model.FailureAnalysisInput;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.correlation.signal.ChangeSetEvidenceMissingExtractor;
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
 * Golden-output tests for {@link MarkdownAssessmentRenderer}. Deliberately excludes any assigned
 * owner or time-estimate content — Axiom has no evidence source for either today, so a renderer
 * asserting them would be presenting fabricated output as if it were derived.
 */
class MarkdownAssessmentRendererTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private final MarkdownAssessmentRenderer renderer = new MarkdownAssessmentRenderer();

    private static CorrelationEngine engine() {
        List<SignalExtractor> extractors = List.of(
            new StackFrameMatchesChangedFileExtractor(),
            new TopFrameIsTestCodeExtractor(),
            new RetryOutcomeExtractor(),
            new ChangeSetEvidenceMissingExtractor());
        List<CorrelationRule> rules = List.of(new ApplicationBugCorrelationRule());
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
        return ExecutionEvidence.from(
            "evidence-execution", NOW, new ExecutionInput(retryAttempted, retryPassed, 0));
    }

    @Test
    void determinedApplicationBugMarkdown() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, false));
        RootCauseAssessment assessment = engine().assess(evidence);

        String expected = String.join("\n",
            "## Axiom Investigation: PaymentServiceTest.testCharge",
            "",
            "**Verdict:** Application bug (Moderate confidence - 80%)",
            "",
            "**Why**",
            "- Changed production file matches stack frame",
            "- Failure reproduced on retry",
            "- Existing deterministic classification is already APPLICATION_BUG",
            "",
            "**Evidence against:** none",
            "",
            "**Files to review**",
            "- src/main/java/com/example/PaymentService.java",
            "",
            "**Recommended next step**",
            "Review the recent changes in src/main/java/com/example/PaymentService.java.",
            "",
            "**Result:** Root cause determined");

        String actual = renderer.renderSummary(assessment, evidence);
        assertEquals(expected, actual);

        // The one integrity constraint this renderer must never violate: no fabricated fields.
        assertFalse(actual.toLowerCase(java.util.Locale.ROOT).contains("owner"));
        assertFalse(actual.toLowerCase(java.util.Locale.ROOT).contains("estimated"));
    }

    @Test
    void retryPassedAbstentionMarkdown() {
        List<CorrelationEvidence> evidence = List.of(
            testFailureEvidence(
                "at com.example.PaymentService.charge(PaymentService.java:42)",
                FailureCategory.APPLICATION_BUG),
            sourceChangeEvidence("src/main/java/com/example/PaymentService.java"),
            executionEvidence(true, true));
        RootCauseAssessment assessment = engine().assess(evidence);

        String expected = String.join("\n",
            "## Axiom Investigation: PaymentServiceTest.testCharge",
            "",
            "**Status:** More investigation needed",
            "",
            "Axiom could not determine a reliable root cause.",
            "",
            "**Closest hypothesis considered:** Application bug (Low confidence - 20%)",
            "",
            "**Why Axiom did not choose a cause**",
            "- Retry passed",
            "",
            "**Recommended next step**",
            "Run the test again and check whether it fails consistently before treating this as an application bug.",
            "",
            "**Result:** More investigation needed");

        assertEquals(expected, renderer.renderSummary(assessment, evidence));
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

        assertEquals(
            renderer.renderSummary(assessment, evidence),
            renderer.renderSummary(assessment, evidence));
        assertEquals(
            renderer.renderDetailed(assessment, evidence),
            renderer.renderDetailed(assessment, evidence));
    }
}
