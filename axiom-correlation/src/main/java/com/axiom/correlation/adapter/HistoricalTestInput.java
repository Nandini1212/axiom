package com.axiom.correlation.adapter;

import java.util.List;
import java.util.Objects;

/**
 * The wire-format shape one entry of {@code history.json}'s {@code tests} array deserializes
 * into — {@code className}/{@code testName} here, not a {@code TestIdentity}, since this is the
 * raw wire shape; {@link HistoryFileAdapter} is where matching against a real
 * {@code TestIdentity} happens.
 */
public record HistoricalTestInput(String className, String testName, List<HistoricalRunInput> runs) {

    public HistoricalTestInput {
        Objects.requireNonNull(className, "className is mandatory");
        Objects.requireNonNull(testName, "testName is mandatory");
        Objects.requireNonNull(runs, "runs is mandatory");
        runs = List.copyOf(runs);
    }
}
