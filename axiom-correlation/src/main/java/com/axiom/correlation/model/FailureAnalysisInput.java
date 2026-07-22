package com.axiom.correlation.model;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.util.Objects;

/**
 * A {@link FailureEvent} paired with the deterministic classifier's {@link ClassificationResult}
 * for it — the correlation engine's own small input model, deliberately not {@code AnalyzedFailure}
 * ({@code axiom-analyzer}). Keeps this module independent of the AI/orchestration layer; a caller
 * that already has an {@code AnalyzedFailure} (e.g. a future CLI wiring) converts it into this type
 * one layer up, outside this module.
 */
public record FailureAnalysisInput(FailureEvent failure, ClassificationResult classification) {

    public FailureAnalysisInput {
        Objects.requireNonNull(failure, "failure is mandatory");
        Objects.requireNonNull(classification, "classification is mandatory");
    }
}
