package com.axiom.investigation.model;

import com.axiom.correlation.model.CorrelationEvidence;

import java.util.List;
import java.util.Objects;

/**
 * The return type of {@code EvidenceCollector.collect(...)} — the {@code *Result}-shaped
 * convention already established by {@code ParserResult}/{@code AnalysisResult}
 * ({@code 02-system-architecture.md}'s API Conventions section): a collector's primary output is
 * the evidence it gathered, but a partial failure must surface as a warning, never silently
 * disappear.
 */
public record CollectedEvidence(List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {

    public CollectedEvidence {
        Objects.requireNonNull(evidence, "evidence is mandatory");
        Objects.requireNonNull(warnings, "warnings is mandatory");
        evidence = List.copyOf(evidence);
        warnings = List.copyOf(warnings);
    }
}
