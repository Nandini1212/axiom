package com.axiom.analyzer;

/**
 * The kind of recoverable problem encountered while trying to AI-enhance one failure. Never
 * affects the underlying deterministic classification — only whether an {@link AiExplanation}
 * was successfully attached.
 */
public enum AnalyzerWarningType {
    AI_TIMEOUT,
    AI_EXPLANATION_FAILED
}
