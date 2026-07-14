package com.axiom.classifier.rule;

import java.util.Objects;

/**
 * A single rule as authored in YAML, before RuleProcessor normalization.
 * <p>
 * {@code priority} and {@code enabled} are nullable rather than defaulted here: {@code null}
 * means the rule author left them unspecified in YAML, and the RuleProcessor stage (not this
 * record) decides the actual default and applies sorting/normalization. This record's only job
 * is to faithfully represent what the rule file said.
 */
public record RuleDefinition(
    String id,
    String description,
    Integer priority,
    Boolean enabled,
    MatchGroup match,
    ClassificationSpec classification,
    EvidenceSpec evidence
) {

    public RuleDefinition {
        Objects.requireNonNull(id, "id is mandatory");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(match, "match is mandatory");
        Objects.requireNonNull(classification, "classification is mandatory");
    }
}
