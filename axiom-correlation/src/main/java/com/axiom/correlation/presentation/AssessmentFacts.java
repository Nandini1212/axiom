package com.axiom.correlation.presentation;

import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.ConfidenceContribution;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.RootCauseHypothesis;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.TestFailureEvidence;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Derived facts shared by every {@link AssessmentRenderer} implementation, computed once from
 * (assessment, evidence) so each renderer only handles its own markup/formatting — not
 * re-deriving the same reasoning (e.g. the abstention recommendation's rule-reason matching)
 * independently and risking two renderers silently disagreeing on the same input.
 * <p>
 * Package-private: this is wiring between {@code TextAssessmentRenderer} and
 * {@code MarkdownAssessmentRenderer}, not part of the public {@code AssessmentRenderer} contract.
 */
record AssessmentFacts(
        String testName,
        AssessmentDisposition disposition,
        String friendlyDisposition,
        Optional<RootCauseHypothesis> topHypothesis,
        Optional<String> friendlyCategory,
        Optional<ConfidenceLevel> confidenceLevel,
        Optional<Integer> confidencePercentage,
        List<String> topSupportingReasons,
        Optional<String> strongestContradiction,
        List<String> missingEvidence,
        String recommendedAction,
        Optional<String> changedFile) {

    static AssessmentFacts derive(RootCauseAssessment assessment, List<CorrelationEvidence> evidence) {
        Optional<RootCauseHypothesis> top = assessment.rankedHypotheses().stream().findFirst();

        String recommendedAction = assessment.disposition() == AssessmentDisposition.DETERMINED
            ? recommendedActionForDetermined(evidence)
            : recommendedActionForAbstention(assessment);

        return new AssessmentFacts(
            testName(evidence),
            assessment.disposition(),
            friendlyDisposition(assessment.disposition()),
            top,
            top.map(h -> friendlyCategory(h.category())),
            top.map(h -> ConfidenceLevel.forConfidence(h.confidence())),
            top.map(h -> (int) Math.round(h.confidence() * 100)),
            top.map(h -> topSupportingReasons(h, 3)).orElse(List.of()),
            top.flatMap(AssessmentFacts::strongestContradiction),
            assessment.missingEvidence(),
            recommendedAction,
            changedFile(evidence));
    }

    private static String testName(List<CorrelationEvidence> evidence) {
        return evidence.stream()
            .filter(e -> e.type() == EvidenceType.TEST_FAILURE)
            .map(TestFailureEvidence.class::cast)
            .findFirst()
            .map(AssessmentFacts::describeTest)
            .orElse("(unknown test)");
    }

    private static String describeTest(TestFailureEvidence testFailure) {
        String testName = testFailure.failureEvent().testName();
        String simpleClassName = simpleName(testFailure.failureEvent().className());
        if (testName != null && simpleClassName != null) {
            return simpleClassName + "." + testName;
        }
        return testName != null ? testName : (simpleClassName != null ? simpleClassName : "(unknown test)");
    }

    private static String simpleName(String fullyQualifiedClassName) {
        if (fullyQualifiedClassName == null) {
            return null;
        }
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedClassName.substring(lastDot + 1) : fullyQualifiedClassName;
    }

    private static List<String> topSupportingReasons(RootCauseHypothesis hypothesis, int max) {
        return hypothesis.contributions().stream()
            .filter(c -> c.weight() > 0)
            .sorted(Comparator.comparingDouble(ConfidenceContribution::weight).reversed())
            .limit(max)
            .map(ConfidenceContribution::reason)
            .toList();
    }

    private static Optional<String> strongestContradiction(RootCauseHypothesis hypothesis) {
        return hypothesis.contributions().stream()
            .filter(c -> c.weight() < 0)
            .min(Comparator.comparingDouble(ConfidenceContribution::weight))
            .map(ConfidenceContribution::reason);
    }

    private static Optional<String> changedFile(List<CorrelationEvidence> evidence) {
        return evidence.stream()
            .filter(e -> e.type() == EvidenceType.SOURCE_CHANGE)
            .map(SourceChangeEvidence.class::cast)
            .flatMap(e -> e.changedFiles().stream())
            .findFirst();
    }

    private static String recommendedActionForDetermined(List<CorrelationEvidence> evidence) {
        return changedFile(evidence)
            .map(file -> "Review the recent changes in " + file + ".")
            .orElse("Review the recent code changes associated with this failure.");
    }

    /**
     * Matches on known reason strings (e.g. "Retry passed") rather than a structured
     * signal-reason type. A pragmatic v0.1 shortcut, acceptable with one rule — revisit with
     * something less string-matched once a second rule's abstention reasons need the same
     * treatment (not designed speculatively now, off a single example).
     */
    private static String recommendedActionForAbstention(RootCauseAssessment assessment) {
        if (assessment.missingEvidence().contains("source-change evidence not supplied")) {
            return "Supply source-change information (a changes.json diff) and re-run the investigation.";
        }
        if (assessment.missingEvidence().contains("execution evidence not supplied")) {
            return "Supply execution information (an execution.json retry result) and re-run the investigation.";
        }
        if (!assessment.rankedHypotheses().isEmpty()) {
            Optional<String> contradiction = strongestContradiction(assessment.rankedHypotheses().get(0));
            if (contradiction.isPresent() && contradiction.get().equals("Retry passed")) {
                return "Run the test again and check whether it fails consistently before treating this as an application bug.";
            }
            if (contradiction.isPresent() && contradiction.get().equals("Top stack frame belongs to test code")) {
                return "Review the test code itself - the failure may originate in the test, not the production code.";
            }
        }
        return "Review the evidence manually - Axiom did not find a strong enough signal to recommend a specific next step.";
    }

    private static String friendlyCategory(FailureCategory category) {
        return switch (category) {
            case APPLICATION_BUG -> "Application bug";
            case TEST_AUTOMATION_BUG -> "Test automation issue";
            case INFRASTRUCTURE_FAILURE -> "Infrastructure issue";
            case DEPLOYMENT_FAILURE -> "Deployment issue";
            case ENVIRONMENT_FAILURE -> "Environment issue";
            case CONFIGURATION_FAILURE -> "Configuration issue";
            case DATA_ISSUE -> "Data issue";
            case DEPENDENCY_FAILURE -> "Dependency issue";
            case FLAKY_TEST -> "Possibly flaky test";
            case UNKNOWN -> "Unknown";
        };
    }

    private static String friendlyDisposition(AssessmentDisposition disposition) {
        return switch (disposition) {
            case DETERMINED -> "Root cause determined";
            case NEEDS_INVESTIGATION -> "More investigation needed";
        };
    }
}
