package com.axiom.investigation.model;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.RootCauseAssessment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The result of running {@code InvestigationRunner} once: the context that started it, every
 * evidence item collected (across all collectors, deduplicated by {@code evidenceId}), every
 * collection-time warning, and the resulting {@link RootCauseAssessment} from the unchanged
 * {@code CorrelationEngine}. See {@code 17-investigation-architecture.md} §3.
 * <p>
 * {@code investigationId} and {@code startedAt} follow the same stable-id/timestamp conventions
 * already established elsewhere in this codebase ({@code CorrelationRule.id()},
 * {@code CorrelationEvidence.observedAt()}) — their presence here does not imply a persistence
 * layer exists or is planned by this type alone.
 */
public record Investigation(
        String investigationId,
        InvestigationContext context,
        Instant startedAt,
        List<CorrelationEvidence> evidence,
        List<CollectionWarning> collectionWarnings,
        RootCauseAssessment assessment
) {

    public Investigation {
        Objects.requireNonNull(investigationId, "investigationId is mandatory");
        Objects.requireNonNull(context, "context is mandatory");
        Objects.requireNonNull(startedAt, "startedAt is mandatory");
        Objects.requireNonNull(evidence, "evidence is mandatory");
        Objects.requireNonNull(collectionWarnings, "collectionWarnings is mandatory");
        Objects.requireNonNull(assessment, "assessment is mandatory");
        evidence = List.copyOf(evidence);
        collectionWarnings = List.copyOf(collectionWarnings);
    }
}
