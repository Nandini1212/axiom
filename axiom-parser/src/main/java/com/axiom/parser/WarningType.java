package com.axiom.parser;

/**
 * The kind of recoverable problem a {@link Parser} encountered for one record while still being
 * able to continue parsing the rest of the document.
 */
public enum WarningType {

    /** A testcase lacked enough identifying attributes to construct a valid FailureEvent. */
    MISSING_ATTRIBUTE,

    /** A testcase had more than one of failure/error present; a precedence rule was applied. */
    AMBIGUOUS_STATUS,

    /** A timestamp attribute was present but could not be parsed. */
    INVALID_TIMESTAMP,

    /** An element was encountered that this parser does not map to any FailureEvent field. */
    UNSUPPORTED_ELEMENT
}
