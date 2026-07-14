package com.axiom.classifier.rule;

/**
 * Thrown when a {@link RuleProcessor} cannot turn a set of {@link RuleDefinition}s into
 * {@link PreparedRule}s (duplicate rule ids, unparseable regex, etc.).
 */
public final class RuleProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RuleProcessingException(String message) {
        super(message);
    }

    public RuleProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
