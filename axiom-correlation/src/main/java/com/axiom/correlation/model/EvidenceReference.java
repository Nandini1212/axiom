package com.axiom.correlation.model;

import java.util.Objects;

/**
 * A lightweight pointer to a specific {@link CorrelationEvidence} instance plus a short
 * human-readable excerpt of why it's relevant — kept small and serializable rather than embedding
 * the full evidence object into a {@code RootCauseHypothesis}.
 */
public record EvidenceReference(String evidenceId, String excerpt) {

    public EvidenceReference {
        Objects.requireNonNull(evidenceId, "evidenceId is mandatory");
        Objects.requireNonNull(excerpt, "excerpt is mandatory");
    }
}
