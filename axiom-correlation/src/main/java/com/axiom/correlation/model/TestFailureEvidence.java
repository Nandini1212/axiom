package com.axiom.correlation.model;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.common.model.FailureEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * Wraps the pipeline's existing, already-validated {@link FailureEvent}/{@link ClassificationResult}
 * pair rather than re-deriving testName/message/stackTrace as new fields — avoids two copies of
 * the same failure data drifting apart.
 */
public record TestFailureEvidence(
        String evidenceId,
        Instant observedAt,
        FailureEvent failureEvent,
        ClassificationResult classification
) implements CorrelationEvidence {

    public TestFailureEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is mandatory");
        Objects.requireNonNull(observedAt, "observedAt is mandatory");
        Objects.requireNonNull(failureEvent, "failureEvent is mandatory");
        Objects.requireNonNull(classification, "classification is mandatory");
    }

    @Override
    public EvidenceType type() {
        return EvidenceType.TEST_FAILURE;
    }

    /**
     * The only supported construction path — building from an already-paired
     * {@link FailureAnalysisInput} makes a mismatched event/classification pair structurally
     * impossible, rather than something a runtime check has to catch (there is no shared
     * identifier between the two types to validate against).
     */
    public static TestFailureEvidence from(String evidenceId, Instant observedAt, FailureAnalysisInput input) {
        Objects.requireNonNull(input, "input is mandatory");
        return new TestFailureEvidence(evidenceId, observedAt, input.failure(), input.classification());
    }
}
