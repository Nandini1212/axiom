package com.axiom.analyzer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No test here makes a real call to Anthropic's API — {@link #client} points at an unreachable
 * local address specifically so the network-failure path can be exercised deterministically and
 * fast, without a real API key or network access. Verifying an actual successful call against
 * the live API is out of scope for this environment; see docs/05-ai-analyzer.md.
 */
class ClaudeProviderTest {

    private static final FailureEvent SOME_EVENT = new FailureEvent(
        "evt-1", "test", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
        "boom", null, null, null, null, null);

    private static final ClassificationResult SOME_CLASSIFICATION =
        new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of());

    private static AnthropicClient unreachableClient() {
        return AnthropicOkHttpClient.builder()
            .apiKey("test-key-not-real")
            .baseUrl("http://127.0.0.1:1") // reserved port, connection refused immediately
            .timeout(Duration.ofSeconds(2))
            .maxRetries(0)
            .build();
    }

    @Test
    void networkFailureIsWrappedAsLlmExplanationException() {
        ClaudeProvider provider = new ClaudeProvider(unreachableClient());

        LlmExplanationException ex = assertThrows(LlmExplanationException.class,
            () -> provider.explain(SOME_EVENT, SOME_CLASSIFICATION));

        assertNotNull(ex.getCause());
        assertTrue(ex.getMessage().contains("Claude API call failed"));
    }

    @Test
    void throwsWhenClientIsNull() {
        assertThrows(NullPointerException.class, () -> new ClaudeProvider(null));
    }
}
