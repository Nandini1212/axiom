package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.util.Objects;

/**
 * A test/demo {@link LLMProvider} that never calls a real API. Lets {@link AIEnhancedAnalyzer}
 * and its timeout/fallback handling be built and tested deterministically before a real provider
 * (e.g. Claude) exists — the surrounding design (timeouts, per-failure isolation, warnings) can
 * be fully proven out without a network call, an API key, or nondeterministic LLM output.
 */
public final class FakeLLMProvider implements LLMProvider {

    private enum Behavior { FIXED_EXPLANATION, TIMEOUT, THROW }

    /** How long {@link #alwaysTimeout()} sleeps for — comfortably longer than any test timeout used against it. */
    public static final long TIMEOUT_SLEEP_MILLIS = 5000;

    private final Behavior behavior;
    private final AiExplanation fixedExplanation;

    public FakeLLMProvider(AiExplanation fixedExplanation) {
        this.behavior = Behavior.FIXED_EXPLANATION;
        this.fixedExplanation = Objects.requireNonNull(fixedExplanation, "fixedExplanation is mandatory");
    }

    private FakeLLMProvider(Behavior behavior) {
        this.behavior = behavior;
        this.fixedExplanation = null;
    }

    public static FakeLLMProvider alwaysTimeout() {
        return new FakeLLMProvider(Behavior.TIMEOUT);
    }

    public static FakeLLMProvider alwaysThrows() {
        return new FakeLLMProvider(Behavior.THROW);
    }

    @Override
    public AiExplanation explain(FailureEvent event, ClassificationResult classification) {
        switch (behavior) {
            case FIXED_EXPLANATION -> {
                return fixedExplanation;
            }
            case TIMEOUT -> {
                try {
                    Thread.sleep(TIMEOUT_SLEEP_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return fixedExplanation;
            }
            case THROW -> throw new LlmExplanationException("Simulated provider failure");
            default -> throw new IllegalStateException("Unhandled behavior: " + behavior);
        }
    }
}
