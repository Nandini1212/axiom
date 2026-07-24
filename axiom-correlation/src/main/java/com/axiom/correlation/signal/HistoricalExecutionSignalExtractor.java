package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.HistoricalExecutionEvidence;
import com.axiom.correlation.model.HistoricalOutcome;
import com.axiom.correlation.model.HistoricalTestRun;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Derives the five historical signals from {@code docs/15-historical-execution-evidence-design.md}
 * §9. Pure interpretation of already-adapted evidence — no warnings, no rule decisions, no
 * confidence, no flake-rate calculation (that stays out of scope for this slice; signals only).
 */
public final class HistoricalExecutionSignalExtractor implements SignalExtractor {

    @Override
    public List<Signal> extract(List<CorrelationEvidence> evidence) {
        Optional<HistoricalExecutionEvidence> historical =
            EvidenceLookup.find(evidence, EvidenceType.HISTORICAL_EXECUTION, HistoricalExecutionEvidence.class);

        Set<Signal> signals = historical.map(HistoricalExecutionSignalExtractor::deriveSignals)
            .orElseGet(HistoricalExecutionSignalExtractor::absentSignals);

        return List.copyOf(signals);
    }

    /**
     * The pure computation, directly testable against a {@link HistoricalExecutionEvidence}
     * without needing to wrap it in a full evidence list or exercise the "absent" branch.
     */
    static Set<Signal> deriveSignals(HistoricalExecutionEvidence evidence) {
        List<HistoricalTestRun> runs = evidence.runs();
        List<String> evidenceIds = List.of(evidence.evidenceId());

        boolean present = !runs.isEmpty();
        boolean sufficient = runs.size() >= HistoricalExecutionPolicy.MINIMUM_USABLE_RUNS;
        boolean hasPassed = runs.stream().anyMatch(run -> run.outcome() == HistoricalOutcome.PASSED);
        boolean hasFailed = runs.stream().anyMatch(run -> run.outcome() == HistoricalOutcome.FAILED);
        boolean mixed = hasPassed && hasFailed;
        // "present &&" guards against Stream.allMatch's vacuous truth on an empty stream - an
        // empty sample must satisfy neither ALWAYS_PASSED nor ALWAYS_FAILED.
        boolean alwaysPassed = present && runs.stream().allMatch(run -> run.outcome() == HistoricalOutcome.PASSED);
        boolean alwaysFailed = present && runs.stream().allMatch(run -> run.outcome() == HistoricalOutcome.FAILED);

        Set<Signal> signals = new LinkedHashSet<>();
        signals.add(signal(SignalType.HISTORICAL_EXECUTION_PRESENT, present, evidenceIds));
        signals.add(signal(SignalType.HISTORICAL_SAMPLE_SUFFICIENT, sufficient, evidenceIds));
        signals.add(signal(SignalType.HISTORICAL_MIXED_OUTCOMES, mixed, evidenceIds));
        signals.add(signal(SignalType.HISTORICAL_ALWAYS_PASSED, alwaysPassed, evidenceIds));
        signals.add(signal(SignalType.HISTORICAL_ALWAYS_FAILED, alwaysFailed, evidenceIds));
        return signals;
    }

    private static Set<Signal> absentSignals() {
        Set<Signal> signals = new LinkedHashSet<>();
        signals.add(new Signal(SignalType.HISTORICAL_EXECUTION_PRESENT, false, List.of()));
        signals.add(new Signal(SignalType.HISTORICAL_SAMPLE_SUFFICIENT, false, List.of()));
        signals.add(new Signal(SignalType.HISTORICAL_MIXED_OUTCOMES, false, List.of()));
        signals.add(new Signal(SignalType.HISTORICAL_ALWAYS_PASSED, false, List.of()));
        signals.add(new Signal(SignalType.HISTORICAL_ALWAYS_FAILED, false, List.of()));
        return signals;
    }

    private static Signal signal(SignalType type, boolean present, List<String> evidenceIds) {
        return new Signal(type, present, present ? evidenceIds : List.of());
    }
}
