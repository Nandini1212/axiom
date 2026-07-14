package com.axiom.parser;

import java.util.Objects;

/**
 * A recoverable problem encountered while parsing one record — surfaced rather than silently
 * dropped, since a caller has no other way to know a testcase was skipped or a value was
 * ignored. {@code testcaseName}/{@code className}/{@code suiteName} are nullable: they may be
 * exactly the attributes that are missing or unreliable for the record this warning is about.
 */
public record ParserWarning(
    WarningType type,
    String testcaseName,
    String className,
    String suiteName,
    String detail
) {

    public ParserWarning {
        Objects.requireNonNull(type, "type is mandatory");
        Objects.requireNonNull(detail, "detail is mandatory");
    }
}
