package com.axiom.analyzer;

/**
 * Thrown by an {@link LLMProvider} when it cannot produce an {@link AiExplanation} (network
 * error, API error, malformed provider response, etc.). Caught per-failure by
 * {@link AIEnhancedAnalyzer} and converted into an {@link AnalyzerWarning} — never allowed to
 * abort the rest of the analysis.
 */
public final class LlmExplanationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LlmExplanationException(String message) {
        super(message);
    }

    public LlmExplanationException(String message, Throwable cause) {
        super(message, cause);
    }
}
