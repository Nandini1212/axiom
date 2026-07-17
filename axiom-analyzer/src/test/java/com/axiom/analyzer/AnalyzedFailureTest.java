package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzedFailureTest {

    private static final FailureEvent SOME_EVENT = new FailureEvent(
        "evt-1", "test", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
        null, null, null, null, null, null);

    private static final ClassificationResult SOME_CLASSIFICATION =
        new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of());

    private static final AiExplanation SOME_EXPLANATION =
        new AiExplanation("summary", "root cause", List.of("step 1"), "confidence explanation");

    @Test
    void twoArgConstructorLeavesExplanationEmpty() {
        AnalyzedFailure analyzed = new AnalyzedFailure(SOME_EVENT, SOME_CLASSIFICATION);

        assertEquals(SOME_EVENT, analyzed.event());
        assertEquals(SOME_CLASSIFICATION, analyzed.classification());
        assertTrue(analyzed.explanation().isEmpty());
    }

    @Test
    void threeArgConstructorCanCarryAnExplanation() {
        AnalyzedFailure analyzed =
            new AnalyzedFailure(SOME_EVENT, SOME_CLASSIFICATION, Optional.of(SOME_EXPLANATION));

        assertEquals(Optional.of(SOME_EXPLANATION), analyzed.explanation());
    }

    @Test
    void throwsWhenEventIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalyzedFailure(null, SOME_CLASSIFICATION));
    }

    @Test
    void throwsWhenClassificationIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalyzedFailure(SOME_EVENT, null));
    }

    @Test
    void throwsWhenExplanationIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AnalyzedFailure(SOME_EVENT, SOME_CLASSIFICATION, null));
    }
}
