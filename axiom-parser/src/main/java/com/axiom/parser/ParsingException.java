package com.axiom.parser;

/**
 * Thrown when a {@link Parser} cannot parse a document at all (e.g. it is not well-formed XML).
 * Distinct from {@link ParserWarning}: this is for whole-document failures with no partial
 * result, not per-record issues that still allow the rest of the document to be parsed.
 */
public final class ParsingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
