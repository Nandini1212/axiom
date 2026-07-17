package com.axiom.analyzer;

import java.util.List;
import java.util.Objects;

/**
 * The AI's elaboration on an already-decided {@code ClassificationResult}. Deliberately has no
 * {@code category} or {@code confidence} field — the model cannot override the deterministic
 * classification even if it tried, because there is no field for it to occupy.
 */
public record AiExplanation(
    String summary,
    String rootCause,
    List<String> suggestedNextSteps,
    String confidenceExplanation
) {

    public AiExplanation {
        Objects.requireNonNull(summary, "summary is mandatory");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        Objects.requireNonNull(rootCause, "rootCause is mandatory");
        if (rootCause.isBlank()) {
            throw new IllegalArgumentException("rootCause must not be blank");
        }
        Objects.requireNonNull(confidenceExplanation, "confidenceExplanation is mandatory");
        if (confidenceExplanation.isBlank()) {
            throw new IllegalArgumentException("confidenceExplanation must not be blank");
        }
        Objects.requireNonNull(suggestedNextSteps, "suggestedNextSteps is mandatory");
        suggestedNextSteps = List.copyOf(suggestedNextSteps);
    }
}
