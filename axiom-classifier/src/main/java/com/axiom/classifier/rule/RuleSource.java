package com.axiom.classifier.rule;

import java.util.List;

/**
 * Loads {@link RuleDefinition}s from wherever they are authored (YAML file, future
 * database-backed source, etc.), without applying any RuleProcessor-stage normalization.
 */
public interface RuleSource {

    /**
     * @return the rule definitions from this source, in source order
     * @throws RuleSourceException if the rules cannot be loaded or parsed
     */
    List<RuleDefinition> loadRules();
}
