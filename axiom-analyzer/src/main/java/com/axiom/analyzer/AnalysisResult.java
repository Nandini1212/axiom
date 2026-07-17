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
 * {@code analyzerWarnings} is a separate list from {@code parserWarnings}, not folded into it —
 * a parser-level problem (e.g. a malformed testcase) and an analyzer-level one (e.g. an LLM call
 * timing out) are different kinds of diagnostic, and {@code ParserWarning}'s own
 * {@code WarningType} values have nothing that fits an AI-layer failure.
 * <p>
 * May gain execution metadata (parser used, analysis timestamp, duration, version) in future
 * versions — deliberately not added now, before any concrete need exists for it.
 */
public record AnalysisResult(
    List<AnalyzedFailure> analyses,
    List<ParserWarning> parserWarnings,
    List<AnalyzerWarning> analyzerWarnings
) {

    public AnalysisResult {
        Objects.requireNonNull(analyses, "analyses is mandatory");
        Objects.requireNonNull(parserWarnings, "parserWarnings is mandatory");
        Objects.requireNonNull(analyzerWarnings, "analyzerWarnings is mandatory");
        analyses = List.copyOf(analyses);
        parserWarnings = List.copyOf(parserWarnings);
        analyzerWarnings = List.copyOf(analyzerWarnings);
    }

    /** Deterministic-only construction: no analyzer-level (AI) warnings possible. */
    public AnalysisResult(List<AnalyzedFailure> analyses, List<ParserWarning> parserWarnings) {
        this(analyses, parserWarnings, List.of());
    }
}
