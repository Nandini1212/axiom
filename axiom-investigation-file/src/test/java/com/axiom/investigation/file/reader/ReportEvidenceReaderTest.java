package com.axiom.investigation.file.reader;

import com.axiom.analyzer.Analyzer;
import com.axiom.analyzer.DeterministicAnalyzer;
import com.axiom.classifier.engine.ClassificationStrategy;
import com.axiom.classifier.engine.DefaultRuleEngine;
import com.axiom.classifier.engine.DeterministicStrategy;
import com.axiom.classifier.engine.RuleEngine;
import com.axiom.classifier.rule.DefaultRuleProcessor;
import com.axiom.classifier.rule.PreparedRule;
import com.axiom.classifier.rule.RuleDefinition;
import com.axiom.classifier.rule.RuleProcessor;
import com.axiom.classifier.rule.YamlRuleSource;
import com.axiom.investigation.file.reader.ReportEvidenceReader.ReportReadResult;
import com.axiom.investigation.model.CollectionWarningType;
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

import static org.junit.jupiter.api.Assertions.*;

/** Direct tests for {@link ReportEvidenceReader}, isolated from {@code FileEvidenceCollector}. */
class ReportEvidenceReaderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-24T09:00:00Z"), ZoneOffset.UTC);

    private static Path resource(String path) {
        URL url = ReportEvidenceReaderTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static Analyzer deterministicAnalyzer() {
        List<RuleDefinition> definitions = new YamlRuleSource(resource("rules/connection-refused.yaml")).loadRules();
        RuleProcessor ruleProcessor = new DefaultRuleProcessor();
        List<PreparedRule> prepared = ruleProcessor.process(definitions);
        RuleEngine ruleEngine = new DefaultRuleEngine(prepared);
        ClassificationStrategy strategy = new DeterministicStrategy();
        Parser parser = new JUnitXmlParser();
        return new DeterministicAnalyzer(parser, ruleEngine, strategy);
    }

    @Test
    void oneFailureProducesEvidenceAndTheUnderlyingFailureEvent() {
        ReportEvidenceReader reader = new ReportEvidenceReader(deterministicAnalyzer());

        ReportReadResult result = reader.read(resource("reports/single-failure.xml"), "test-collector", FIXED_CLOCK);

        assertTrue(result.warnings().isEmpty());
        assertTrue(result.evidence().isPresent());
        assertTrue(result.failureEvent().isPresent());
        assertEquals("shouldLogin", result.failureEvent().get().testName());
    }

    @Test
    void zeroFailuresProducesAWarningNotAnException() {
        ReportEvidenceReader reader = new ReportEvidenceReader(deterministicAnalyzer());

        ReportReadResult result = reader.read(resource("reports/passed-only.xml"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertTrue(result.failureEvent().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, result.warnings().get(0).type());
    }

    @Test
    void multipleFailuresProducesAWarningNamingTheCount() {
        ReportEvidenceReader reader = new ReportEvidenceReader(deterministicAnalyzer());

        ReportReadResult result = reader.read(resource("reports/two-failures.xml"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).message().contains("2 failures"));
    }

    @Test
    void malformedXmlProducesAWarningNotAnException() {
        ReportEvidenceReader reader = new ReportEvidenceReader(deterministicAnalyzer());

        ReportReadResult result = reader.read(resource("reports/malformed.xml"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void unexpectedRuntimeExceptionFromAnalyzerPropagates() {
        Analyzer brokenAnalyzer = report -> {
            throw new IllegalStateException("a real bug");
        };
        ReportEvidenceReader reader = new ReportEvidenceReader(brokenAnalyzer);

        assertThrows(IllegalStateException.class,
            () -> reader.read(resource("reports/single-failure.xml"), "test-collector", FIXED_CLOCK));
    }
}
