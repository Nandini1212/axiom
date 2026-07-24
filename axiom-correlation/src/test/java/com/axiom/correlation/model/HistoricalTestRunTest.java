package com.axiom.correlation.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalTestRunTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void constructsWithValidFields() {
        HistoricalTestRun run = new HistoricalTestRun("build-1042", NOW, HistoricalOutcome.PASSED);

        assertEquals("build-1042", run.runId());
        assertEquals(NOW, run.timestamp());
        assertEquals(HistoricalOutcome.PASSED, run.outcome());
    }

    @Test
    void rejectsNullRunId() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalTestRun(null, NOW, HistoricalOutcome.PASSED));
    }

    @Test
    void rejectsBlankRunId() {
        assertThrows(IllegalArgumentException.class,
            () -> new HistoricalTestRun(" ", NOW, HistoricalOutcome.PASSED));
    }

    @Test
    void rejectsNullTimestamp() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalTestRun("build-1042", null, HistoricalOutcome.PASSED));
    }

    @Test
    void rejectsNullOutcome() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalTestRun("build-1042", NOW, null));
    }
}
