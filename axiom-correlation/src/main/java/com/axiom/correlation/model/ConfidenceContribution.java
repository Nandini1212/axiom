package com.axiom.correlation.model;

import java.util.List;
import java.util.Objects;

/**
 * One signed, named adjustment to a hypothesis's confidence score — retained individually (not
 * just summed away) so an assessment can show exactly how it arrived at its final number.
 */
public record ConfidenceContribution(String ruleId, double weight, String reason, List<String> evidenceIds) {

    public ConfidenceContribution {
        Objects.requireNonNull(ruleId, "ruleId is mandatory");
        Objects.requireNonNull(reason, "reason is mandatory");
        Objects.requireNonNull(evidenceIds, "evidenceIds is mandatory");
        evidenceIds = List.copyOf(evidenceIds);
    }
}
