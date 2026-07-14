package com.axiom.cli;

import com.axiom.analyzer.AnalysisResult;
import com.axiom.analyzer.AnalyzedFailure;
import com.axiom.analyzer.Analyzer;
import com.axiom.analyzer.DeterministicAnalyzer;
import com.axiom.classifier.engine.ClassificationStrategy;
import com.axiom.classifier.engine.DefaultRuleEngine;
import com.axiom.classifier.engine.DeterministicStrategy;
import com.axiom.classifier.engine.RuleEngine;
import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.rule.DefaultRuleProcessor;
import com.axiom.classifier.rule.PreparedRule;
import com.axiom.classifier.rule.RuleDefinition;
import com.axiom.classifier.rule.RuleProcessor;
import com.axiom.classifier.rule.YamlRuleSource;
import com.axiom.common.model.FailureEvent;
import com.axiom.parser.JUnitXmlParser;
import com.axiom.parser.Parser;
import com.axiom.parser.ParserWarning;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Thin composition root: builds the concrete pipeline (rule loading/preparation, engine,
 * strategy, parser, analyzer) from a rules file, then hands off to {@link Analyzer} alone for
 * the actual work. Presentation code below the analyzer call never reaches into
 * axiom-classifier/axiom-parser directly.
 */
public final class AxiomCli {

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 2) {
            err.println("Usage: axiom <rules.yaml> <report.xml>");
            return 2;
        }

        Path rulesPath = Path.of(args[0]);
        Path reportPath = Path.of(args[1]);

        try {
            Analyzer analyzer = createAnalyzer(rulesPath);
            try (InputStream report = Files.newInputStream(reportPath)) {
                AnalysisResult result = analyzer.analyze(report);
                printResult(result, out);
                return 0;
            }
        } catch (IOException e) {
            err.println("Error reading report file: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /** Constructs the concrete dependency graph. Nothing past this method knows these types exist. */
    static Analyzer createAnalyzer(Path rulesPath) {
        List<RuleDefinition> definitions = new YamlRuleSource(rulesPath).loadRules();
        RuleProcessor ruleProcessor = new DefaultRuleProcessor();
        List<PreparedRule> prepared = ruleProcessor.process(definitions);

        RuleEngine ruleEngine = new DefaultRuleEngine(prepared);
        ClassificationStrategy strategy = new DeterministicStrategy();
        Parser parser = new JUnitXmlParser();

        return new DeterministicAnalyzer(parser, ruleEngine, strategy);
    }

    /**
     * Deliberately temporary, inline presentation logic — not a preview of axiom-reporting's
     * eventual {@code Reporter} design, which doesn't exist yet. Expected to be replaced once
     * that module is actually designed.
     */
    private static void printResult(AnalysisResult result, PrintStream out) {
        out.println("Detected " + result.analyses().size() + " failure(s)");
        out.println();

        for (AnalyzedFailure analyzed : result.analyses()) {
            printAnalyzedFailure(analyzed, out);
        }

        printWarnings(result.parserWarnings(), out);
    }

    private static void printAnalyzedFailure(AnalyzedFailure analyzed, PrintStream out) {
        FailureEvent event = analyzed.event();
        ClassificationResult classification = analyzed.classification();

        out.println(event.status() + "  " + describeTest(event));
        out.println("  Category:   " + classification.category());
        out.println("  Confidence: " + classification.confidence());
        if (classification.matchedRuleId() != null) {
            out.println("  Rule:       " + classification.matchedRuleId());
        } else {
            out.println("  (no rule matched)");
        }
        out.println();
    }

    private static void printWarnings(List<ParserWarning> warnings, PrintStream out) {
        if (warnings.isEmpty()) {
            out.println("Warnings: none");
            return;
        }
        out.println("Warnings:");
        for (ParserWarning warning : warnings) {
            out.println("  - " + warning.detail());
        }
    }

    private static String describeTest(FailureEvent event) {
        String name = event.testName() != null ? event.testName() : event.suiteName();
        return event.className() != null ? name + " (" + event.className() + ")" : name;
    }

    private AxiomCli() {
    }
}
