package com.axiom.investigation.file;

import com.axiom.analyzer.Analyzer;
import com.axiom.analyzer.DeterministicAnalyzer;
import com.axiom.classifier.engine.ClassificationStrategy;
import com.axiom.classifier.engine.DefaultRuleEngine;
import com.axiom.classifier.engine.DeterministicStrategy;
import com.axiom.classifier.engine.RuleEngine;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.classifier.rule.DefaultRuleProcessor;
import com.axiom.classifier.rule.PreparedRule;
import com.axiom.classifier.rule.RuleDefinition;
import com.axiom.classifier.rule.RuleProcessor;
import com.axiom.classifier.rule.YamlRuleSource;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.HistoricalExecutionEvidence;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.investigation.model.CollectedEvidence;
import com.axiom.investigation.model.CollectionWarningType;
import com.axiom.investigation.model.InvestigationContext;
import com.axiom.investigation.model.TriggerType;
import com.axiom.parser.JUnitXmlParser;
import com.axiom.parser.Parser;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link FileEvidenceCollector} end to end against real files — no mocks, matching this
 * codebase's fixture-based convention. Per-reader behavior (malformed JSON, branch mismatch, etc.)
 * is covered more narrowly by each {@code *EvidenceReaderTest} in the {@code reader} package; this
 * class verifies the orchestration: the right readers run, results merge correctly.
 */
class FileEvidenceCollectorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-24T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final InvestigationContext MANUAL_CONTEXT =
        new InvestigationContext(TriggerType.MANUAL, null);

    private static Path resource(String path) {
        URL url = FileEvidenceCollectorTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static Analyzer deterministicAnalyzer(String rulesResourcePath) {
        List<RuleDefinition> definitions = new YamlRuleSource(resource(rulesResourcePath)).loadRules();
        RuleProcessor ruleProcessor = new DefaultRuleProcessor();
        List<PreparedRule> prepared = ruleProcessor.process(definitions);
        RuleEngine ruleEngine = new DefaultRuleEngine(prepared);
        ClassificationStrategy strategy = new DeterministicStrategy();
        Parser parser = new JUnitXmlParser();
        return new DeterministicAnalyzer(parser, ruleEngine, strategy);
    }

    private static CorrelationEvidence evidenceOfType(CollectedEvidence collected, EvidenceType type) {
        return collected.evidence().stream()
            .filter(e -> e.type() == type)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no evidence of type " + type));
    }

    @Test
    void allFourSourcesPresentProducesFourEvidenceItemsAndNoWarnings() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/single-failure.xml"),
            Optional.of(resource("correlation/changes.json")),
            Optional.of(resource("correlation/execution.json")),
            Optional.of(resource("correlation/history.json")),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertTrue(collected.warnings().isEmpty(), "unexpected warnings: " + collected.warnings());
        assertEquals(4, collected.evidence().size());

        TestFailureEvidence testFailure = (TestFailureEvidence) evidenceOfType(collected, EvidenceType.TEST_FAILURE);
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, testFailure.classification().category());

        SourceChangeEvidence sourceChange = (SourceChangeEvidence) evidenceOfType(collected, EvidenceType.SOURCE_CHANGE);
        assertEquals("abc123", sourceChange.commitSha());

        HistoricalExecutionEvidence historical =
            (HistoricalExecutionEvidence) evidenceOfType(collected, EvidenceType.HISTORICAL_EXECUTION);
        assertEquals(2, historical.runs().size());
    }

    @Test
    void onlyReportPresentProducesJustTestFailureEvidence() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/single-failure.xml"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertTrue(collected.warnings().isEmpty());
        assertEquals(1, collected.evidence().size());
        assertEquals(EvidenceType.TEST_FAILURE, collected.evidence().get(0).type());
    }

    @Test
    void malformedReportBecomesAWarningNotAnException() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/malformed.xml"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertTrue(collected.evidence().isEmpty());
        assertEquals(1, collected.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, collected.warnings().get(0).type());
    }

    @Test
    void reportWithNoFailuresBecomesAWarningWithNoEvidence() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/passed-only.xml"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertTrue(collected.evidence().isEmpty());
        assertEquals(1, collected.warnings().size());
    }

    @Test
    void reportWithMultipleFailuresBecomesAWarningRatherThanGuessingWhichOne() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/two-failures.xml"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertTrue(collected.evidence().isEmpty());
        assertEquals(1, collected.warnings().size());
        assertTrue(collected.warnings().get(0).message().contains("2 failures"));
    }

    @Test
    void malformedOptionalJsonInputBecomesAWarningNotAnException() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/single-failure.xml"),
            Optional.of(resource("correlation/malformed.json")),
            Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertEquals(1, collected.evidence().size(), "test-failure evidence still produced");
        assertEquals(1, collected.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, collected.warnings().get(0).type());
    }

    @Test
    void missingOptionalFileBecomesAWarningNotAnException() {
        FileEvidenceCollector collector = new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/single-failure.xml"),
            Optional.of(Path.of("does-not-exist.json")),
            Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        CollectedEvidence collected = collector.collect(MANUAL_CONTEXT);

        assertEquals(1, collected.evidence().size());
        assertEquals(1, collected.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, collected.warnings().get(0).type());
    }

    @Test
    void unexpectedRuntimeExceptionFromAnalyzerPropagatesRatherThanBecomingAWarning() {
        Analyzer brokenAnalyzer = report -> {
            throw new IllegalStateException("a real bug, not an operational failure");
        };
        FileEvidenceCollector collector = new FileEvidenceCollector(
            brokenAnalyzer, resource("reports/single-failure.xml"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            FIXED_CLOCK);

        assertThrows(IllegalStateException.class, () -> collector.collect(MANUAL_CONTEXT));
    }

    @Test
    void idIsStable() {
        assertEquals(FileEvidenceCollector.COLLECTOR_ID, new FileEvidenceCollector(
            deterministicAnalyzer("rules/connection-refused.yaml"),
            resource("reports/single-failure.xml"),
            Optional.empty(), Optional.empty(), Optional.empty(), FIXED_CLOCK).id());
    }
}
