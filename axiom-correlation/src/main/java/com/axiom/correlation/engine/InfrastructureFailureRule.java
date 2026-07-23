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
 * The second root-cause rule. Reuses {@code RETRY_PASSED} — the same signal
 * {@code ApplicationBugCorrelationRule} treats as contradicting is supporting here; signals are
 * facts, interpretation is a rule's own concern.
 * <p>
 * Deliberately conservative: {@link #evaluate} refuses to produce a hypothesis at all
 * ({@code Optional.empty()}) unless the existing deterministic classification already agrees, or
 * a failure cluster is present — a single passing retry alone must never be enough to suggest
 * infrastructure on its own (that evidence pattern is closer to a future flaky-test rule's
 * territory). Never marks a contradiction as blocking — the negative weights are trusted to lower
 * confidence below the selection threshold on their own; see
 * {@code AssessmentSelector.CONFIDENCE_THRESHOLD} and {@code MINIMUM_HYPOTHESIS_LEAD}.
 */
public final class InfrastructureFailureRule implements CorrelationRule {

    public static final String RULE_ID = "infrastructure-failure-v1";

    static final double WEIGHT_ALREADY_INFRASTRUCTURE_FAILURE = 0.40;
    static final double WEIGHT_FAILURE_CLUSTER_PRESENT = 0.25;
    static final double WEIGHT_RETRY_PASSED = 0.20;
    static final double WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE = -0.35;
    static final double WEIGHT_ALREADY_APPLICATION_BUG = -0.25;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleEvaluation> evaluate(CorrelationContext context) {
        Map<SignalType, Signal> signals = index(context.signals());
        Optional<TestFailureEvidence> testFailure = findTestFailure(context.evidence());

        boolean classifiedAsInfrastructure = testFailure.isPresent()
            && testFailure.get().classification().category() == FailureCategory.INFRASTRUCTURE_FAILURE;
        boolean failureClusterPresent = isPresent(signals, SignalType.FAILURE_CLUSTER_PRESENT);

        // A single passing retry, on its own, must never be enough to suggest infrastructure —
        // that pattern belongs to a future flaky-test rule, not this one.
        if (!classifiedAsInfrastructure && !failureClusterPresent) {
            return Optional.empty();
        }

        List<ConfidenceContribution> contributions = new ArrayList<>();
        List<EvidenceReference> supporting = new ArrayList<>();
        List<EvidenceReference> contradicting = new ArrayList<>();

        if (classifiedAsInfrastructure) {
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_ALREADY_INFRASTRUCTURE_FAILURE,
                "Existing deterministic classification is already INFRASTRUCTURE_FAILURE",
                List.of(testFailure.get().evidenceId())));
            supporting.add(new EvidenceReference(testFailure.get().evidenceId(),
                "Existing deterministic classification is already INFRASTRUCTURE_FAILURE"));
        }

        Signal cluster = signals.get(SignalType.FAILURE_CLUSTER_PRESENT);
        if (cluster != null && cluster.present()) {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_FAILURE_CLUSTER_PRESENT, "Failure cluster present", cluster.evidenceIds()));
            supporting.add(new EvidenceReference(firstOrEmpty(cluster.evidenceIds()), "Failure cluster present"));
        }

        Signal retryPassed = signals.get(SignalType.RETRY_PASSED);
        if (retryPassed != null && retryPassed.present()) {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_RETRY_PASSED, "Retry passed", retryPassed.evidenceIds()));
            supporting.add(new EvidenceReference(firstOrEmpty(retryPassed.evidenceIds()), "Retry passed"));
        }

        Signal stackFrameMatches = signals.get(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE);
        if (stackFrameMatches != null && stackFrameMatches.present()) {
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE,
                "Changed production file matches stack frame", stackFrameMatches.evidenceIds()));
            contradicting.add(new EvidenceReference(
                firstOrEmpty(stackFrameMatches.evidenceIds()), "Changed production file matches stack frame"));
        }

        if (testFailure.isPresent()
                && testFailure.get().classification().category() == FailureCategory.APPLICATION_BUG) {
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_ALREADY_APPLICATION_BUG,
                "Existing deterministic classification is already APPLICATION_BUG",
                List.of(testFailure.get().evidenceId())));
            contradicting.add(new EvidenceReference(testFailure.get().evidenceId(),
                "Existing deterministic classification is already APPLICATION_BUG"));
        }

        return Optional.of(new RuleEvaluation(
            FailureCategory.INFRASTRUCTURE_FAILURE, contributions, supporting, contradicting, false));
    }

    private static boolean isPresent(Map<SignalType, Signal> signals, SignalType type) {
        Signal signal = signals.get(type);
        return signal != null && signal.present();
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
