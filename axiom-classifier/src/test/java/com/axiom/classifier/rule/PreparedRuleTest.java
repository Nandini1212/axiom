package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreparedRuleTest {

    private static final PreparedMatchGroup SOME_MATCH = new PreparedMatchGroup(
        List.of(new PreparedCondition(RuleField.MESSAGE, Operator.CONTAINS, "boom", false, null)),
        null);

    @Test
    void constructsWithValidFields() {
        PreparedRule rule = new PreparedRule(
            "connection-refused", 100, SOME_MATCH,
            FailureCategory.INFRASTRUCTURE_FAILURE, 0.95, "Dependent service unavailable");

        assertEquals("connection-refused", rule.id());
        assertEquals(100, rule.priority());
        assertEquals(SOME_MATCH, rule.match());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, rule.category());
        assertEquals(0.95, rule.confidence());
        assertEquals("Dependent service unavailable", rule.evidenceMessage());
    }

    @Test
    void evidenceMessageMayBeNull() {
        PreparedRule rule = new PreparedRule(
            "connection-refused", 0, SOME_MATCH, FailureCategory.UNKNOWN, 0.5, null);

        assertNull(rule.evidenceMessage());
    }

    @Test
    void throwsWhenIdIsNull() {
        assertThrows(NullPointerException.class, () ->
            new PreparedRule(null, 0, SOME_MATCH, FailureCategory.UNKNOWN, 0.5, null));
    }

    @Test
    void throwsWhenMatchIsNull() {
        assertThrows(NullPointerException.class, () ->
            new PreparedRule("id", 0, null, FailureCategory.UNKNOWN, 0.5, null));
    }

    @Test
    void throwsWhenCategoryIsNull() {
        assertThrows(NullPointerException.class, () ->
            new PreparedRule("id", 0, SOME_MATCH, null, 0.5, null));
    }

    @Test
    void throwsWhenConfidenceOutOfRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new PreparedRule("id", 0, SOME_MATCH, FailureCategory.UNKNOWN, 1.5, null));
    }

    @Test
    void allowsNegativeAndZeroPriority() {
        PreparedRule negative = new PreparedRule(
            "fallback", -100, SOME_MATCH, FailureCategory.UNKNOWN, 0.1, null);
        PreparedRule zero = new PreparedRule(
            "id", 0, SOME_MATCH, FailureCategory.UNKNOWN, 0.1, null);

        assertEquals(-100, negative.priority());
        assertEquals(0, zero.priority());
    }
}
