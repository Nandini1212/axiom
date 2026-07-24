package com.axiom.correlation.signal;

/**
 * Centralized threshold(s) for interpreting {@code HistoricalExecutionEvidence} — a named
 * constant in a policy class, not inline in the extractor, since the future rule layer
 * ({@code HistoricalFlakyTestRule}) is expected to need the same number (e.g. to decide whether a
 * "mixed outcomes" signal alone is enough, or whether it also needs
 * {@code HISTORICAL_SAMPLE_SUFFICIENT}). Public so both packages can reference the one definition.
 */
public final class HistoricalExecutionPolicy {

    /**
     * Below this many usable runs, history may still be shown for transparency but must not
     * produce a strong historical-flakiness signal — see
     * {@code docs/15-historical-execution-evidence-design.md} §8.
     */
    public static final int MINIMUM_USABLE_RUNS = 5;

    private HistoricalExecutionPolicy() {
    }
}
