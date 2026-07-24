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
 * The third root-cause rule — renamed from {@code FlakyTestRule} once a second, genuinely
 * historical rule ({@code HistoricalFlakyTestRule}) existed to distinguish itself from. This rule
 * identifies a <b>single-run transient failure</b> from this execution's own retry evidence alone
 * — not historical flakiness, which needs a track record across runs this rule has no access to.
 * {@code FailureCategory.FLAKY_TEST} is still reused for taxonomy compatibility with the
 * deterministic classifier (both this rule and {@code HistoricalFlakyTestRule} conclude the same
 * category, from different evidence — see {@code AssessmentSelector}'s same-category
 * aggregation), but the presentation layer phrases the result as "this failure appears
 * transient," never "this test is flaky" — see {@code AssessmentFacts}.
 * <p>
 * Reasons largely by elimination — not code (no stack match), not infrastructure (no cluster),
 * and it un-failed on retry — which is epistemically weaker than the other rules' direct positive
 * evidence (a stack match, a failure cluster). That's reflected in the confidence ceiling (0.85,
 * same numeric ceiling as the others, but reached only by stacking four modest contributions
 * rather than one or two strong ones) and in treating an actual code correlation as a hard veto: a
 * real, intermittent application bug can still pass on retry, so a changed production file
 * matching the stack frame must not be waved away as "probably transient."
 */
public final class TransientFailureRule implements CorrelationRule {

    public static final String RULE_ID = "transient-failure-v1";

    static final double WEIGHT_RETRY_PASSED = 0.45;
    static final double WEIGHT_ALREADY_FLAKY_TEST = 0.20;
    static final double WEIGHT_NO_STACK_FRAME_MATCH = 0.10;
    static final double WEIGHT_NO_FAILURE_CLUSTER = 0.10;
    static final double WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE = -0.40;
    static final double WEIGHT_FAILURE_CLUSTER_PRESENT = -0.30;
    static final double WEIGHT_ALREADY_APPLICATION_BUG = -0.25;
    static final double WEIGHT_ALREADY_INFRASTRUCTURE_FAILURE = -0.20;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Optional<RuleEvaluation> evaluate(CorrelationContext context) {
        Map<SignalType, Signal> signals = index(context.signals());
        Signal retryPassed = signals.get(SignalType.RETRY_PASSED);

        // Minimum direct evidence: you cannot call a failure transient without having seen it
        // pass. No retry-passed signal at all means this rule has nothing to say.
        if (retryPassed == null || !retryPassed.present()) {
            return Optional.empty();
        }

        List<ConfidenceContribution> contributions = new ArrayList<>();
        List<EvidenceReference> supporting = new ArrayList<>();
        List<EvidenceReference> contradicting = new ArrayList<>();
        boolean blocking = false;

        contributions.add(new ConfidenceContribution(
            RULE_ID, WEIGHT_RETRY_PASSED, "Retry passed", retryPassed.evidenceIds()));
        supporting.add(new EvidenceReference(firstOrEmpty(retryPassed.evidenceIds()), "Retry passed"));

        Optional<TestFailureEvidence> testFailure = findTestFailure(context.evidence());

        if (testFailure.isPresent() && testFailure.get().classification().category() == FailureCategory.FLAKY_TEST) {
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_ALREADY_FLAKY_TEST,
                "Existing deterministic classification is already FLAKY_TEST",
                List.of(testFailure.get().evidenceId())));
            supporting.add(new EvidenceReference(testFailure.get().evidenceId(),
                "Existing deterministic classification is already FLAKY_TEST"));
        }

        Signal stackMatch = signals.get(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE);
        if (stackMatch != null && stackMatch.present()) {
            // A real, intermittent application bug can still pass on retry — a changed production
            // file matching the stack frame must prevent a single-run flaky determination, not
            // just lower it. Hard veto, not a modest deduction.
            contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_STACK_FRAME_MATCHES_CHANGED_FILE,
                "Changed production file matches stack frame", stackMatch.evidenceIds()));
            contradicting.add(new EvidenceReference(
                firstOrEmpty(stackMatch.evidenceIds()), "Changed production file matches stack frame"));
            blocking = true;
        } else {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_NO_STACK_FRAME_MATCH, "No stack frame correlates with a changed file", List.of()));
            supporting.add(new EvidenceReference("", "No stack frame correlates with a changed file"));
        }

        Signal cluster = signals.get(SignalType.FAILURE_CLUSTER_PRESENT);
        if (cluster != null && cluster.present()) {
            // Isolated flaky tests and infrastructure incidents can coexist, so this is
            // substantial but non-blocking — unlike the stack-match case above.
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_FAILURE_CLUSTER_PRESENT, "Failure cluster present", cluster.evidenceIds()));
            contradicting.add(new EvidenceReference(firstOrEmpty(cluster.evidenceIds()), "Failure cluster present"));
        } else {
            contributions.add(new ConfidenceContribution(
                RULE_ID, WEIGHT_NO_FAILURE_CLUSTER, "No failure cluster present", List.of()));
            supporting.add(new EvidenceReference("", "No failure cluster present"));
        }

        if (testFailure.isPresent()) {
            FailureCategory existingCategory = testFailure.get().classification().category();
            if (existingCategory == FailureCategory.APPLICATION_BUG) {
                contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_ALREADY_APPLICATION_BUG,
                    "Existing deterministic classification is already APPLICATION_BUG",
                    List.of(testFailure.get().evidenceId())));
                contradicting.add(new EvidenceReference(testFailure.get().evidenceId(),
                    "Existing deterministic classification is already APPLICATION_BUG"));
            } else if (existingCategory == FailureCategory.INFRASTRUCTURE_FAILURE) {
                contributions.add(new ConfidenceContribution(RULE_ID, WEIGHT_ALREADY_INFRASTRUCTURE_FAILURE,
                    "Existing deterministic classification is already INFRASTRUCTURE_FAILURE",
                    List.of(testFailure.get().evidenceId())));
                contradicting.add(new EvidenceReference(testFailure.get().evidenceId(),
                    "Existing deterministic classification is already INFRASTRUCTURE_FAILURE"));
            }
        }

        return Optional.of(new RuleEvaluation(
            FailureCategory.FLAKY_TEST, contributions, supporting, contradicting, blocking));
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
