package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FakeLLMProviderTest {

    private static final FailureEvent SOME_EVENT = new FailureEvent(
        "evt-1", "test", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
        null, null, null, null, null, null);

    private static final ClassificationResult SOME_CLASSIFICATION =
        new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of());

    @Test
    void fixedExplanationIsReturnedAsIs() {
        AiExplanation explanation = new AiExplanation("summary", "root cause", List.of(), "confidence");
        FakeLLMProvider provider = new FakeLLMProvider(explanation);

        assertEquals(explanation, provider.explain(SOME_EVENT, SOME_CLASSIFICATION));
    }

    @Test
    void alwaysThrowsThrowsLlmExplanationException() {
        FakeLLMProvider provider = FakeLLMProvider.alwaysThrows();

        assertThrows(LlmExplanationException.class,
            () -> provider.explain(SOME_EVENT, SOME_CLASSIFICATION));
    }

    @Test
    void alwaysTimeoutSleepsLongerThanATypicalTestTimeout() {
        FakeLLMProvider provider = FakeLLMProvider.alwaysTimeout();

        long start = System.currentTimeMillis();
        Thread thread = new Thread(() -> provider.explain(SOME_EVENT, SOME_CLASSIFICATION));
        thread.setDaemon(true);
        thread.start();

        // Confirm it hasn't returned almost instantly (i.e. it's actually simulating a slow call).
        assertDoesNotThrow(() -> Thread.sleep(50));
        assertTrue(thread.isAlive());
        assertTrue(System.currentTimeMillis() - start < FakeLLMProvider.TIMEOUT_SLEEP_MILLIS);
    }
}
