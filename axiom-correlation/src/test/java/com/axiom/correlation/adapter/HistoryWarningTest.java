package com.axiom.correlation.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoryWarningTest {

    @Test
    void constructsWithValidFields() {
        HistoryWarning warning = new HistoryWarning("build-1042", "Duplicate runId");

        assertEquals("build-1042", warning.runId());
        assertEquals("Duplicate runId", warning.message());
    }

    @Test
    void rejectsNullRunId() {
        assertThrows(NullPointerException.class, () -> new HistoryWarning(null, "message"));
    }

    @Test
    void rejectsNullMessage() {
        assertThrows(NullPointerException.class, () -> new HistoryWarning("build-1042", null));
    }

    @Test
    void duplicateRunIdFactoryCarriesTheRunId() {
        HistoryWarning warning = HistoryWarning.duplicateRunId("build-1042");

        assertEquals("build-1042", warning.runId());
        assertFalse(warning.message().isBlank());
    }
}
