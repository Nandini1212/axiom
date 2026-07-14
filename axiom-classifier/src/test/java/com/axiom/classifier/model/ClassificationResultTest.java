package com.axiom.classifier.model;

import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.RuleField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationResultTest {

    private static final Evidence SOME_EVIDENCE = new Evidence(
        RuleField.MESSAGE, Operator.CONTAINS, "boom", "boom happened", null);

    @Test
    void constructsWithValidFields() {
        ClassificationResult result = new ClassificationResult(
            FailureCategory.INFRASTRUCTURE_FAILURE, 0.95, "connection-refused",
            List.of(SOME_EVIDENCE));

        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, result.category());
        assertEquals(0.95, result.confidence());
        assertEquals("connection-refused", result.matchedRuleId());
        assertEquals(List.of(SOME_EVIDENCE), result.evidence());
    }

    @Test
    void matchedRuleIdMayBeNull() {
        ClassificationResult result = new ClassificationResult(
            FailureCategory.UNKNOWN, 0.0, null, List.of());

        assertNull(result.matchedRuleId());
    }

    @Test
    void evidenceMayBeEmpty() {
        ClassificationResult result = new ClassificationResult(
            FailureCategory.UNKNOWN, 0.0, null, List.of());

        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void acceptsBoundaryConfidenceValues() {
        assertEquals(0.0, new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of()).confidence());
        assertEquals(1.0, new ClassificationResult(FailureCategory.UNKNOWN, 1.0, null, List.of()).confidence());
    }

    @Test
    void throwsWhenCategoryIsNull() {
        assertThrows(NullPointerException.class, () ->
            new ClassificationResult(null, 0.5, null, List.of()));
    }

    @Test
    void throwsWhenConfidenceBelowZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassificationResult(FailureCategory.UNKNOWN, -0.01, null, List.of()));
    }

    @Test
    void throwsWhenConfidenceAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassificationResult(FailureCategory.UNKNOWN, 1.01, null, List.of()));
    }

    @Test
    void throwsWhenEvidenceIsNull() {
        assertThrows(NullPointerException.class, () ->
            new ClassificationResult(FailureCategory.UNKNOWN, 0.5, null, null));
    }

    @Test
    void evidenceIsDefensivelyCopiedAndImmutable() {
        List<Evidence> mutable = new ArrayList<>();
        mutable.add(SOME_EVIDENCE);

        ClassificationResult result = new ClassificationResult(
            FailureCategory.UNKNOWN, 0.5, "r1", mutable);
        mutable.add(SOME_EVIDENCE);

        assertEquals(1, result.evidence().size());
        assertThrows(UnsupportedOperationException.class, () -> result.evidence().add(SOME_EVIDENCE));
    }
}
