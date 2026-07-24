package com.axiom.correlation.adapter;

import com.axiom.correlation.model.HistoricalOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Validation tests for the three wire-format DTOs {@link HistoryFileAdapter} consumes. */
class HistoryInputTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Test
    void historicalRunInputRejectsNullFields() {
        assertThrows(NullPointerException.class,
            () -> new HistoricalRunInput(null, NOW, HistoricalOutcome.PASSED));
        assertThrows(NullPointerException.class,
            () -> new HistoricalRunInput("build-1042", null, HistoricalOutcome.PASSED));
        assertThrows(NullPointerException.class,
            () -> new HistoricalRunInput("build-1042", NOW, null));
    }

    @Test
    void historicalTestInputDefensivelyCopiesRuns() {
        List<HistoricalRunInput> runs = List.of(new HistoricalRunInput("build-1042", NOW, HistoricalOutcome.PASSED));
        HistoricalTestInput input = new HistoricalTestInput("com.example.Foo", "testBar", runs);

        assertEquals(1, input.runs().size());
        assertThrows(UnsupportedOperationException.class,
            () -> input.runs().add(new HistoricalRunInput("x", NOW, HistoricalOutcome.PASSED)));
    }

    @Test
    void historyInputRejectsNullBranchOptional() {
        assertThrows(NullPointerException.class,
            () -> new HistoryInput("1.0", NOW, null, List.of()));
    }

    @Test
    void historyInputAllowsAbsentBranch() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of());

        assertTrue(input.branch().isEmpty());
    }
}
