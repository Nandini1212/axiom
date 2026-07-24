package com.axiom.correlation.adapter;

import java.util.Objects;

/**
 * A recoverable problem noticed while adapting one test's history — kept as a structured value,
 * not a raw {@code String}, so a future renderer/CLI/AI-prompt consumer can use {@code runId}
 * directly rather than parsing it back out of an English sentence. Deliberately not a typed enum
 * taxonomy yet ({@code HistoryWarningType}-style) — duplicate {@code runId} is the only concrete
 * case that exists today; introduce a taxonomy once a second case actually shows up, not before.
 */
public record HistoryWarning(String runId, String message) {

    public HistoryWarning {
        Objects.requireNonNull(runId, "runId is mandatory");
        Objects.requireNonNull(message, "message is mandatory");
    }

    /**
     * The English wording lives here, with the warning it describes — not scattered across
     * whichever adapter code happens to construct one. {@link HistoryFileAdapter}'s job reduces to
     * "duplicate detected" → this factory, not "duplicate detected" → its own prose.
     */
    public static HistoryWarning duplicateRunId(String runId) {
        return new HistoryWarning(runId, "Duplicate runId in history.json - keeping first occurrence");
    }
}
