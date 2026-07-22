package com.axiom.correlation.engine;

import java.util.Optional;

/**
 * A single deterministic reasoning path. Implemented as Java code, not YAML, for v0.1 — the
 * cross-evidence-type weighted logic these rules need doesn't map cleanly onto
 * {@code axiom-classifier}'s field/operator/value condition shape without a materially bigger DSL.
 * Revisit externalizing rules only if a concrete need for non-recompiled customization shows up.
 */
public interface CorrelationRule {

    /** Stable identifier — referenced by {@code ConfidenceContribution.ruleId} and by tests. */
    String id();

    Optional<RuleEvaluation> evaluate(CorrelationContext context);
}
