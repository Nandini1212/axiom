package com.axiom.classifier.engine;

import com.axiom.classifier.model.Evidence;
import com.axiom.classifier.model.RuleMatch;
import com.axiom.classifier.rule.PreparedCondition;
import com.axiom.classifier.rule.PreparedMatchGroup;
import com.axiom.classifier.rule.PreparedRule;
import com.axiom.classifier.rule.RuleField;
import com.axiom.common.model.FailureEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DefaultRuleEngine implements RuleEngine {

    private final List<PreparedRule> rules;

    public DefaultRuleEngine(List<PreparedRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules is mandatory"));
    }

    @Override
    public List<RuleMatch> evaluate(FailureEvent event) {
        Objects.requireNonNull(event, "event is mandatory");

        List<RuleMatch> matches = new ArrayList<>();
        for (PreparedRule rule : rules) {
            RuleMatch match = evaluateRule(event, rule);
            if (match != null) {
                matches.add(match);
            }
        }
        return List.copyOf(matches);
    }

    private RuleMatch evaluateRule(FailureEvent event, PreparedRule rule) {
        PreparedMatchGroup group = rule.match();
        boolean isAny = !group.any().isEmpty();
        List<PreparedCondition> conditions = isAny ? group.any() : group.all();

        List<Evidence> evidence = new ArrayList<>();
        for (PreparedCondition condition : conditions) {
            String actual = actualValue(event, condition.field());
            if (actual != null && matchesCondition(condition, actual)) {
                evidence.add(new Evidence(
                    condition.field(), condition.operator(), condition.value(), actual,
                    rule.evidenceMessage()));
            }
        }

        boolean groupMatched = isAny ? !evidence.isEmpty() : evidence.size() == conditions.size();
        if (!groupMatched) {
            return null;
        }

        return new RuleMatch(rule.id(), rule.priority(), rule.category(), rule.confidence(), evidence);
    }

    private static String actualValue(FailureEvent event, RuleField field) {
        return switch (field) {
            case MESSAGE -> event.message();
            case STACK_TRACE -> event.stackTrace();
            case TEST_NAME -> event.testName();
            case CLASS_NAME -> event.className();
            case SUITE_NAME -> event.suiteName();
        };
    }

    private static boolean matchesCondition(PreparedCondition condition, String actual) {
        String value = condition.value();
        boolean caseSensitive = condition.caseSensitive();

        return switch (condition.operator()) {
            case CONTAINS -> fold(actual, caseSensitive).contains(fold(value, caseSensitive));
            case EQUALS -> fold(actual, caseSensitive).equals(fold(value, caseSensitive));
            case STARTS_WITH -> fold(actual, caseSensitive).startsWith(fold(value, caseSensitive));
            case ENDS_WITH -> fold(actual, caseSensitive).endsWith(fold(value, caseSensitive));
            case REGEX -> condition.compiledPattern().matcher(actual).find();
        };
    }

    private static String fold(String value, boolean caseSensitive) {
        return caseSensitive ? value : value.toLowerCase(Locale.ROOT);
    }
}
