package com.axiom.classifier.rule;

import java.util.List;

/**
 * A {@link MatchGroup} after RuleProcessor normalization, holding {@link PreparedCondition}s
 * instead of raw {@link Condition}s. Same any/all-mutual-exclusivity invariant as
 * {@link MatchGroup}, re-asserted here since this record can be constructed independently of it.
 */
public record PreparedMatchGroup(List<PreparedCondition> any, List<PreparedCondition> all) {

    public PreparedMatchGroup {
        any = any == null ? List.of() : List.copyOf(any);
        all = all == null ? List.of() : List.copyOf(all);

        if (any.isEmpty() == all.isEmpty()) {
            throw new IllegalArgumentException("Exactly one of 'any' or 'all' must be non-empty");
        }
    }
}
