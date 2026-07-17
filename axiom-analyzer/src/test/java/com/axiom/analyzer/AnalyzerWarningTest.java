package com.axiom.analyzer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerWarningTest {

    @Test
    void constructsWithAllFieldsPresent() {
        AnalyzerWarning warning = new AnalyzerWarning(AnalyzerWarningType.AI_TIMEOUT, "evt-1", "timed out");

        assertEquals(AnalyzerWarningType.AI_TIMEOUT, warning.type());
        assertEquals("evt-1", warning.failureEventId());
        assertEquals("timed out", warning.detail());
    }

    @Test
    void throwsWhenTypeIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalyzerWarning(null, "evt-1", "detail"));
    }

    @Test
    void throwsWhenFailureEventIdIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AnalyzerWarning(AnalyzerWarningType.AI_TIMEOUT, null, "detail"));
    }

    @Test
    void throwsWhenDetailIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AnalyzerWarning(AnalyzerWarningType.AI_TIMEOUT, "evt-1", null));
    }
}
