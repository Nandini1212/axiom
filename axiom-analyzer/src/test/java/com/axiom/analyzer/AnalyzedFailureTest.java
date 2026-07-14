package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzedFailureTest {

    private static final FailureEvent SOME_EVENT = new FailureEvent(
        "evt-1", "test", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
        null, null, null, null, null, null);

    private static final ClassificationResult SOME_CLASSIFICATION =
        new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of());

    @Test
    void constructsWithValidFields() {
        AnalyzedFailure analyzed = new AnalyzedFailure(SOME_EVENT, SOME_CLASSIFICATION);

        assertEquals(SOME_EVENT, analyzed.event());
        assertEquals(SOME_CLASSIFICATION, analyzed.classification());
    }

    @Test
    void throwsWhenEventIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalyzedFailure(null, SOME_CLASSIFICATION));
    }

    @Test
    void throwsWhenClassificationIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalyzedFailure(SOME_EVENT, null));
    }
}
