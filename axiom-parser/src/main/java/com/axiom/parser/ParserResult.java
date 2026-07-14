package com.axiom.parser;

import com.axiom.common.model.FailureEvent;

import java.util.List;
import java.util.Objects;

/**
 * What a {@link Parser} understood from one document: every non-passing test case it could
 * successfully turn into a {@link FailureEvent}, plus every recoverable problem it hit along the
 * way. Deliberately not just {@code List<FailureEvent>} — once malformed records must never
 * disappear silently, something has to carry the "here's what I couldn't parse" information, and
 * this is that something.
 */
public record ParserResult(List<FailureEvent> failures, List<ParserWarning> warnings) {

    public ParserResult {
        Objects.requireNonNull(failures, "failures is mandatory");
        Objects.requireNonNull(warnings, "warnings is mandatory");
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
