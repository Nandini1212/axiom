package com.axiom.classifier.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class DefaultRuleProcessor implements RuleProcessor {

    @Override
    public List<PreparedRule> process(List<RuleDefinition> definitions) {
        rejectDuplicateIds(definitions);

        List<PreparedRule> prepared = new ArrayList<>();
        for (RuleDefinition definition : definitions) {
            boolean enabled = definition.enabled() == null || definition.enabled();
            if (enabled) {
                prepared.add(prepare(definition));
            }
        }

        prepared.sort(
            Comparator.comparingInt(PreparedRule::priority).reversed()
                .thenComparing(PreparedRule::id));
        return List.copyOf(prepared);
    }

    private void rejectDuplicateIds(List<RuleDefinition> definitions) {
        Set<String> seen = new HashSet<>();
        for (RuleDefinition definition : definitions) {
            if (!seen.add(definition.id())) {
                throw new RuleProcessingException("Duplicate rule id: " + definition.id());
            }
        }
    }

    private PreparedRule prepare(RuleDefinition definition) {
        int priority = definition.priority() == null ? 0 : definition.priority();
        PreparedMatchGroup match = prepareMatchGroup(definition.id(), definition.match());
        String evidenceMessage =
            definition.evidence() == null ? null : definition.evidence().message();

        return new PreparedRule(
            definition.id(),
            priority,
            match,
            definition.classification().category(),
            definition.classification().confidence(),
            evidenceMessage);
    }

    private PreparedMatchGroup prepareMatchGroup(String ruleId, MatchGroup match) {
        return new PreparedMatchGroup(
            prepareConditions(ruleId, match.any()),
            prepareConditions(ruleId, match.all()));
    }

    private List<PreparedCondition> prepareConditions(String ruleId, List<Condition> conditions) {
        List<PreparedCondition> result = new ArrayList<>();
        for (Condition condition : conditions) {
            result.add(prepareCondition(ruleId, condition));
        }
        return result;
    }

    private PreparedCondition prepareCondition(String ruleId, Condition condition) {
        boolean caseSensitive =
            condition.caseSensitive() != null && condition.caseSensitive();

        Pattern compiledPattern = null;
        if (condition.operator() == Operator.REGEX) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            try {
                compiledPattern = Pattern.compile(condition.value(), flags);
            } catch (PatternSyntaxException e) {
                throw new RuleProcessingException(
                    "Invalid regex in rule '" + ruleId + "': " + condition.value(), e);
            }
        }

        return new PreparedCondition(
            condition.field(), condition.operator(), condition.value(), caseSensitive, compiledPattern);
    }
}
