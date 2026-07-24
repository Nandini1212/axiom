package com.axiom.correlation.adapter;

import com.axiom.correlation.model.HistoricalOutcome;

import java.time.Instant;
import java.util.Objects;

/**
 * The wire-format shape one entry of {@code history.json}'s {@code runs} array deserializes into.
 * Reuses {@link HistoricalOutcome} directly rather than a separate wire-only enum — Jackson
 * deserializes enums by name with no extra code, and there's no divergence to guard against here
 * the way there would be for a richer domain type.
 */
public record HistoricalRunInput(String runId, Instant timestamp, HistoricalOutcome outcome) {

    public HistoricalRunInput {
        Objects.requireNonNull(runId, "runId is mandatory");
        Objects.requireNonNull(timestamp, "timestamp is mandatory");
        Objects.requireNonNull(outcome, "outcome is mandatory");
    }
}
