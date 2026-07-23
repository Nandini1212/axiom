package com.axiom.correlation.presentation;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.RootCauseAssessment;

import java.util.List;

/**
 * Formats a {@link RootCauseAssessment} for a human reader. Deliberately outside
 * {@code CorrelationEngine} — formatting is a presentation concern, not correlation logic.
 * Takes the originating {@code evidence} alongside the assessment because
 * {@code RootCauseAssessment} itself carries no back-reference to which failure it's about (no
 * test name, no changed-file list) — that context only exists in the evidence it was built from.
 * Future implementations ({@code JsonAssessmentRenderer}, {@code MarkdownAssessmentRenderer},
 * {@code GitHubCommentRenderer}) implement this same interface without
 * {@code CorrelationEngine}/{@code RootCauseAssessment} needing to change.
 */
public interface AssessmentRenderer {

    /** Concise, default-mode output: likely cause, confidence, top reasons, one next step. */
    String renderSummary(RootCauseAssessment assessment, List<CorrelationEvidence> evidence);

    /** Full detail: every contribution (with weights), every evidence reference, rule id. */
    String renderDetailed(RootCauseAssessment assessment, List<CorrelationEvidence> evidence);
}
