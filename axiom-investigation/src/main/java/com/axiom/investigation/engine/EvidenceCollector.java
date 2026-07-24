package com.axiom.investigation.engine;

import com.axiom.investigation.model.CollectedEvidence;
import com.axiom.investigation.model.InvestigationContext;

/**
 * The only extension point for external evidence sources — see
 * {@code 17-investigation-architecture.md} §5. No provider-specific interface
 * ({@code GitHubEvidenceCollector}, etc.) exists; every source implements this same shape.
 * <p>
 * <b>Failure contract</b>: implementations must convert expected operational failures (a
 * timeout, a rate limit, a malformed input file) into {@link CollectedEvidence#warnings()} —
 * they must not throw for those cases. An unexpected exception (a programming error) is allowed
 * to propagate; {@code InvestigationRunner} does not catch it, so a real bug surfaces immediately
 * rather than being silently absorbed as a warning.
 */
public interface EvidenceCollector {

    /**
     * A stable identifier for this collector, referenced by
     * {@code CollectionWarning.collectorId()} — same reasoning {@code CorrelationRule.id()}
     * already carries one.
     */
    String id();

    CollectedEvidence collect(InvestigationContext context);
}
