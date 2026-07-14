package com.axiom.parser;

import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserResultTest {

    private static final FailureEvent SOME_EVENT = new FailureEvent(
        "evt-1", "test", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
        null, null, null, null, null, null);

    private static final ParserWarning SOME_WARNING = new ParserWarning(
        WarningType.MISSING_ATTRIBUTE, null, null, null, "detail");

    @Test
    void constructsWithValidFields() {
        ParserResult result = new ParserResult(List.of(SOME_EVENT), List.of(SOME_WARNING));

        assertEquals(List.of(SOME_EVENT), result.failures());
        assertEquals(List.of(SOME_WARNING), result.warnings());
    }

    @Test
    void emptyListsAreAllowed() {
        ParserResult result = new ParserResult(List.of(), List.of());

        assertTrue(result.failures().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void throwsWhenFailuresIsNull() {
        assertThrows(NullPointerException.class, () -> new ParserResult(null, List.of()));
    }

    @Test
    void throwsWhenWarningsIsNull() {
        assertThrows(NullPointerException.class, () -> new ParserResult(List.of(), null));
    }

    @Test
    void failuresAreDefensivelyCopiedAndImmutable() {
        List<FailureEvent> mutable = new ArrayList<>();
        mutable.add(SOME_EVENT);

        ParserResult result = new ParserResult(mutable, List.of());
        mutable.add(SOME_EVENT);

        assertEquals(1, result.failures().size());
        assertThrows(UnsupportedOperationException.class, () -> result.failures().add(SOME_EVENT));
    }

    @Test
    void warningsAreDefensivelyCopiedAndImmutable() {
        List<ParserWarning> mutable = new ArrayList<>();
        mutable.add(SOME_WARNING);

        ParserResult result = new ParserResult(List.of(), mutable);
        mutable.add(SOME_WARNING);

        assertEquals(1, result.warnings().size());
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add(SOME_WARNING));
    }
}
