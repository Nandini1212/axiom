package com.axiom.correlation.engine;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.ConfidenceContribution;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceReference;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.correlation.signal.Signal;
import com.axiom.correlation.signal.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one root-cause rule in this v0.1 slice. Never calls an LLM, never touches
 * {@code FailureCategory} itself (no new constant added — see {@code AssessmentDisposition}), and
 * always returns the real {@link FailureCategory#APPLICATION_BUG} value for its hypothesis, never
 * an abstention marker.
 */
public final class ApplicationBugCorrelationRule implements CorrelationRule {

    public static final String RULE_ID = "application-bug-v1";

    // Named constants, not inline literals, specifically so these are easy to find and adjust
    // from benchmark results later without hunting through evaluate()'s body for magic numbers.
    static final double WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE = 0.40;
    static final double WEIGHT_RETRY_FAILED = 0.25;
    static final double WEIGHT_ALREADY_APPLICATION_BUG = 0.15;
    static final double WEIGHT_TOP_FRAME_IS_TEST_CODE = -0.30;
    static final double WEIGHT_RETRY_PASSED = -0.35;
    static final double WEIGHT_CHANGE_SET_EVIDENCE_MISSING = -0.15;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleEvaluation> evaluate(CorrelationContext context) {
        Map<SignalType, Signal> signals = index(context.signals());
        List<ConfidenceContribution> contributions = new ArrayList<>();
        List<EvidenceReference> supporting = new ArrayList<>();
        List<EvidenceReference> contradicting = new ArrayList<>();
        boolean blocking = false;

        Signal stackFrameMatches = signals.get(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE);
        if (stackFrameMatches != null && stackFrameMatches.present()) {
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE,
                "Changed production file matches stack frame", stackFrameMatches.evidenceIds()));
            supporting.add(new EvidenceReference(
                firstOrEmpty(stackFrameMatches.evidenceIds()), "Changed production file matches stack frame"));
        }

        Signal retryFailed = signals.get(SignalType.RETRY_FAILED);
        if (retryFailed != null && retryFailed.present()) {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_RETRY_FAILED, "Failure reproduced on retry", retryFailed.evidenceIds()));
            supporting.add(new EvidenceReference(
                firstOrEmpty(retryFailed.evidenceIds()), "Failure reproduced on retry"));
        }

        Optional<TestFailureEvidence> testFailure =
            findTestFailure(context.evidence());
        if (testFailure.isPresent()
                && testFailure.get().classification().category() == FailureCategory.APPLICATION_BUG) {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_ALREADY_APPLICATION_BUG,
                "Existing deterministic classification is already APPLICATION_BUG",
                List.of(testFailure.get().evidenceId())));
            supporting.add(new EvidenceReference(
                testFailure.get().evidenceId(), "Existing deterministic classification is already APPLICATION_BUG"));
        }

        Signal topFrameIsTestCode = signals.get(SignalType.TOP_FRAME_IS_TEST_CODE);
        if (topFrameIsTestCode != null && topFrameIsTestCode.present()) {
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_TOP_FRAME_IS_TEST_CODE,
                "Top stack frame belongs to test code", topFrameIsTestCode.evidenceIds()));
            contradicting.add(new EvidenceReference(
                firstOrEmpty(topFrameIsTestCode.evidenceIds()), "Top stack frame belongs to test code"));
            blocking = true;
        }

        Signal retryPassed = signals.get(SignalType.RETRY_PASSED);
        if (retryPassed != null && retryPassed.present()) {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_RETRY_PASSED, "Retry passed", retryPassed.evidenceIds()));
            contradicting.add(new EvidenceReference(firstOrEmpty(retryPassed.evidenceIds()), "Retry passed"));
            blocking = true;
        }

        Signal changeSetMissing = signals.get(SignalType.CHANGE_SET_EVIDENCE_MISSING);
        if (changeSetMissing != null && changeSetMissing.present()) {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_CHANGE_SET_EVIDENCE_MISSING, "No change-set evidence supplied", List.of()));
            contradicting.add(new EvidenceReference("", "No change-set evidence supplied"));
        }

        if (contributions.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RuleEvaluation(
            FailureCategory.APPLICATION_BUG, contributions, supporting, contradicting, blocking));
    }

    private static Optional<TestFailureEvidence> findTestFailure(List<CorrelationEvidence> evidence) {
        return evidence.stream()
            .filter(e -> e.type() == EvidenceType.TEST_FAILURE)
            .map(TestFailureEvidence.class::cast)
            .findFirst();
    }

    private static Map<SignalType, Signal> index(List<Signal> signals) {
        return signals.stream().collect(java.util.stream.Collectors.toMap(
            Signal::type, s -> s, (first, second) -> first));
    }

    private static String firstOrEmpty(List<String> evidenceIds) {
        return evidenceIds.isEmpty() ? "" : evidenceIds.get(0);
    }
}
