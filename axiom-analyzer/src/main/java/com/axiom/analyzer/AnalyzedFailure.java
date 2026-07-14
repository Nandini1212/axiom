package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.util.Objects;

/**
 * One failure paired with what the deterministic classifier decided about it — including the
 * {@code UNKNOWN}/{@code 0.0} case when no rule matched, which is still a real result, not
 * something to drop.
 */
public record AnalyzedFailure(FailureEvent event, ClassificationResult classification) {

    public AnalyzedFailure {
        Objects.requireNonNull(event, "event is mandatory");
        Objects.requireNonNull(classification, "classification is mandatory");
    }
}
