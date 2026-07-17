package com.axiom.analyzer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.util.Objects;

/**
 * The one concrete {@link LLMProvider} implementation. Uses structured outputs so Claude's
 * response deserializes directly into {@link AiExplanation} — no manual JSON parsing, and
 * {@link AiExplanation}'s own validation (non-blank summary/rootCause/confidenceExplanation)
 * runs automatically on the response.
 * <p>
 * Model id is passed as a plain string ({@code "claude-opus-4-8"}), not the SDK's {@code Model}
 * enum constant — the pinned SDK version (2.34.0) predates that model, but the API itself
 * accepts the string regardless (Stainless-generated SDKs are forward-compatible this way).
 */
public final class ClaudeProvider implements LLMProvider {

    private static final String MODEL = "claude-opus-4-8";
    private static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private final PromptBuilder promptBuilder;

    public ClaudeProvider(AnthropicClient client) {
        this.client = Objects.requireNonNull(client, "client is mandatory");
        this.promptBuilder = new PromptBuilder();
    }

    /** Reads ANTHROPIC_API_KEY (or an active `ant auth login` profile) from the environment. */
    public static ClaudeProvider fromEnv() {
        return new ClaudeProvider(AnthropicOkHttpClient.fromEnv());
    }

    @Override
    public AiExplanation explain(FailureEvent event, ClassificationResult classification) {
        String prompt = promptBuilder.build(event, classification);

        StructuredMessageCreateParams<AiExplanation> params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .outputConfig(AiExplanation.class)
            .addUserMessage(prompt)
            .build();

        StructuredMessage<AiExplanation> response;
        try {
            response = client.messages().create(params);
        } catch (AnthropicException e) {
            // Covers both AnthropicServiceException (HTTP-level: 4xx/5xx) and
            // AnthropicIoException (network failure before any response) — this call site
            // doesn't differentiate retry policy, so the common base is the right catch.
            throw new LlmExplanationException("Claude API call failed: " + e.getMessage(), e);
        }

        return response.content().stream()
            .flatMap(block -> block.text().stream())
            .findFirst()
            .map(structuredTextBlock -> structuredTextBlock.text())
            .orElseThrow(() -> new LlmExplanationException(
                "Claude response contained no structured output (stop_reason: "
                    + response.stopReason() + ")"));
    }
}
