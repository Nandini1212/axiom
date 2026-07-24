package com.axiom.investigation.model;

import java.util.Objects;

/**
 * A structured collection-time warning, not a plain {@code String} — this codebase has already
 * relearned that lesson twice ({@code ParserWarning}, {@code HistoryWarning}), so this type starts
 * typed rather than repeating the string-first-then-refactor cycle a third time (see
 * {@code 17-investigation-architecture.md} §5). {@code collectorId} says which collector produced
 * it; {@code type} says what kind of problem it is; {@code message} stays human-readable.
 */
public record CollectionWarning(String collectorId, CollectionWarningType type, String message) {

    public CollectionWarning {
        Objects.requireNonNull(collectorId, "collectorId is mandatory");
        Objects.requireNonNull(type, "type is mandatory");
        Objects.requireNonNull(message, "message is mandatory");
    }

    public static CollectionWarning duplicateEvidenceId(String collectorId, String evidenceId) {
        return new CollectionWarning(collectorId, CollectionWarningType.DUPLICATE_EVIDENCE_ID,
            "Duplicate evidenceId " + evidenceId + ", first occurrence retained");
    }
}
