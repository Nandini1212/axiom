package com.axiom.correlation.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The wire-format shape a whole {@code history.json} file deserializes into. One {@code branch}
 * per file (the file describes one branch's history), not per-run — matched against the current
 * execution's branch by {@link HistoryFileAdapter}, not by this type itself.
 * <p>
 * Deliberately no {@code flakeRate}/{@code failureRate} field — a source file that could assert
 * its own computed rate is the same "second source of truth" problem this project avoids
 * elsewhere between docs and code; here it would be input data vs. engine calculation.
 */
public record HistoryInput(
        String schemaVersion, Instant generatedAt, Optional<String> branch, List<HistoricalTestInput> tests) {

    public HistoryInput {
        Objects.requireNonNull(schemaVersion, "schemaVersion is mandatory");
        Objects.requireNonNull(generatedAt, "generatedAt is mandatory");
        Objects.requireNonNull(branch, "branch is mandatory (use Optional.empty(), not null)");
        Objects.requireNonNull(tests, "tests is mandatory");
        tests = List.copyOf(tests);
    }
}
