package com.axiom.classifier.rule;

/**
 * Thrown when a {@link RuleSource} cannot load or parse its rule definitions.
 */
public final class RuleSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RuleSourceException(String message) {
        super(message);
    }

    public RuleSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
