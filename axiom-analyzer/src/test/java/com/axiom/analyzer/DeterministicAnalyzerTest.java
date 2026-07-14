package com.axiom.analyzer;

import com.axiom.classifier.engine.ClassificationStrategy;
import com.axiom.classifier.engine.DefaultRuleEngine;
import com.axiom.classifier.engine.DeterministicStrategy;
import com.axiom.classifier.engine.RuleEngine;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.classifier.rule.ClassificationSpec;
import com.axiom.classifier.rule.Condition;
import com.axiom.classifier.rule.DefaultRuleProcessor;
import com.axiom.classifier.rule.MatchGroup;
import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.PreparedRule;
import com.axiom.classifier.rule.RuleDefinition;
import com.axiom.classifier.rule.RuleField;
import com.axiom.classifier.rule.RuleProcessor;
import com.axiom.parser.JUnitXmlParser;
import com.axiom.parser.Parser;
import com.axiom.parser.ParsingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicAnalyzerTest {

    private static InputStream xml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static DeterministicAnalyzer analyzerWithConnectionRefusedRule() {
        RuleDefinition rule = new RuleDefinition(
            "connection-refused", null, 100, true,
            new MatchGroup(
                List.of(new Condition(RuleField.MESSAGE, Operator.CONTAINS, "Connection refused", null)),
                null),
            new ClassificationSpec(FailureCategory.INFRASTRUCTURE_FAILURE, 0.95),
            null);

        RuleProcessor ruleProcessor = new DefaultRuleProcessor();
        List<PreparedRule> prepared = ruleProcessor.process(List.of(rule));

        Parser parser = new JUnitXmlParser();
        RuleEngine ruleEngine = new DefaultRuleEngine(prepared);
        ClassificationStrategy strategy = new DeterministicStrategy();

        return new DeterministicAnalyzer(parser, ruleEngine, strategy);
    }

    @Test
    void singleFailureProducesOneAnalyzedFailure() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        InputStream report = xml("""
            <testsuite name="UserServiceTest">
              <testcase name="shouldReturnUser" classname="com.example.UserServiceTest" time="0.1">
                <failure message="Connection refused">stack</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertEquals(1, result.analyses().size());
        AnalyzedFailure analyzed = result.analyses().get(0);
        assertEquals("shouldReturnUser", analyzed.event().testName());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, analyzed.classification().category());
        assertEquals(0.95, analyzed.classification().confidence());
        assertEquals("connection-refused", analyzed.classification().matchedRuleId());
        assertTrue(result.parserWarnings().isEmpty());
    }

    @Test
    void multipleFailuresProduceMultipleAnalyzedFailuresInOrder() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        InputStream report = xml("""
            <testsuite name="UserServiceTest">
              <testcase name="testA" classname="com.example.A" time="0.1">
                <failure message="Connection refused">stack A</failure>
              </testcase>
              <testcase name="testB" classname="com.example.B" time="0.1">
                <failure message="Some other error">stack B</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertEquals(2, result.analyses().size());
        assertEquals("testA", result.analyses().get(0).event().testName());
        assertEquals("testB", result.analyses().get(1).event().testName());
    }

    @Test
    void passedOnlyReportProducesEmptyAnalyses() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        InputStream report = xml("""
            <testsuite name="UserServiceTest">
              <testcase name="shouldPass" classname="com.example.UserServiceTest" time="0.1"/>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertTrue(result.analyses().isEmpty());
        assertTrue(result.parserWarnings().isEmpty());
    }

    @Test
    void unmatchedFailureStillProducesAnalyzedFailureWithUnknownCategory() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        InputStream report = xml("""
            <testsuite name="UserServiceTest">
              <testcase name="testA" classname="com.example.A" time="0.1">
                <failure message="Something totally unrelated">stack</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertEquals(1, result.analyses().size());
        AnalyzedFailure analyzed = result.analyses().get(0);
        assertEquals(FailureCategory.UNKNOWN, analyzed.classification().category());
        assertEquals(0.0, analyzed.classification().confidence());
        assertNull(analyzed.classification().matchedRuleId());
    }

    @Test
    void parserWarningsPropagateIntoAnalysisResult() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        // First testcase lacks enough identifying attributes to construct a FailureEvent.
        InputStream report = xml("""
            <testsuite>
              <testcase time="0.1">
                <failure message="broken">broken stack</failure>
              </testcase>
              <testcase name="validTest" classname="com.example.Valid" time="0.1">
                <failure message="Connection refused">stack</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertEquals(1, result.analyses().size());
        assertEquals(1, result.parserWarnings().size());
    }

    @Test
    void emptyParserWarningsRemainEmptyWhenNothingIsMalformed() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        InputStream report = xml("""
            <testsuite name="UserServiceTest">
              <testcase name="testA" classname="com.example.A" time="0.1">
                <failure message="Connection refused">stack A</failure>
              </testcase>
              <testcase name="testB" classname="com.example.B" time="0.1">
                <failure message="Connection refused">stack B</failure>
              </testcase>
              <testcase name="testC" classname="com.example.C" time="0.1">
                <failure message="Connection refused">stack C</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertEquals(3, result.analyses().size());
        assertTrue(result.parserWarnings().isEmpty());
    }

    @Test
    void malformedXmlPropagatesParsingException() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();
        InputStream report = xml("<testsuite name=\"Broken\"><testcase name=\"t\">");

        assertThrows(ParsingException.class, () -> analyzer.analyze(report));
    }

    @Test
    void throwsWhenReportIsNull() {
        DeterministicAnalyzer analyzer = analyzerWithConnectionRefusedRule();

        assertThrows(NullPointerException.class, () -> analyzer.analyze(null));
    }
}
