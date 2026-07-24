package com.axiom.investigation.model;

import com.axiom.common.model.PipelineContext;

import java.util.Objects;

/**
 * Identifies what is being investigated. Wraps the existing {@link PipelineContext} (repository,
 * branch, commit, PR number) rather than re-declaring those fields — see
 * {@code 17-investigation-architecture.md} §4. {@code pipelineContext} is nullable, same
 * "null means no CI context at all" convention {@link com.axiom.common.model.FailureEvent}
 * already established, not {@code Optional}, for consistency with that exact field elsewhere.
 * <p>
 * Deliberately does not carry file paths, API endpoints, or any other collector-specific
 * configuration — a collector that needs one is constructed with it directly, not handed it
 * through this context.
 */
public record InvestigationContext(TriggerType triggerType, PipelineContext pipelineContext) {

    public InvestigationContext {
        Objects.requireNonNull(triggerType, "triggerType is mandatory");
    }
}
