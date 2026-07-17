package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps a {@link DeterministicAnalyzer} (composition, not re-orchestration) and attempts to
 * attach an {@link AiExplanation} to each failure via an {@link LLMProvider}. The deterministic
 * result is computed once, fully, before any LLM call happens — nothing an LLM call does can
 * change the classification. Each failure's explanation attempt is isolated: one timing out or
 * erroring only affects that failure's {@code explanation} (left {@code Optional.empty()}) plus
 * an {@link AnalyzerWarning}, never the rest of the report.
 * <p>
 * Deliberately does not build a prompt itself — {@link PromptBuilder} is consumed internally by
 * {@link LLMProvider} implementations (e.g. a future Claude-backed one), since prompt
 * construction is exactly the kind of provider-specific request mechanics this class isn't
 * supposed to know about.
 */
public final class AIEnhancedAnalyzer implements Analyzer {

    private final DeterministicAnalyzer deterministic;
    private final LLMProvider provider;
    private final Duration timeout;

    public AIEnhancedAnalyzer(DeterministicAnalyzer deterministic, LLMProvider provider, Duration timeout) {
        this.deterministic = Objects.requireNonNull(deterministic, "deterministic is mandatory");
        this.provider = Objects.requireNonNull(provider, "provider is mandatory");
        this.timeout = Objects.requireNonNull(timeout, "timeout is mandatory");
    }

    @Override
    public AnalysisResult analyze(InputStream report) {
        AnalysisResult base = deterministic.analyze(report);

        List<AnalyzedFailure> enhanced = new ArrayList<>();
        List<AnalyzerWarning> analyzerWarnings = new ArrayList<>();

        for (AnalyzedFailure failure : base.analyses()) {
            enhance(failure, enhanced, analyzerWarnings);
        }

        return new AnalysisResult(enhanced, base.parserWarnings(), analyzerWarnings);
    }

    private void enhance(AnalyzedFailure failure, List<AnalyzedFailure> enhanced, List<AnalyzerWarning> warnings) {
        try {
            AiExplanation explanation = explainWithTimeout(failure.event(), failure.classification());
            enhanced.add(new AnalyzedFailure(failure.event(), failure.classification(), Optional.of(explanation)));
        } catch (TimeoutException e) {
            enhanced.add(failure);
            warnings.add(new AnalyzerWarning(
                AnalyzerWarningType.AI_TIMEOUT, failure.event().id(),
                "LLM call timed out after " + timeout));
        } catch (LlmExplanationException e) {
            enhanced.add(failure);
            warnings.add(new AnalyzerWarning(
                AnalyzerWarningType.AI_EXPLANATION_FAILED, failure.event().id(), e.getMessage()));
        }
    }

    private AiExplanation explainWithTimeout(FailureEvent event, ClassificationResult classification)
        throws TimeoutException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AiExplanation> future = executor.submit(() -> provider.explain(event, classification));
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof LlmExplanationException llmException) {
                throw llmException;
            }
            throw new LlmExplanationException("Unexpected error calling LLM provider", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmExplanationException("Interrupted while calling LLM provider", e);
        } finally {
            executor.shutdownNow();
        }
    }
}
