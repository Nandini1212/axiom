package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultRuleProcessorTest {

    private final DefaultRuleProcessor processor = new DefaultRuleProcessor();

    private static MatchGroup anyMatch(RuleField field, Operator operator, String value, Boolean caseSensitive) {
        return new MatchGroup(List.of(new Condition(field, operator, value, caseSensitive)), null);
    }

    private static RuleDefinition rule(
        String id, Integer priority, Boolean enabled, MatchGroup match, FailureCategory category) {
        return new RuleDefinition(
            id, null, priority, enabled, match, new ClassificationSpec(category, 0.5), null);
    }

    @Test
    void resolvesUnspecifiedPriorityToZero() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);

        List<PreparedRule> result = processor.process(List.of(definition));

        assertEquals(0, result.get(0).priority());
    }

    @Test
    void resolvesUnspecifiedEnabledToTrue() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);

        List<PreparedRule> result = processor.process(List.of(definition));

        assertEquals(1, result.size());
    }

    @Test
    void dropsRulesWhereEnabledIsExplicitlyFalse() {
        RuleDefinition enabled = rule(
            "r1", null, true, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);
        RuleDefinition disabled = rule(
            "r2", null, false, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "bust", null),
            FailureCategory.UNKNOWN);

        List<PreparedRule> result = processor.process(List.of(enabled, disabled));

        assertEquals(1, result.size());
        assertEquals("r1", result.get(0).id());
    }

    @Test
    void allRulesDisabledProducesEmptyOutput() {
        RuleDefinition disabled = rule(
            "r1", null, false, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);

        List<PreparedRule> result = processor.process(List.of(disabled));

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertTrue(processor.process(List.of()).isEmpty());
    }

    @Test
    void sortsByPriorityDescendingThenIdAscending() {
        RuleDefinition low = rule(
            "z-rule", 10, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "a", null),
            FailureCategory.UNKNOWN);
        RuleDefinition highB = rule(
            "b-rule", 100, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "b", null),
            FailureCategory.UNKNOWN);
        RuleDefinition highA = rule(
            "a-rule", 100, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "c", null),
            FailureCategory.UNKNOWN);

        List<PreparedRule> result = processor.process(List.of(low, highB, highA));

        assertEquals(List.of("a-rule", "b-rule", "z-rule"),
            result.stream().map(PreparedRule::id).toList());
    }

    @Test
    void throwsWhenDuplicateIdsPresentAcrossBatch() {
        RuleDefinition first = rule(
            "dup", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "a", null),
            FailureCategory.UNKNOWN);
        RuleDefinition second = rule(
            "dup", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "b", null),
            FailureCategory.UNKNOWN);

        assertThrows(RuleProcessingException.class, () -> processor.process(List.of(first, second)));
    }

    @Test
    void duplicateIdCheckAppliesEvenWhenOneRuleIsDisabled() {
        RuleDefinition enabled = rule(
            "dup", null, true, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "a", null),
            FailureCategory.UNKNOWN);
        RuleDefinition disabled = rule(
            "dup", null, false, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "b", null),
            FailureCategory.UNKNOWN);

        assertThrows(RuleProcessingException.class,
            () -> processor.process(List.of(enabled, disabled)));
    }

    @Test
    void resolvesUnspecifiedCaseSensitiveToFalse() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);

        List<PreparedRule> result = processor.process(List.of(definition));

        assertFalse(result.get(0).match().any().get(0).caseSensitive());
    }

    @Test
    void regexConditionGetsCompiledPattern() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.REGEX, "boom.*", null),
            FailureCategory.UNKNOWN);

        PreparedCondition condition = processor.process(List.of(definition)).get(0).match().any().get(0);

        assertNotNull(condition.compiledPattern());
        assertTrue(condition.compiledPattern().matcher("boomtown").matches());
    }

    @Test
    void regexConditionDefaultsToCaseInsensitive() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.REGEX, "boom.*", null),
            FailureCategory.UNKNOWN);

        PreparedCondition condition = processor.process(List.of(definition)).get(0).match().any().get(0);

        assertTrue(condition.compiledPattern().matcher("BOOMTOWN").matches());
    }

    @Test
    void regexConditionRespectsExplicitCaseSensitive() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.REGEX, "boom.*", true),
            FailureCategory.UNKNOWN);

        PreparedCondition condition = processor.process(List.of(definition)).get(0).match().any().get(0);

        assertFalse(condition.compiledPattern().matcher("BOOMTOWN").matches());
        assertTrue(condition.compiledPattern().matcher("boomtown").matches());
    }

    @Test
    void nonRegexConditionHasNullCompiledPattern() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);

        PreparedCondition condition = processor.process(List.of(definition)).get(0).match().any().get(0);

        assertNull(condition.compiledPattern());
    }

    @Test
    void throwsWhenRegexSyntaxIsInvalid() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.REGEX, "[unterminated", null),
            FailureCategory.UNKNOWN);

        assertThrows(RuleProcessingException.class, () -> processor.process(List.of(definition)));
    }

    @Test
    void flattensClassificationAndEvidenceOntoPreparedRule() {
        RuleDefinition definition = new RuleDefinition(
            "connection-refused", "desc", 100, true,
            anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "Connection refused", null),
            new ClassificationSpec(FailureCategory.INFRASTRUCTURE_FAILURE, 0.95),
            new EvidenceSpec("Dependent service unavailable"));

        PreparedRule result = processor.process(List.of(definition)).get(0);

        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, result.category());
        assertEquals(0.95, result.confidence());
        assertEquals("Dependent service unavailable", result.evidenceMessage());
    }

    @Test
    void evidenceMessageIsNullWhenEvidenceAbsent() {
        RuleDefinition definition = rule(
            "r1", null, null, anyMatch(RuleField.MESSAGE, Operator.CONTAINS, "boom", null),
            FailureCategory.UNKNOWN);

        PreparedRule result = processor.process(List.of(definition)).get(0);

        assertNull(result.evidenceMessage());
    }
}
