package com.axiom.analyzer;

import com.axiom.parser.ParserWarning;

import java.util.List;
import java.util.Objects;

/**
 * The canonical output of one {@link Analyzer#analyze} call: every failure and its
 * classification, plus every parser-level warning carried through unchanged — the same
 * "no silent data loss" principle {@code ParserResult} established has to survive this layer too,
 * or a malformed testcase's warning would quietly vanish the moment something orchestrates the
 * parser instead of calling it directly.
 * <p>
 * May gain execution metadata (parser used, analysis timestamp, duration, version) in future
 * versions — deliberately not added now, before any concrete need exists for it.
 */
public record AnalysisResult(List<AnalyzedFailure> analyses, List<ParserWarning> parserWarnings) {

    public AnalysisResult {
        Objects.requireNonNull(analyses, "analyses is mandatory");
        Objects.requireNonNull(parserWarnings, "parserWarnings is mandatory");
        analyses = List.copyOf(analyses);
        parserWarnings = List.copyOf(parserWarnings);
    }
}
