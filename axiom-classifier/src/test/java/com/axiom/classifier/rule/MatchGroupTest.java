package com.axiom.classifier.rule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchGroupTest {

    private static final Condition SOME_CONDITION =
        new Condition(RuleField.MESSAGE, Operator.CONTAINS, "boom", null);

    @Test
    void constructsWithOnlyAnyPresent() {
        MatchGroup group = new MatchGroup(List.of(SOME_CONDITION), null);

        assertEquals(List.of(SOME_CONDITION), group.any());
        assertTrue(group.all().isEmpty());
    }

    @Test
    void constructsWithOnlyAllPresent() {
        MatchGroup group = new MatchGroup(null, List.of(SOME_CONDITION));

        assertEquals(List.of(SOME_CONDITION), group.all());
        assertTrue(group.any().isEmpty());
    }

    @Test
    void throwsWhenBothAnyAndAllPresent() {
        assertThrows(IllegalArgumentException.class, () ->
            new MatchGroup(List.of(SOME_CONDITION), List.of(SOME_CONDITION)));
    }

    @Test
    void throwsWhenBothAnyAndAllAbsent() {
        assertThrows(IllegalArgumentException.class, () ->
            new MatchGroup(null, null));
    }

    @Test
    void throwsWhenBothAnyAndAllAreEmptyLists() {
        assertThrows(IllegalArgumentException.class, () ->
            new MatchGroup(List.of(), List.of()));
    }

    @Test
    void anyIsDefensivelyCopiedAndImmutable() {
        List<Condition> mutable = new ArrayList<>();
        mutable.add(SOME_CONDITION);

        MatchGroup group = new MatchGroup(mutable, null);
        mutable.add(SOME_CONDITION); // mutate the original after construction

        assertEquals(1, group.any().size());
        assertThrows(UnsupportedOperationException.class, () -> group.any().add(SOME_CONDITION));
    }
}
