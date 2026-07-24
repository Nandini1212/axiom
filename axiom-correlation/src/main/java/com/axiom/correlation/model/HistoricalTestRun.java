package com.axiom.correlation.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One prior, completed execution of a test. Raw and immutable — no derived judgment
 * ({@code isFlaky}, a computed rate, etc.) belongs here; see
 * {@code docs/15-historical-execution-evidence-design.md} §4 for why the evidence layer stays
 * strictly factual and leaves interpretation to signal extraction.
 */
public record HistoricalTestRun(String runId, Instant timestamp, HistoricalOutcome outcome) {

    public HistoricalTestRun {
        Objects.requireNonNull(runId, "runId is mandatory");
        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(timestamp, "timestamp is mandatory");
        Objects.requireNonNull(outcome, "outcome is mandatory");
    }
}
