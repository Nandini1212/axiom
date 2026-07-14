package com.axiom.classifier.model;

import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.RuleField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleMatchTest {

    private static final Evidence SOME_EVIDENCE = new Evidence(
        RuleField.MESSAGE, Operator.CONTAINS, "boom", "boom happened", null);

    @Test
    void constructsWithValidFields() {
        RuleMatch match = new RuleMatch(
            "connection-refused", 100, FailureCategory.INFRASTRUCTURE_FAILURE, 0.95,
            List.of(SOME_EVIDENCE));

        assertEquals("connection-refused", match.ruleId());
        assertEquals(100, match.priority());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, match.category());
        assertEquals(0.95, match.confidence());
        assertEquals(List.of(SOME_EVIDENCE), match.evidence());
    }

    @Test
    void throwsWhenEvidenceIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            new RuleMatch("id", 0, FailureCategory.UNKNOWN, 0.5, List.of()));
    }

    @Test
    void throwsWhenRuleIdIsNull() {
        assertThrows(NullPointerException.class, () ->
            new RuleMatch(null, 0, FailureCategory.UNKNOWN, 0.5, List.of(SOME_EVIDENCE)));
    }

    @Test
    void throwsWhenCategoryIsNull() {
        assertThrows(NullPointerException.class, () ->
            new RuleMatch("id", 0, null, 0.5, List.of(SOME_EVIDENCE)));
    }

    @Test
    void throwsWhenEvidenceIsNull() {
        assertThrows(NullPointerException.class, () ->
            new RuleMatch("id", 0, FailureCategory.UNKNOWN, 0.5, null));
    }

    @Test
    void evidenceIsDefensivelyCopiedAndImmutable() {
        List<Evidence> mutable = new ArrayList<>();
        mutable.add(SOME_EVIDENCE);

        RuleMatch match = new RuleMatch("id", 0, FailureCategory.UNKNOWN, 0.5, mutable);
        mutable.add(SOME_EVIDENCE);

        assertEquals(1, match.evidence().size());
        assertThrows(UnsupportedOperationException.class, () -> match.evidence().add(SOME_EVIDENCE));
    }
}
