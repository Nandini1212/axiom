package com.axiom.correlation.signal;

import java.util.List;
import java.util.Objects;

/**
 * One derived fact about the available evidence: whether it's {@code present}, and which
 * evidence ids support that determination (for traceability back into a hypothesis's evidence
 * trail).
 */
public record Signal(SignalType type, boolean present, List<String> evidenceIds) {

    public Signal {
        Objects.requireNonNull(type, "type is mandatory");
        Objects.requireNonNull(evidenceIds, "evidenceIds is mandatory");
        evidenceIds = List.copyOf(evidenceIds);
    }
}
