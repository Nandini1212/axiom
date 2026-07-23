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
 * GitHub-flavored Markdown {@link AssessmentRenderer} — meant to be pasted directly into a PR or
 * CI comment. Shows only what the engine actually computed: no assigned owner, no time estimate.
 * Axiom has no code-ownership mapping or historical-timing evidence source today, so fields like
 * that would be fabricated, not derived — add them only once a real evidence source for either
 * exists, not as renderer-invented content.
 */
public final class MarkdownAssessmentRenderer implements AssessmentRenderer {

    @Override
    public String renderSummary(RootCauseAssessment assessment, List<CorrelationEvidence> evidence) {
        AssessmentFacts facts = AssessmentFacts.derive(assessment, evidence);
        StringBuilder out = new StringBuilder();
        out.append("## Axiom Investigation: ").append(facts.testName()).append("\n\n");

        if (facts.disposition() == AssessmentDisposition.DETERMINED) {
            out.append("**Verdict:** ").append(facts.friendlyCategory().orElseThrow())
                .append(" (").append(facts.confidenceLevel().orElseThrow().displayName())
                .append(" confidence - ").append(facts.confidencePercentage().orElseThrow()).append("%)\n\n");

            out.append("**Why**\n");
            for (String reason : facts.topSupportingReasons()) {
                out.append("- ").append(reason).append('\n');
            }
            out.append('\n');

            out.append("**Evidence against:** ").append(facts.strongestContradiction().orElse("none")).append("\n\n");

            if (facts.changedFile().isPresent()) {
                out.append("**Files to review**\n");
                out.append("- ").append(facts.changedFile().get()).append("\n\n");
            }

            out.append("**Recommended next step**\n");
            out.append(facts.recommendedAction()).append("\n\n");
        } else {
            out.append("**Status:** More investigation needed\n\n");
            out.append("Axiom could not determine a reliable root cause.\n\n");

            if (facts.topHypothesis().isPresent()) {
                out.append("**Closest hypothesis considered:** ").append(facts.friendlyCategory().orElseThrow())
                    .append(" (").append(facts.confidenceLevel().orElseThrow().displayName())
                    .append(" confidence - ").append(facts.confidencePercentage().orElseThrow()).append("%)\n\n");

                if (facts.strongestContradiction().isPresent()) {
                    out.append("**Why Axiom did not choose a cause**\n");
                    out.append("- ").append(facts.strongestContradiction().get()).append("\n\n");
                }
            }

            if (!facts.missingEvidence().isEmpty()) {
                out.append("**Missing evidence**\n");
                for (String missing : facts.missingEvidence()) {
                    out.append("- ").append(missing).append('\n');
                }
                out.append('\n');
            }

            out.append("**Recommended next step**\n");
            out.append(facts.recommendedAction()).append("\n\n");
        }

        out.append("**Result:** ").append(facts.friendlyDisposition());
        return out.toString();
    }

    @Override
    public String renderDetailed(RootCauseAssessment assessment, List<CorrelationEvidence> evidence) {
        AssessmentFacts facts = AssessmentFacts.derive(assessment, evidence);
        StringBuilder out = new StringBuilder();
        out.append("## Axiom Investigation: ").append(facts.testName()).append("\n\n");
        out.append("**Disposition:** ").append(assessment.disposition()).append("  \n");
        out.append("**Selected category:** ")
            .append(assessment.selectedCategory().map(Enum::name).orElse("(none)")).append("\n\n");

        int index = 1;
        for (RootCauseHypothesis hypothesis : assessment.rankedHypotheses()) {
            out.append("### Hypothesis ").append(index++).append(" — rule `")
                .append(hypothesis.matchedReasoningPath()).append("`\n\n");
            out.append("- **Category:** ").append(hypothesis.category()).append('\n');
            out.append("- **Confidence:** ")
                .append(String.format(Locale.ROOT, "%.2f", hypothesis.confidence())).append("\n\n");

            out.append("**Contributions**\n\n");
            out.append("| Weight | Reason |\n");
            out.append("| --- | --- |\n");
            for (ConfidenceContribution contribution : hypothesis.contributions()) {
                out.append("| ").append(formatWeight(contribution.weight())).append(" | ")
                    .append(contribution.reason()).append(" |\n");
            }
            out.append('\n');

            out.append("**Supporting evidence**\n");
            for (EvidenceReference reference : hypothesis.supportingEvidence()) {
                out.append("- `").append(reference.evidenceId()).append("` ")
                    .append(reference.excerpt()).append('\n');
            }
            out.append("\n**Contradicting evidence**\n");
            for (EvidenceReference reference : hypothesis.contradictingEvidence()) {
                out.append("- `").append(reference.evidenceId()).append("` ")
                    .append(reference.excerpt()).append('\n');
            }
            out.append('\n');
        }

        out.append("**Missing evidence:** ")
            .append(assessment.missingEvidence().isEmpty() ? "none" : String.join(", ", assessment.missingEvidence()));
        return out.toString();
    }

    private static String formatWeight(double weight) {
        return (weight >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", weight);
    }
}
