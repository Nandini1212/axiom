package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

/**
 * Produces an {@link AiExplanation} for an already-classified failure. Implementations own their
 * own request/response mechanics with whatever underlying LLM API they wrap (including getting
 * structured output from it) — callers never see provider-specific shapes, only
 * {@link AiExplanation}.
 */
public interface LLMProvider {

    /**
     * @throws LlmExplanationException if no explanation could be produced
     */
    AiExplanation explain(FailureEvent event, ClassificationResult classification);
}
