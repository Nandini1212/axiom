package com.axiom.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserWarningTest {

    @Test
    void constructsWithAllFieldsPresent() {
        ParserWarning warning = new ParserWarning(
            WarningType.MISSING_ATTRIBUTE, "testA", "com.example.A", "SuiteA", "some detail");

        assertEquals(WarningType.MISSING_ATTRIBUTE, warning.type());
        assertEquals("testA", warning.testcaseName());
        assertEquals("com.example.A", warning.className());
        assertEquals("SuiteA", warning.suiteName());
        assertEquals("some detail", warning.detail());
    }

    @Test
    void identifyingFieldsMayAllBeNull() {
        ParserWarning warning = new ParserWarning(
            WarningType.MISSING_ATTRIBUTE, null, null, null, "some detail");

        assertNull(warning.testcaseName());
        assertNull(warning.className());
        assertNull(warning.suiteName());
    }

    @Test
    void throwsWhenTypeIsNull() {
        assertThrows(NullPointerException.class, () ->
            new ParserWarning(null, "testA", "com.example.A", "SuiteA", "detail"));
    }

    @Test
    void throwsWhenDetailIsNull() {
        assertThrows(NullPointerException.class, () ->
            new ParserWarning(WarningType.MISSING_ATTRIBUTE, "testA", "com.example.A", "SuiteA", null));
    }
}
