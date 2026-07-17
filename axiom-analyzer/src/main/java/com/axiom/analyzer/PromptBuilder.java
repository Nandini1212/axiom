package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.Evidence;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.PipelineContext;

/**
 * Builds the prompt an {@link LLMProvider} is asked to explain a failure from. Extracted as its
 * own testable class specifically because the prompt is one of the most important assets in this
 * project — a "golden prompt" test catches an accidental wording change silently dropping
 * evidence or breaking stack-trace truncation, which a loose assertion inside
 * {@link AIEnhancedAnalyzer} would not.
 */
public final class PromptBuilder {

    static final int MAX_STACK_TRACE_CHARS = 2000;

    public String build(FailureEvent event, ClassificationResult classification) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are explaining a test failure classification that has already been ")
            .append("determined deterministically. Do not propose a different category or ")
            .append("confidence — only explain and elaborate on the evidence already provided.\n\n");

        prompt.append("Test: ").append(orUnknown(event.testName())).append(" (")
            .append(orUnknown(event.className())).append(", suite: ")
            .append(orUnknown(event.suiteName())).append(")\n");
        prompt.append("Status: ").append(event.status()).append('\n');
        prompt.append("Message: ").append(orUnknown(event.message())).append('\n');
        prompt.append("Stack trace (truncated):\n").append(truncateStackTrace(event.stackTrace())).append('\n');

        if (event.pipelineContext() != null) {
            appendPipelineContext(prompt, event.pipelineContext());
        }

        prompt.append("\nClassification: ").append(classification.category())
            .append(" (confidence: ").append(classification.confidence()).append(")\n");
        prompt.append("Matched rule: ")
            .append(classification.matchedRuleId() != null ? classification.matchedRuleId() : "none")
            .append('\n');

        prompt.append("Evidence:\n");
        for (Evidence evidence : classification.evidence()) {
            appendEvidence(prompt, evidence);
        }

        prompt.append("\nRespond with: (1) a one-paragraph summary, (2) the likely root cause, ")
            .append("grounded only in the evidence above, (3) 1-3 suggested next steps, (4) a ")
            .append("short explanation of the confidence level. If the evidence above is ")
            .append("insufficient to explain with confidence, say so rather than inventing detail.");

        return prompt.toString();
    }

    private static void appendPipelineContext(StringBuilder prompt, PipelineContext context) {
        prompt.append("Pipeline context:\n");
        appendIfPresent(prompt, "  Repository: ", context.repository());
        appendIfPresent(prompt, "  Branch: ", context.branch());
        appendIfPresent(prompt, "  Commit: ", context.commitSha());
        appendIfPresent(prompt, "  Workflow: ", context.workflow());
        appendIfPresent(prompt, "  Job: ", context.job());
    }

    private static void appendIfPresent(StringBuilder prompt, String label, String value) {
        if (value != null) {
            prompt.append(label).append(value).append('\n');
        }
    }

    private static void appendEvidence(StringBuilder prompt, Evidence evidence) {
        prompt.append("- field=").append(evidence.field())
            .append(" operator=").append(evidence.operator())
            .append(" expected=\"").append(evidence.expectedValue()).append('"')
            .append(" actual=\"").append(evidence.actualValue()).append('"')
            .append(" note=\"").append(evidence.explanation() != null ? evidence.explanation() : "").append('"')
            .append('\n');
    }

    private static String orUnknown(String value) {
        return value != null ? value : "(unknown)";
    }

    private static String truncateStackTrace(String stackTrace) {
        if (stackTrace == null) {
            return "(none)";
        }
        return stackTrace.length() > MAX_STACK_TRACE_CHARS
            ? stackTrace.substring(0, MAX_STACK_TRACE_CHARS) + "... (truncated)"
            : stackTrace;
    }
}
