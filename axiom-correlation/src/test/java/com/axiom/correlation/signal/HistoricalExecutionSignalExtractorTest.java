package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.HistoricalExecutionEvidence;
import com.axiom.correlation.model.HistoricalOutcome;
import com.axiom.correlation.model.HistoricalTestRun;
import com.axiom.correlation.model.TestIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalExecutionSignalExtractorTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final TestIdentity IDENTITY = new TestIdentity("com.example.PaymentServiceTest", "testCharge");

    private static HistoricalExecutionEvidence evidence(HistoricalOutcome... outcomes) {
        List<HistoricalTestRun> runs = new ArrayList<>();
        for (int i = 0; i < outcomes.length; i++) {
            runs.add(new HistoricalTestRun("build-" + i, NOW.minusSeconds(i), outcomes[i]));
        }
        return new HistoricalExecutionEvidence("evidence-history", NOW, IDENTITY, Optional.empty(), runs);
    }

    private static Map<SignalType, Signal> index(Set<Signal> signals) {
        return signals.stream().collect(Collectors.toMap(Signal::type, s -> s));
    }

    private static boolean present(Map<SignalType, Signal> signals, SignalType type) {
        return signals.get(type).present();
    }

    @Test
    void emptyHistoryProducesNoHistoricalStateSignals() {
        Map<SignalType, Signal> signals = index(HistoricalExecutionSignalExtractor.deriveSignals(evidence()));

        assertFalse(present(signals, SignalType.HISTORICAL_EXECUTION_PRESENT));
        assertFalse(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertFalse(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void onePass() {
        Map<SignalType, Signal> signals = index(
            HistoricalExecutionSignalExtractor.deriveSignals(evidence(HistoricalOutcome.PASSED)));

        assertTrue(present(signals, SignalType.HISTORICAL_EXECUTION_PRESENT));
        assertFalse(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertFalse(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertTrue(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void oneFailure() {
        Map<SignalType, Signal> signals = index(
            HistoricalExecutionSignalExtractor.deriveSignals(evidence(HistoricalOutcome.FAILED)));

        assertTrue(present(signals, SignalType.HISTORICAL_EXECUTION_PRESENT));
        assertFalse(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertFalse(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertTrue(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void mixedOutcomesBelowThreshold() {
        Map<SignalType, Signal> signals = index(HistoricalExecutionSignalExtractor.deriveSignals(
            evidence(HistoricalOutcome.PASSED, HistoricalOutcome.FAILED)));

        assertTrue(present(signals, SignalType.HISTORICAL_EXECUTION_PRESENT));
        assertFalse(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertTrue(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void mixedOutcomesAtExactlyFive() {
        Map<SignalType, Signal> signals = index(HistoricalExecutionSignalExtractor.deriveSignals(evidence(
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED, HistoricalOutcome.FAILED,
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED)));

        assertTrue(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertTrue(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void allPassedAtExactlyFive() {
        Map<SignalType, Signal> signals = index(HistoricalExecutionSignalExtractor.deriveSignals(evidence(
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED, HistoricalOutcome.PASSED,
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED)));

        assertTrue(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertFalse(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertTrue(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void allFailedAtExactlyFive() {
        Map<SignalType, Signal> signals = index(HistoricalExecutionSignalExtractor.deriveSignals(evidence(
            HistoricalOutcome.FAILED, HistoricalOutcome.FAILED, HistoricalOutcome.FAILED,
            HistoricalOutcome.FAILED, HistoricalOutcome.FAILED)));

        assertTrue(present(signals, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertFalse(present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertFalse(present(signals, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertTrue(present(signals, SignalType.HISTORICAL_ALWAYS_FAILED));
    }

    @Test
    void thresholdBoundaryFourVersusFive() {
        Map<SignalType, Signal> four = index(HistoricalExecutionSignalExtractor.deriveSignals(evidence(
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED, HistoricalOutcome.PASSED, HistoricalOutcome.PASSED)));
        Map<SignalType, Signal> five = index(HistoricalExecutionSignalExtractor.deriveSignals(evidence(
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED, HistoricalOutcome.PASSED,
            HistoricalOutcome.PASSED, HistoricalOutcome.PASSED)));

        assertFalse(present(four, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertTrue(present(five, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
    }

    @Test
    void terminalSignalsAreNeverBothTrueAndNeverCoexistWithMixed() {
        List<HistoricalExecutionEvidence> cases = List.of(
            evidence(),
            evidence(HistoricalOutcome.PASSED),
            evidence(HistoricalOutcome.FAILED),
            evidence(HistoricalOutcome.PASSED, HistoricalOutcome.FAILED),
            evidence(HistoricalOutcome.PASSED, HistoricalOutcome.PASSED, HistoricalOutcome.PASSED,
                HistoricalOutcome.PASSED, HistoricalOutcome.PASSED),
            evidence(HistoricalOutcome.FAILED, HistoricalOutcome.FAILED, HistoricalOutcome.FAILED,
                HistoricalOutcome.FAILED, HistoricalOutcome.FAILED));

        for (HistoricalExecutionEvidence testCase : cases) {
            Map<SignalType, Signal> signals = index(HistoricalExecutionSignalExtractor.deriveSignals(testCase));
            boolean alwaysPassed = present(signals, SignalType.HISTORICAL_ALWAYS_PASSED);
            boolean alwaysFailed = present(signals, SignalType.HISTORICAL_ALWAYS_FAILED);
            boolean mixed = present(signals, SignalType.HISTORICAL_MIXED_OUTCOMES);

            assertFalse(alwaysPassed && alwaysFailed, "ALWAYS_PASSED and ALWAYS_FAILED must never both be true");
            assertFalse(mixed && alwaysPassed, "MIXED_OUTCOMES and ALWAYS_PASSED must never coexist");
            assertFalse(mixed && alwaysFailed, "MIXED_OUTCOMES and ALWAYS_FAILED must never coexist");
        }
    }

    @Test
    void extractReturnsAllFalseSignalsWhenNoHistoricalEvidencePresent() {
        List<CorrelationEvidence> noHistoricalEvidence = List.of();

        List<Signal> signals = new HistoricalExecutionSignalExtractor().extract(noHistoricalEvidence);
        Map<SignalType, Signal> indexed = signals.stream().collect(Collectors.toMap(Signal::type, s -> s));

        assertFalse(present(indexed, SignalType.HISTORICAL_EXECUTION_PRESENT));
        assertFalse(present(indexed, SignalType.HISTORICAL_SAMPLE_SUFFICIENT));
        assertFalse(present(indexed, SignalType.HISTORICAL_MIXED_OUTCOMES));
        assertFalse(present(indexed, SignalType.HISTORICAL_ALWAYS_PASSED));
        assertFalse(present(indexed, SignalType.HISTORICAL_ALWAYS_FAILED));
    }
}
