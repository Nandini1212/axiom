package com.axiom.classifier.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreparedMatchGroupTest {

    private static final PreparedCondition SOME_CONDITION =
        new PreparedCondition(RuleField.MESSAGE, Operator.CONTAINS, "boom", false, null);

    @Test
    void constructsWithOnlyAnyPresent() {
        PreparedMatchGroup group = new PreparedMatchGroup(List.of(SOME_CONDITION), null);

        assertEquals(List.of(SOME_CONDITION), group.any());
        assertTrue(group.all().isEmpty());
    }

    @Test
    void constructsWithOnlyAllPresent() {
        PreparedMatchGroup group = new PreparedMatchGroup(null, List.of(SOME_CONDITION));

        assertEquals(List.of(SOME_CONDITION), group.all());
        assertTrue(group.any().isEmpty());
    }

    @Test
    void throwsWhenBothAnyAndAllPresent() {
        assertThrows(IllegalArgumentException.class, () ->
            new PreparedMatchGroup(List.of(SOME_CONDITION), List.of(SOME_CONDITION)));
    }

    @Test
    void throwsWhenBothAnyAndAllAbsent() {
        assertThrows(IllegalArgumentException.class, () -> new PreparedMatchGroup(null, null));
    }
}
