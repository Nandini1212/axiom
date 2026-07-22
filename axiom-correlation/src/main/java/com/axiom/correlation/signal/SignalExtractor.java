package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;

import java.util.List;

/**
 * Derives zero or more {@link Signal}s from the full set of available evidence. Each implementation
 * is independently constructible and testable — no shared base class, mirroring
 * {@code RuleSource}'s single-implementation-today approach in {@code axiom-classifier}.
 */
public interface SignalExtractor {

    List<Signal> extract(List<CorrelationEvidence> evidence);
}
