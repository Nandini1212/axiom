package com.axiom.common.model;

/**
 * Describes the CI/CD pipeline a {@link FailureEvent} occurred in.
 * <p>
 * All fields are optional: a failure parsed from a local XML file on a developer's
 * machine has no pipeline at all, and a {@code FailureEvent.pipelineContext()} of
 * {@code null} communicates that directly. Deliberately no {@code empty()} factory —
 * an instance filled with eight nulls carries no more information than the reference
 * being null, and forces every caller to unpack it to find that out.
 * <p>
 * Individual fields within a present context remain nullable too: a provider may
 * supply a repository and branch but no pull request number, for instance.
 */
public record PipelineContext(
    String provider,
    String repository,
    String workflow,
    String job,
    String branch,
    String commitSha,
    String pullRequestNumber,
    String runId
) {
}
