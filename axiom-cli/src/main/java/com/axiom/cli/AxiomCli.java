package com.axiom.cli;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.axiom.analyzer.AIEnhancedAnalyzer;
import com.axiom.analyzer.AiExplanation;
import com.axiom.analyzer.AnalysisResult;
import com.axiom.analyzer.AnalyzedFailure;
import com.axiom.analyzer.Analyzer;
import com.axiom.analyzer.ClaudeProvider;
import com.axiom.analyzer.DeterministicAnalyzer;
import com.axiom.analyzer.LLMProvider;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Thin composition root: builds the concrete pipeline (rule loading/preparation, engine,
 * strategy, parser, analyzer) from a rules file, then hands off to {@link Analyzer} alone for
 * the actual work. Presentation code below the analyzer call never reaches into
 * axiom-classifier/axiom-parser directly.
 */
public final class AxiomCli {

    private static final String DEFAULT_AI_PROVIDER = "claude";
    private static final Duration DEFAULT_AI_TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, System.getenv());
    }

    /**
     * Env-parameterized overload for testability — lets AI-configuration tests supply a
     * controlled environment map instead of mutating the real process environment.
     */
    static int run(String[] args, PrintStream out, PrintStream err, Map<String, String> env) {
        boolean aiEnabled = args.length > 0 && "--ai".equals(args[0]);
        String[] positional = aiEnabled ? Arrays.copyOfRange(args, 1, args.length) : args;

        if (positional.length != 2) {
            err.println("Usage: axiom [--ai] <rules.yaml> <report.xml>");
            return 2;
        }

        Path rulesPath = Path.of(positional[0]);
        Path reportPath = Path.of(positional[1]);

        try {
            Analyzer analyzer = createAnalyzer(rulesPath, aiEnabled, env);
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
    static Analyzer createAnalyzer(Path rulesPath, boolean aiEnabled, Map<String, String> env) {
        DeterministicAnalyzer deterministic = createDeterministicAnalyzer(rulesPath);
        return aiEnabled ? wrapWithAi(deterministic, env) : deterministic;
    }

    private static DeterministicAnalyzer createDeterministicAnalyzer(Path rulesPath) {
        List<RuleDefinition> definitions = new YamlRuleSource(rulesPath).loadRules();
        RuleProcessor ruleProcessor = new DefaultRuleProcessor();
        List<PreparedRule> prepared = ruleProcessor.process(definitions);

        RuleEngine ruleEngine = new DefaultRuleEngine(prepared);
        ClassificationStrategy strategy = new DeterministicStrategy();
        Parser parser = new JUnitXmlParser();

        return new DeterministicAnalyzer(parser, ruleEngine, strategy);
    }

    /**
     * AI must be an explicit opt-in ({@code --ai}), never triggered just because an env var
     * happens to be set — and if requested but not configurable, that's a fail-fast usage error
     * (caught by {@link #run}'s broad {@code RuntimeException} handler, exit {@code 1}), not a
     * silent fallback to deterministic-only.
     */
    private static Analyzer wrapWithAi(DeterministicAnalyzer deterministic, Map<String, String> env) {
        String providerName = env.getOrDefault("AXIOM_LLM_PROVIDER", DEFAULT_AI_PROVIDER);
        if (!DEFAULT_AI_PROVIDER.equals(providerName)) {
            throw new IllegalStateException(
                "Unsupported AXIOM_LLM_PROVIDER: " + providerName + " (supported: claude)");
        }

        String apiKey = env.get("AXIOM_LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "AI was requested (--ai) but AXIOM_LLM_API_KEY is not set");
        }

        LLMProvider provider = new ClaudeProvider(
            AnthropicOkHttpClient.builder().apiKey(apiKey).build());
        return new AIEnhancedAnalyzer(deterministic, provider, readTimeout(env));
    }

    private static Duration readTimeout(Map<String, String> env) {
        String raw = env.get("AXIOM_LLM_TIMEOUT_SECONDS");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_AI_TIMEOUT;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "AXIOM_LLM_TIMEOUT_SECONDS must be a whole number of seconds, got: " + raw);
        }
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
        analyzed.explanation().ifPresent(explanation -> printExplanation(explanation, out));
        out.println();
    }

    private static void printExplanation(AiExplanation explanation, PrintStream out) {
        out.println("  AI Summary:    " + explanation.summary());
        out.println("  AI Root Cause: " + explanation.rootCause());
        if (!explanation.suggestedNextSteps().isEmpty()) {
            out.println("  AI Suggested Next Steps:");
            for (String step : explanation.suggestedNextSteps()) {
                out.println("    - " + step);
            }
        }
        out.println("  AI Confidence Note: " + explanation.confidenceExplanation());
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
