package com.axiom.correlation.presentation;

import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.ConfidenceContribution;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceReference;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.model.RootCauseHypothesis;

import java.util.List;
import java.util.Locale;

/**
 * Plain-text {@link AssessmentRenderer} — no Unicode symbols, matching {@code axiom-cli}'s
 * existing plain-ASCII console output convention (avoids encoding surprises in CI log capture).
 * Shares its derived reasoning (recommended action, top reasons, etc.) with
 * {@link MarkdownAssessmentRenderer} via {@link AssessmentFacts}; only the surrounding
 * markup/formatting differs here.
 */
public final class TextAssessmentRenderer implements AssessmentRenderer {

    @Override
    public String renderSummary(RootCauseAssessment assessment, List<CorrelationEvidence> evidence) {
        AssessmentFacts facts = AssessmentFacts.derive(assessment, evidence);
        StringBuilder out = new StringBuilder();
        out.append(facts.testName()).append("\n\n");

        if (facts.disposition() == AssessmentDisposition.DETERMINED) {
            out.append("Likely cause: ").append(facts.friendlyCategory().orElseThrow()).append('\n');
            out.append("Confidence: ").append(facts.confidenceLevel().orElseThrow().displayName())
                .append(" - ").append(facts.confidencePercentage().orElseThrow()).append("%\n\n");

            out.append("Why Axiom thinks this:\n");
            for (String reason : facts.topSupportingReasons()) {
                out.append("- ").append(reason).append('\n');
            }
            out.append('\n');

            out.append("Evidence against: ").append(facts.strongestContradiction().orElse("none")).append("\n\n");

            out.append("Recommended next step:\n");
            out.append(facts.recommendedAction()).append("\n\n");
        } else {
            out.append("Axiom could not determine a reliable root cause.\n\n");

            if (facts.topHypothesis().isPresent()) {
                out.append("Closest hypothesis considered: ").append(facts.friendlyCategory().orElseThrow())
                    .append(" (").append(facts.confidenceLevel().orElseThrow().displayName())
                    .append(" confidence - ").append(facts.confidencePercentage().orElseThrow()).append("%)\n\n");

                if (facts.strongestContradiction().isPresent()) {
                    out.append("Why Axiom did not choose a cause:\n");
                    out.append("- ").append(facts.strongestContradiction().get()).append("\n\n");
                }
            }

            if (!facts.missingEvidence().isEmpty()) {
                out.append("Missing evidence:\n");
                for (String missing : facts.missingEvidence()) {
                    out.append("- ").append(missing).append('\n');
                }
                out.append('\n');
            }

            out.append("Recommended next step:\n");
            out.append(facts.recommendedAction()).append("\n\n");
        }

        out.append("Result: ").append(facts.friendlyDisposition());
        return out.toString();
    }

    @Override
    public String renderDetailed(RootCauseAssessment assessment, List<CorrelationEvidence> evidence) {
        AssessmentFacts facts = AssessmentFacts.derive(assessment, evidence);
        StringBuilder out = new StringBuilder();
        out.append(facts.testName()).append("\n\n");
        out.append("Disposition: ").append(assessment.disposition()).append('\n');
        out.append("Selected category: ")
            .append(assessment.selectedCategory().map(Enum::name).orElse("(none)")).append("\n\n");

        int index = 1;
        for (RootCauseHypothesis hypothesis : assessment.rankedHypotheses()) {
            out.append("Hypothesis ").append(index++).append(" [")
                .append(formatReasoningPaths(hypothesis.matchedReasoningPaths())).append("]\n");
            out.append("  Category: ").append(hypothesis.category()).append('\n');
            out.append("  Confidence: ")
                .append(String.format(Locale.ROOT, "%.2f", hypothesis.confidence())).append('\n');

            out.append("  Contributions:\n");
            for (ConfidenceContribution contribution : hypothesis.contributions()) {
                out.append("    ").append(formatWeight(contribution.weight()))
                    .append("  ").append(contribution.reason()).append('\n');
            }

            out.append("  Supporting evidence:\n");
            for (EvidenceReference reference : hypothesis.supportingEvidence()) {
                out.append("    - [").append(reference.evidenceId()).append("] ")
                    .append(reference.excerpt()).append('\n');
            }
            out.append("  Contradicting evidence:\n");
            for (EvidenceReference reference : hypothesis.contradictingEvidence()) {
                out.append("    - [").append(reference.evidenceId()).append("] ")
                    .append(reference.excerpt()).append('\n');
            }
            out.append('\n');
        }

        out.append("Missing evidence: ")
            .append(assessment.missingEvidence().isEmpty() ? "none" : String.join(", ", assessment.missingEvidence()));
        return out.toString();
    }

    private static String formatWeight(double weight) {
        return (weight >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", weight);
    }

    /** Singular "rule:" for one contributing rule, plural "rules:" once aggregation combines more. */
    private static String formatReasoningPaths(List<String> ruleIds) {
        return ruleIds.size() == 1
            ? "rule: " + ruleIds.get(0)
            : "rules: " + String.join(", ", ruleIds);
    }
}
