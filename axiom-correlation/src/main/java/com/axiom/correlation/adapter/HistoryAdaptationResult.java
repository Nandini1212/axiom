package com.axiom.correlation.adapter;

import com.axiom.correlation.model.HistoricalExecutionEvidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code evidence} is empty when the current test has no usable history at all (not found in the
 * file, or the file's branch doesn't match the current execution's — see
 * {@link HistoryFileAdapter}) — that's a legitimate "no evidence," not an error. {@code warnings}
 * mirrors {@code ParserWarning}'s "no silent data loss" principle at this layer: today the only
 * concrete case is a duplicate {@code runId} within the matched test's runs (first occurrence
 * kept, not silently overwritten or merged). Structured {@link HistoryWarning} values, not raw
 * strings — a future renderer/CLI/AI-prompt consumer can use {@code runId} directly rather than
 * parsing English sentences back apart.
 */
public record HistoryAdaptationResult(Optional<HistoricalExecutionEvidence> evidence, List<HistoryWarning> warnings) {

    public HistoryAdaptationResult {
        Objects.requireNonNull(evidence, "evidence is mandatory (use Optional.empty(), not null)");
        Objects.requireNonNull(warnings, "warnings is mandatory");
        warnings = List.copyOf(warnings);
    }
}
