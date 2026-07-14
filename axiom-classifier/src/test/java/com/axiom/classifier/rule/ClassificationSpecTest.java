package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationSpecTest {

    @Test
    void constructsWithValidConfidence() {
        ClassificationSpec spec = new ClassificationSpec(FailureCategory.FLAKY_TEST, 0.6);

        assertEquals(FailureCategory.FLAKY_TEST, spec.category());
        assertEquals(0.6, spec.confidence());
    }

    @Test
    void acceptsZeroAsBoundaryConfidence() {
        ClassificationSpec spec = new ClassificationSpec(FailureCategory.UNKNOWN, 0.0);

        assertEquals(0.0, spec.confidence());
    }

    @Test
    void acceptsOneAsBoundaryConfidence() {
        ClassificationSpec spec = new ClassificationSpec(FailureCategory.UNKNOWN, 1.0);

        assertEquals(1.0, spec.confidence());
    }

    @Test
    void throwsWhenCategoryIsNull() {
        assertThrows(NullPointerException.class, () -> new ClassificationSpec(null, 0.5));
    }

    @Test
    void throwsWhenConfidenceBelowZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassificationSpec(FailureCategory.UNKNOWN, -0.01));
    }

    @Test
    void throwsWhenConfidenceAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClassificationSpec(FailureCategory.UNKNOWN, 1.01));
    }
}
