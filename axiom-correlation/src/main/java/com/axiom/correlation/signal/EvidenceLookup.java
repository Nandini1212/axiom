package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;

import java.util.List;
import java.util.Optional;

/**
 * Shared by all four signal extractors — a concrete, immediate duplication this package actually
 * has today, not a speculative shared base class.
 */
final class EvidenceLookup {

    static <T extends CorrelationEvidence> Optional<T> find(
            List<CorrelationEvidence> evidence, EvidenceType type, Class<T> clazz) {
        return evidence.stream().filter(e -> e.type() == type).map(clazz::cast).findFirst();
    }

    private EvidenceLookup() {
    }
}
