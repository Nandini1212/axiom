package com.axiom.classifier.model;

import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.RuleField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceTest {

    @Test
    void constructsWithAllFieldsPresent() {
        Evidence evidence = new Evidence(
            RuleField.MESSAGE, Operator.CONTAINS, "Connection refused",
            "Connection refused: could not connect", "Dependent service unavailable");

        assertEquals(RuleField.MESSAGE, evidence.field());
        assertEquals(Operator.CONTAINS, evidence.operator());
        assertEquals("Connection refused", evidence.expectedValue());
        assertEquals("Connection refused: could not connect", evidence.actualValue());
        assertEquals("Dependent service unavailable", evidence.explanation());
    }

    @Test
    void explanationMayBeNull() {
        Evidence evidence = new Evidence(
            RuleField.MESSAGE, Operator.CONTAINS, "boom", "boom happened", null);

        assertNull(evidence.explanation());
    }

    @Test
    void throwsWhenFieldIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Evidence(null, Operator.CONTAINS, "boom", "boom happened", null));
    }

    @Test
    void throwsWhenOperatorIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Evidence(RuleField.MESSAGE, null, "boom", "boom happened", null));
    }

    @Test
    void throwsWhenExpectedValueIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Evidence(RuleField.MESSAGE, Operator.CONTAINS, null, "boom happened", null));
    }

    @Test
    void throwsWhenActualValueIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Evidence(RuleField.MESSAGE, Operator.CONTAINS, "boom", null, null));
    }
}
