package com.axiom.classifier.rule;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PreparedConditionTest {

    @Test
    void constructsWithCompiledPatternWhenOperatorIsRegex() {
        Pattern pattern = Pattern.compile("boom.*");
        PreparedCondition condition =
            new PreparedCondition(RuleField.MESSAGE, Operator.REGEX, "boom.*", true, pattern);

        assertEquals(pattern, condition.compiledPattern());
    }

    @Test
    void constructsWithNullCompiledPatternWhenOperatorIsNotRegex() {
        PreparedCondition condition =
            new PreparedCondition(RuleField.MESSAGE, Operator.CONTAINS, "boom", false, null);

        assertNull(condition.compiledPattern());
    }

    @Test
    void throwsWhenOperatorIsRegexAndCompiledPatternIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
            new PreparedCondition(RuleField.MESSAGE, Operator.REGEX, "boom.*", false, null));
    }

    @Test
    void throwsWhenOperatorIsNotRegexAndCompiledPatternIsPresent() {
        Pattern pattern = Pattern.compile("boom.*");
        assertThrows(IllegalArgumentException.class, () ->
            new PreparedCondition(RuleField.MESSAGE, Operator.CONTAINS, "boom", false, pattern));
    }

    @Test
    void throwsWhenFieldIsNull() {
        assertThrows(NullPointerException.class, () ->
            new PreparedCondition(null, Operator.CONTAINS, "boom", false, null));
    }
}
