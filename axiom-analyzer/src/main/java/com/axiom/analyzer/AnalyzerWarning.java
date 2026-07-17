package com.axiom.analyzer;

import java.util.Objects;

/**
 * A recoverable problem AI-enhancing one specific failure — surfaced rather than silently
 * dropped, mirroring {@code ParserWarning}'s "no silent data loss" principle at this layer.
 */
public record AnalyzerWarning(AnalyzerWarningType type, String failureEventId, String detail) {

    public AnalyzerWarning {
        Objects.requireNonNull(type, "type is mandatory");
        Objects.requireNonNull(failureEventId, "failureEventId is mandatory");
        Objects.requireNonNull(detail, "detail is mandatory");
    }
}
