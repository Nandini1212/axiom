package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * One failure paired with what the deterministic classifier decided about it — including the
 * {@code UNKNOWN}/{@code 0.0} case when no rule matched, which is still a real result, not
 * something to drop.
 * <p>
 * {@code explanation} is {@code Optional}, not nullable: a bare {@code null} would have to mean
 * several different things at once (AI wasn't attempted; AI timed out; AI errored), each of which
 * is actually a distinct state — an {@link AnalyzerWarning} on {@link AnalysisResult} carries the
 * "why," while {@code Optional.empty()} here just means "no explanation is attached," full stop.
 */
public record AnalyzedFailure(FailureEvent event, ClassificationResult classification, Optional<AiExplanation> explanation) {

    public AnalyzedFailure {
        Objects.requireNonNull(event, "event is mandatory");
        Objects.requireNonNull(classification, "classification is mandatory");
        Objects.requireNonNull(explanation, "explanation is mandatory (use Optional.empty(), not null)");
    }

    /** Deterministic-only construction: no AI explanation attempted. */
    public AnalyzedFailure(FailureEvent event, ClassificationResult classification) {
        this(event, classification, Optional.empty());
    }
}
