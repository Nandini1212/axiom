package com.axiom.classifier.rule;

import java.util.List;

/**
 * The set of {@link Condition}s a {@link RuleDefinition} matches against, combined with either
 * OR ({@code any}) or AND ({@code all}) semantics.
 * <p>
 * Exactly one of {@code any}/{@code all} must be non-empty — the two are mutually exclusive and
 * not nestable. This is a deliberate MVP simplification; a general boolean expression tree over
 * nested groups is deferred until a concrete rule needs it.
 */
public record MatchGroup(List<Condition> any, List<Condition> all) {

    public MatchGroup {
        any = any == null ? List.of() : List.copyOf(any);
        all = all == null ? List.of() : List.copyOf(all);

        if (any.isEmpty() == all.isEmpty()) {
            throw new IllegalArgumentException("Exactly one of 'any' or 'all' must be non-empty");
        }
    }
}
