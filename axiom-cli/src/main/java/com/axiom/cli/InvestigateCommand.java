package com.axiom.cli;

import com.axiom.analyzer.Analyzer;
import com.axiom.analyzer.DeterministicAnalyzer;
import com.axiom.correlation.engine.ApplicationBugCorrelationRule;
import com.axiom.correlation.engine.CorrelationEngine;
import com.axiom.correlation.engine.CorrelationRule;
import com.axiom.correlation.engine.InfrastructureFailureRule;
import com.axiom.correlation.engine.TransientFailureRule;
import com.axiom.correlation.presentation.AssessmentRenderer;
import com.axiom.correlation.presentation.TextAssessmentRenderer;
import com.axiom.correlation.signal.ChangeSetEvidenceMissingExtractor;
import com.axiom.correlation.signal.FailureClusterPresentExtractor;
import com.axiom.correlation.signal.HistoricalExecutionSignalExtractor;
import com.axiom.correlation.signal.RetryOutcomeExtractor;
import com.axiom.correlation.signal.SignalExtractor;
import com.axiom.correlation.signal.StackFrameMatchesChangedFileExtractor;
import com.axiom.correlation.signal.TopFrameIsTestCodeExtractor;
import com.axiom.investigation.engine.EvidenceCollector;
import com.axiom.investigation.engine.InvestigationRunner;
import com.axiom.investigation.file.FileEvidenceCollector;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.Investigation;
import com.axiom.investigation.model.InvestigationContext;
import com.axiom.investigation.model.TriggerType;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code axiom investigate} subcommand — the composition root for the Investigation pipeline
 * (see {@code 17-investigation-architecture.md}), the same role {@link AxiomCli} already plays
 * for the classic deterministic-classify(+AI) command. Wires {@link FileEvidenceCollector},
 * {@link CorrelationEngine} (every existing rule/extractor, unchanged), and
 * {@link InvestigationRunner} together, then hands off to {@code InvestigationRunner} alone for
 * the actual work — presentation code below that call never reaches into
 * axiom-correlation/axiom-investigation-file directly.
 * <p>
 * {@code --report}/{@code --rules} are mandatory; {@code --changes}/{@code --execution}/
 * {@code --history} are each optional, exactly matching {@link FileEvidenceCollector}'s own
 * optionality — a missing one just means that evidence source contributes nothing.
 */
final class InvestigateCommand {

    private static final String USAGE = "Usage: axiom investigate --rules <rules.yaml> "
        + "--report <report.xml> [--changes <changes.json>] [--execution <execution.json>] "
        + "[--history <history.json>]";

    static int run(String[] args, PrintStream out, PrintStream err) {
        ParsedArgs parsed;
        try {
            parsed = parseArgs(args);
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 2;
        }

        try {
            Analyzer analyzer = AxiomCli.createDeterministicAnalyzer(parsed.rulesPath);
            EvidenceCollector collector = new FileEvidenceCollector(
                analyzer, parsed.reportPath, parsed.changesPath, parsed.executionPath,
                parsed.historyPath, Clock.systemUTC());
            InvestigationRunner runner = new InvestigationRunner(
                List.of(collector), createCorrelationEngine(),
                () -> UUID.randomUUID().toString(), Clock.systemUTC());

            InvestigationContext context = new InvestigationContext(TriggerType.MANUAL, null);
            Investigation investigation = runner.run(context);
            printInvestigation(investigation, out);
            return 0;
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static CorrelationEngine createCorrelationEngine() {
        List<SignalExtractor> extractors = List.of(
            new StackFrameMatchesChangedFileExtractor(),
            new TopFrameIsTestCodeExtractor(),
            new RetryOutcomeExtractor(),
            new ChangeSetEvidenceMissingExtractor(),
            new FailureClusterPresentExtractor(),
            new HistoricalExecutionSignalExtractor());
        List<CorrelationRule> rules = List.of(
            new ApplicationBugCorrelationRule(),
            new InfrastructureFailureRule(),
            new TransientFailureRule());
        return new CorrelationEngine(extractors, rules);
    }

    private static void printInvestigation(Investigation investigation, PrintStream out) {
        AssessmentRenderer renderer = new TextAssessmentRenderer();
        out.println(renderer.renderSummary(investigation.assessment(), investigation.evidence()));
        out.println();
        printCollectionWarnings(investigation.collectionWarnings(), out);
    }

    private static void printCollectionWarnings(List<CollectionWarning> warnings, PrintStream out) {
        if (warnings.isEmpty()) {
            out.println("Collection warnings: none");
            return;
        }
        out.println("Collection warnings:");
        for (CollectionWarning warning : warnings) {
            out.println("  - [" + warning.collectorId() + "] " + warning.message());
        }
    }

    private static ParsedArgs parseArgs(String[] args) {
        Path rulesPath = null;
        Path reportPath = null;
        Path changesPath = null;
        Path executionPath = null;
        Path historyPath = null;

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (i + 1 >= args.length) {
                throw new UsageException("Missing value for " + flag);
            }
            String value = args[++i];
            switch (flag) {
                case "--rules" -> rulesPath = Path.of(value);
                case "--report" -> reportPath = Path.of(value);
                case "--changes" -> changesPath = Path.of(value);
                case "--execution" -> executionPath = Path.of(value);
                case "--history" -> historyPath = Path.of(value);
                default -> throw new UsageException("Unknown flag: " + flag);
            }
        }

        if (rulesPath == null) {
            throw new UsageException("Missing required flag: --rules");
        }
        if (reportPath == null) {
            throw new UsageException("Missing required flag: --report");
        }

        return new ParsedArgs(rulesPath, reportPath,
            Optional.ofNullable(changesPath), Optional.ofNullable(executionPath), Optional.ofNullable(historyPath));
    }

    private record ParsedArgs(
        Path rulesPath, Path reportPath,
        Optional<Path> changesPath, Optional<Path> executionPath, Optional<Path> historyPath) {
    }

    private static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    private InvestigateCommand() {
    }
}
