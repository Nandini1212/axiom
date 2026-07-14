package com.axiom.classifier.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionTest {

    @Test
    void constructsWithAllFieldsPresent() {
        Condition condition = new Condition(RuleField.MESSAGE, Operator.CONTAINS, "boom", true);

        assertEquals(RuleField.MESSAGE, condition.field());
        assertEquals(Operator.CONTAINS, condition.operator());
        assertEquals("boom", condition.value());
        assertEquals(Boolean.TRUE, condition.caseSensitive());
    }

    @Test
    void caseSensitiveIsNullWhenNotProvided_notDefaultedHere() {
        // Defaulting is RuleProcessor's job, not Condition's.
        Condition condition = new Condition(RuleField.MESSAGE, Operator.CONTAINS, "boom", null);

        assertNull(condition.caseSensitive());
    }

    @Test
    void throwsWhenFieldIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Condition(null, Operator.CONTAINS, "boom", null));
    }

    @Test
    void throwsWhenOperatorIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Condition(RuleField.MESSAGE, null, "boom", null));
    }

    @Test
    void throwsWhenValueIsNull() {
        assertThrows(NullPointerException.class, () ->
            new Condition(RuleField.MESSAGE, Operator.CONTAINS, null, null));
    }
}
