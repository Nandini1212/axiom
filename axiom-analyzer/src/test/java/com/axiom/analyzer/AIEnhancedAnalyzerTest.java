package com.axiom.analyzer;

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
import com.axiom.parser.JUnitXmlParser;
import com.axiom.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AIEnhancedAnalyzerTest {

    private static final AiExplanation FIXED_EXPLANATION =
        new AiExplanation("summary", "root cause", List.of("check the service"), "high confidence, strong evidence match");

    private static InputStream xml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static DeterministicAnalyzer deterministicAnalyzer() {
        RuleDefinition rule = new RuleDefinition(
            "connection-refused", null, 100, true,
            new MatchGroup(
                List.of(new Condition(RuleField.MESSAGE, Operator.CONTAINS, "Connection refused", null)),
                null),
            new ClassificationSpec(FailureCategory.INFRASTRUCTURE_FAILURE, 0.95),
            null);

        List<PreparedRule> prepared = new DefaultRuleProcessor().process(List.of(rule));
        Parser parser = new JUnitXmlParser();
        RuleEngine ruleEngine = new DefaultRuleEngine(prepared);
        return new DeterministicAnalyzer(parser, ruleEngine, new DeterministicStrategy());
    }

    @Test
    void successfulExplanationIsAttachedWithoutChangingClassification() {
        AIEnhancedAnalyzer analyzer = new AIEnhancedAnalyzer(
            deterministicAnalyzer(), new FakeLLMProvider(FIXED_EXPLANATION), Duration.ofSeconds(5));

        InputStream report = xml("""
            <testsuite name="LoginTest">
              <testcase name="shouldLogin" classname="com.example.LoginTest" time="0.1">
                <failure message="Connection refused">stack</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertEquals(1, result.analyses().size());
        AnalyzedFailure analyzed = result.analyses().get(0);
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, analyzed.classification().category());
        assertEquals(0.95, analyzed.classification().confidence());
        assertEquals(Optional.of(FIXED_EXPLANATION), analyzed.explanation());
        assertTrue(result.analyzerWarnings().isEmpty());
    }

    @Test
    void providerFailureLeavesExplanationEmptyAndRecordsWarningWithoutChangingClassification() {
        AIEnhancedAnalyzer analyzer = new AIEnhancedAnalyzer(
            deterministicAnalyzer(), FakeLLMProvider.alwaysThrows(), Duration.ofSeconds(5));

        InputStream report = xml("""
            <testsuite name="LoginTest">
              <testcase name="shouldLogin" classname="com.example.LoginTest" time="0.1">
                <failure message="Connection refused">stack</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        AnalyzedFailure analyzed = result.analyses().get(0);
        assertTrue(analyzed.explanation().isEmpty());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, analyzed.classification().category());

        assertEquals(1, result.analyzerWarnings().size());
        assertEquals(AnalyzerWarningType.AI_EXPLANATION_FAILED, result.analyzerWarnings().get(0).type());
    }

    @Test
    void providerTimeoutLeavesExplanationEmptyAndRecordsTimeoutWarning() {
        AIEnhancedAnalyzer analyzer = new AIEnhancedAnalyzer(
            deterministicAnalyzer(), FakeLLMProvider.alwaysTimeout(), Duration.ofMillis(200));

        InputStream report = xml("""
            <testsuite name="LoginTest">
              <testcase name="shouldLogin" classname="com.example.LoginTest" time="0.1">
                <failure message="Connection refused">stack</failure>
              </testcase>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        AnalyzedFailure analyzed = result.analyses().get(0);
        assertTrue(analyzed.explanation().isEmpty());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, analyzed.classification().category());

        assertEquals(1, result.analyzerWarnings().size());
        assertEquals(AnalyzerWarningType.AI_TIMEOUT, result.analyzerWarnings().get(0).type());
    }

    @Test
    void passedOnlyReportProducesEmptyAnalysesAndNoAnalyzerWarnings() {
        AIEnhancedAnalyzer analyzer = new AIEnhancedAnalyzer(
            deterministicAnalyzer(), new FakeLLMProvider(FIXED_EXPLANATION), Duration.ofSeconds(5));

        InputStream report = xml("""
            <testsuite name="LoginTest">
              <testcase name="shouldPass" classname="com.example.LoginTest" time="0.1"/>
            </testsuite>
            """);

        AnalysisResult result = analyzer.analyze(report);

        assertTrue(result.analyses().isEmpty());
        assertTrue(result.analyzerWarnings().isEmpty());
    }

    @Test
    void parserWarningsStillPropagateThroughAiEnhancedAnalyzer() {
        AIEnhancedAnalyzer analyzer = new AIEnhancedAnalyzer(
            deterministicAnalyzer(), new FakeLLMProvider(FIXED_EXPLANATION), Duration.ofSeconds(5));

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
    void throwsWhenDeterministicIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AIEnhancedAnalyzer(null, new FakeLLMProvider(FIXED_EXPLANATION), Duration.ofSeconds(5)));
    }

    @Test
    void throwsWhenProviderIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AIEnhancedAnalyzer(deterministicAnalyzer(), null, Duration.ofSeconds(5)));
    }

    @Test
    void throwsWhenTimeoutIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AIEnhancedAnalyzer(deterministicAnalyzer(), new FakeLLMProvider(FIXED_EXPLANATION), null));
    }
}
