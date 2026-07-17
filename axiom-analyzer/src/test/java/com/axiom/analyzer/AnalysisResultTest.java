package com.axiom.analyzer;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import com.axiom.parser.ParserWarning;
import com.axiom.parser.WarningType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisResultTest {

    private static final AnalyzedFailure SOME_ANALYZED_FAILURE = new AnalyzedFailure(
        new FailureEvent("evt-1", "test", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
            null, null, null, null, null, null),
        new ClassificationResult(FailureCategory.UNKNOWN, 0.0, null, List.of()));

    private static final ParserWarning SOME_WARNING =
        new ParserWarning(WarningType.MISSING_ATTRIBUTE, null, null, null, "detail");

    private static final AnalyzerWarning SOME_ANALYZER_WARNING =
        new AnalyzerWarning(AnalyzerWarningType.AI_TIMEOUT, "evt-1", "timed out");

    @Test
    void twoArgConstructorLeavesAnalyzerWarningsEmpty() {
        AnalysisResult result = new AnalysisResult(List.of(SOME_ANALYZED_FAILURE), List.of(SOME_WARNING));

        assertEquals(List.of(SOME_ANALYZED_FAILURE), result.analyses());
        assertEquals(List.of(SOME_WARNING), result.parserWarnings());
        assertTrue(result.analyzerWarnings().isEmpty());
    }

    @Test
    void threeArgConstructorCanCarryAnalyzerWarnings() {
        AnalysisResult result = new AnalysisResult(
            List.of(SOME_ANALYZED_FAILURE), List.of(SOME_WARNING), List.of(SOME_ANALYZER_WARNING));

        assertEquals(List.of(SOME_ANALYZER_WARNING), result.analyzerWarnings());
    }

    @Test
    void emptyListsAreAllowed() {
        AnalysisResult result = new AnalysisResult(List.of(), List.of(), List.of());

        assertTrue(result.analyses().isEmpty());
        assertTrue(result.parserWarnings().isEmpty());
        assertTrue(result.analyzerWarnings().isEmpty());
    }

    @Test
    void throwsWhenAnalysesIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalysisResult(null, List.of()));
    }

    @Test
    void throwsWhenParserWarningsIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalysisResult(List.of(), null));
    }

    @Test
    void throwsWhenAnalyzerWarningsIsNull() {
        assertThrows(NullPointerException.class, () -> new AnalysisResult(List.of(), List.of(), null));
    }

    @Test
    void analysesAreDefensivelyCopiedAndImmutable() {
        List<AnalyzedFailure> mutable = new ArrayList<>();
        mutable.add(SOME_ANALYZED_FAILURE);

        AnalysisResult result = new AnalysisResult(mutable, List.of());
        mutable.add(SOME_ANALYZED_FAILURE);

        assertEquals(1, result.analyses().size());
        assertThrows(UnsupportedOperationException.class, () -> result.analyses().add(SOME_ANALYZED_FAILURE));
    }

    @Test
    void parserWarningsAreDefensivelyCopiedAndImmutable() {
        List<ParserWarning> mutable = new ArrayList<>();
        mutable.add(SOME_WARNING);

        AnalysisResult result = new AnalysisResult(List.of(), mutable);
        mutable.add(SOME_WARNING);

        assertEquals(1, result.parserWarnings().size());
        assertThrows(UnsupportedOperationException.class, () -> result.parserWarnings().add(SOME_WARNING));
    }

    @Test
    void analyzerWarningsAreDefensivelyCopiedAndImmutable() {
        List<AnalyzerWarning> mutable = new ArrayList<>();
        mutable.add(SOME_ANALYZER_WARNING);

        AnalysisResult result = new AnalysisResult(List.of(), List.of(), mutable);
        mutable.add(SOME_ANALYZER_WARNING);

        assertEquals(1, result.analyzerWarnings().size());
        assertThrows(UnsupportedOperationException.class, () -> result.analyzerWarnings().add(SOME_ANALYZER_WARNING));
    }
}
