package com.axiom.correlation.engine;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.signal.Signal;

import java.util.List;
import java.util.Objects;

/**
 * Everything a {@link CorrelationRule} needs to evaluate: the extracted signals and the raw
 * evidence they were derived from (rules occasionally need the evidence directly too, e.g. to read
 * an existing classification category — see {@code ApplicationBugCorrelationRule}).
 */
public record CorrelationContext(List<Signal> signals, List<CorrelationEvidence> evidence) {

    public CorrelationContext {
        Objects.requireNonNull(signals, "signals is mandatory");
        Objects.requireNonNull(evidence, "evidence is mandatory");
        signals = List.copyOf(signals);
        evidence = List.copyOf(evidence);
    }
}
