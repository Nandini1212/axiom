package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.Evidence;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.RuleField;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.PipelineContext;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private static FailureEvent event(String stackTrace, PipelineContext pipelineContext) {
        return new FailureEvent(
            "evt-1", "shouldLogin", "com.example.LoginTest", "LoginTest",
            SourceFormat.JUNIT, FailureStatus.FAILED,
            "Connection refused", stackTrace, 340L, null, pipelineContext, null);
    }

    private static ClassificationResult classification() {
        Evidence evidence = new Evidence(
            RuleField.MESSAGE, Operator.CONTAINS, "Connection refused",
            "Connection refused: could not connect", "Dependent service unavailable");
        return new ClassificationResult(
            FailureCategory.INFRASTRUCTURE_FAILURE, 0.95, "connection-refused", List.of(evidence));
    }

    @Test
    void goldenPromptWithoutPipelineContext() {
        String prompt = builder.build(event("java.net.ConnectException: Connection refused", null), classification());

        String expected = """
            You are explaining a test failure classification that has already been determined deterministically. Do not propose a different category or confidence — only explain and elaborate on the evidence already provided.

            Test: shouldLogin (com.example.LoginTest, suite: LoginTest)
            Status: FAILED
            Message: Connection refused
            Stack trace (truncated):
            java.net.ConnectException: Connection refused

            Classification: INFRASTRUCTURE_FAILURE (confidence: 0.95)
            Matched rule: connection-refused
            Evidence:
            - field=MESSAGE operator=CONTAINS expected="Connection refused" actual="Connection refused: could not connect" note="Dependent service unavailable"

            Respond with: (1) a one-paragraph summary, (2) the likely root cause, grounded only in the evidence above, (3) 1-3 suggested next steps, (4) a short explanation of the confidence level. If the evidence above is insufficient to explain with confidence, say so rather than inventing detail.""";

        assertEquals(expected, prompt);
    }

    @Test
    void includesPipelineContextWhenPresent() {
        PipelineContext context = new PipelineContext(
            "github", "org/repo", "ci.yml", "test", "feature/refactor", "abc123", "42", "run-1");

        String prompt = builder.build(event("stack", context), classification());

        assertTrue(prompt.contains("Pipeline context:"));
        assertTrue(prompt.contains("  Repository: org/repo"));
        assertTrue(prompt.contains("  Branch: feature/refactor"));
        assertTrue(prompt.contains("  Commit: abc123"));
        assertTrue(prompt.contains("  Workflow: ci.yml"));
        assertTrue(prompt.contains("  Job: test"));
    }

    @Test
    void omitsPipelineContextSectionWhenAbsent() {
        String prompt = builder.build(event("stack", null), classification());

        assertFalse(prompt.contains("Pipeline context:"));
    }

    @Test
    void truncatesLongStackTraces() {
        String longStackTrace = "x".repeat(PromptBuilder.MAX_STACK_TRACE_CHARS + 500);

        String prompt = builder.build(event(longStackTrace, null), classification());

        assertTrue(prompt.contains("... (truncated)"));
        assertFalse(prompt.contains(longStackTrace));
    }

    @Test
    void doesNotTruncateShortStackTraces() {
        String shortStackTrace = "short stack";

        String prompt = builder.build(event(shortStackTrace, null), classification());

        assertTrue(prompt.contains(shortStackTrace));
        assertFalse(prompt.contains("... (truncated)"));
    }

    @Test
    void handlesMissingStackTraceAndMessage() {
        FailureEvent event = new FailureEvent(
            "evt-1", "shouldLogin", null, null,
            SourceFormat.JUNIT, FailureStatus.SKIPPED,
            null, null, null, null, null, null);

        String prompt = builder.build(event, classification());

        assertTrue(prompt.contains("Message: (unknown)"));
        assertTrue(prompt.contains("Stack trace (truncated):\n(none)"));
        assertTrue(prompt.contains("(unknown), suite: (unknown)"));
    }

    @Test
    void printsNoneWhenNoRuleMatched() {
        ClassificationResult unmatched = new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of());

        String prompt = builder.build(event("stack", null), unmatched);

        assertTrue(prompt.contains("Matched rule: none"));
    }
}
