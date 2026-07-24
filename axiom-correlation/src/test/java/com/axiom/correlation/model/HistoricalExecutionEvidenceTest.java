package com.axiom.correlation.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalExecutionEvidenceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final TestIdentity IDENTITY = new TestIdentity("com.example.PaymentServiceTest", "testCharge");

    private static List<HistoricalTestRun> runs() {
        return List.of(
            new HistoricalTestRun("build-1042", NOW, HistoricalOutcome.PASSED),
            new HistoricalTestRun("build-1041", NOW.minusSeconds(3600), HistoricalOutcome.FAILED));
    }

    @Test
    void constructsWithValidFields() {
        HistoricalExecutionEvidence evidence = new HistoricalExecutionEvidence(
            "evidence-history", NOW, IDENTITY, Optional.of("main"), runs());

        assertEquals("evidence-history", evidence.evidenceId());
        assertEquals(IDENTITY, evidence.testIdentity());
        assertEquals(Optional.of("main"), evidence.branch());
        assertEquals(2, evidence.runs().size());
        assertEquals(EvidenceType.HISTORICAL_EXECUTION, evidence.type());
    }

    @Test
    void branchIsOptionalNotNullable() {
        HistoricalExecutionEvidence evidence = new HistoricalExecutionEvidence(
            "evidence-history", NOW, IDENTITY, Optional.empty(), runs());

        assertTrue(evidence.branch().isEmpty());
    }

    @Test
    void emptyRunsListIsAllowed() {
        HistoricalExecutionEvidence evidence = new HistoricalExecutionEvidence(
            "evidence-history", NOW, IDENTITY, Optional.empty(), List.of());

        assertTrue(evidence.runs().isEmpty());
    }

    @Test
    void runsListIsDefensivelyCopiedAndImmutable() {
        List<HistoricalTestRun> mutable = new ArrayList<>(runs());
        HistoricalExecutionEvidence evidence = new HistoricalExecutionEvidence(
            "evidence-history", NOW, IDENTITY, Optional.empty(), mutable);

        mutable.add(new HistoricalTestRun("build-1040", NOW, HistoricalOutcome.PASSED));

        assertEquals(2, evidence.runs().size());
        assertThrows(UnsupportedOperationException.class,
            () -> evidence.runs().add(new HistoricalTestRun("x", NOW, HistoricalOutcome.PASSED)));
    }

    @Test
    void rejectsBlankEvidenceId() {
        assertThrows(IllegalArgumentException.class,
            () -> new HistoricalExecutionEvidence(" ", NOW, IDENTITY, Optional.empty(), runs()));
    }

    @Test
    void rejectsNullObservedAt() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalExecutionEvidence("evidence-history", null, IDENTITY, Optional.empty(), runs()));
    }

    @Test
    void rejectsNullTestIdentity() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalExecutionEvidence("evidence-history", NOW, null, Optional.empty(), runs()));
    }

    @Test
    void rejectsNullBranchOptional() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalExecutionEvidence("evidence-history", NOW, IDENTITY, null, runs()));
    }

    @Test
    void rejectsNullRuns() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalExecutionEvidence("evidence-history", NOW, IDENTITY, Optional.empty(), null));
    }
}
