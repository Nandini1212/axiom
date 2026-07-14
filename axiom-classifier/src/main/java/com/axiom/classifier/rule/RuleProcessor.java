package com.axiom.classifier.rule;

import java.util.List;

/**
 * Turns raw {@link RuleDefinition}s into runtime-ready {@link PreparedRule}s: validates
 * rule-set-wide invariants (unique ids), resolves defaults ({@code priority}, {@code enabled},
 * per-condition {@code caseSensitive}), precompiles regex conditions, drops disabled rules, and
 * returns the result in a deterministic evaluation order.
 */
public interface RuleProcessor {

    /**
     * @return prepared rules, sorted by priority descending then id ascending; disabled rules
     *         from the input are not present in the output
     * @throws RuleProcessingException if the rule set is invalid (duplicate ids, unparseable
     *         regex, etc.)
     */
    List<PreparedRule> process(List<RuleDefinition> definitions);
}
