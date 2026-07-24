package com.axiom.cli;

import com.axiom.analyzer.Analyzer;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.correlation.engine.CorrelationEngine;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.investigation.engine.EvidenceCollector;
import com.axiom.investigation.engine.InvestigationRunner;
import com.axiom.investigation.file.FileEvidenceCollector;
import com.axiom.investigation.model.Investigation;
import com.axiom.investigation.model.InvestigationContext;
import com.axiom.investigation.model.TriggerType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code axiom benchmark} subcommand: runs every fixture case under a benchmark directory
 * through the real Investigation pipeline (same composition as {@link InvestigateCommand}) and
 * compares the actual {@link RootCauseAssessment} against an {@code expected-assessment.json},
 * so a change to the deterministic engine's rules/weights can be checked for regressions before
 * it's trusted — not a replacement for unit tests, a check on classification quality itself.
 * <p>
 * Expected directory shape: {@code <root>/<category>/<case-id>/} each holding {@code report.xml}
 * (mandatory), optionally {@code changes.json}/{@code execution.json}/{@code history.json}
 * (exactly {@link FileEvidenceCollector}'s own optionality), and a mandatory
 * {@code expected-assessment.json}. A directory missing either mandatory file is skipped, not
 * treated as an error — lets a benchmark root hold scratch/incomplete cases without breaking a run.
 */
final class BenchmarkCommand {

    private static final String USAGE = "Usage: axiom benchmark --rules <rules.yaml> <benchmark-dir>";

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
        .addModule(new Jdk8Module())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .build();

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path rulesPath = null;
        Path benchmarkRoot = null;

        for (int i = 0; i < args.length; i++) {
            if ("--rules".equals(args[i])) {
                if (i + 1 >= args.length) {
                    err.println("Missing value for --rules");
                    err.println(USAGE);
                    return 2;
                }
                rulesPath = Path.of(args[++i]);
            } else if (benchmarkRoot == null) {
                benchmarkRoot = Path.of(args[i]);
            } else {
                err.println("Unexpected argument: " + args[i]);
                err.println(USAGE);
                return 2;
            }
        }

        if (rulesPath == null) {
            err.println("Missing required flag: --rules");
            err.println(USAGE);
            return 2;
        }
        if (benchmarkRoot == null) {
            err.println("Missing required argument: <benchmark-dir>");
            err.println(USAGE);
            return 2;
        }
        if (!Files.isDirectory(benchmarkRoot)) {
            err.println("Not a directory: " + benchmarkRoot);
            return 2;
        }

        try {
            List<BenchmarkCase> cases = discoverCases(benchmarkRoot);
            if (cases.isEmpty()) {
                err.println("No benchmark cases found under " + benchmarkRoot);
                return 2;
            }

            Analyzer analyzer = AxiomCli.createDeterministicAnalyzer(rulesPath);
            CorrelationEngine engine = InvestigateCommand.createCorrelationEngine();

            List<BenchmarkResult> results = new ArrayList<>();
            for (BenchmarkCase testCase : cases) {
                results.add(runCase(testCase, analyzer, engine));
            }

            printResults(results, out);
            return results.stream().allMatch(BenchmarkResult::passed) ? 0 : 1;
        } catch (IOException | RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static BenchmarkResult runCase(BenchmarkCase testCase, Analyzer analyzer, CorrelationEngine engine) {
        EvidenceCollector collector = new FileEvidenceCollector(
            analyzer, testCase.reportPath(), testCase.changesPath(), testCase.executionPath(),
            testCase.historyPath(), Clock.systemUTC());
        InvestigationRunner runner = new InvestigationRunner(
            List.of(collector), engine, () -> UUID.randomUUID().toString(), Clock.systemUTC());

        Investigation investigation = runner.run(new InvestigationContext(TriggerType.MANUAL, null));
        RootCauseAssessment actual = investigation.assessment();
        ExpectedAssessment expected = testCase.expected();

        boolean passed = actual.disposition() == expected.disposition()
            && actual.selectedCategory().equals(expected.category());
        return new BenchmarkResult(testCase, actual, passed);
    }

    private static void printResults(List<BenchmarkResult> results, PrintStream out) {
        for (BenchmarkResult result : results) {
            BenchmarkCase testCase = result.testCase();
            out.println(testCase.category() + "/" + testCase.caseId());
            out.println("Actual:   " + describe(result.actual().disposition(), result.actual().selectedCategory()));
            out.println("Expected: " + describe(testCase.expected().disposition(), testCase.expected().category()));
            out.println(result.passed() ? "PASS" : "FAIL");
            out.println();
        }

        long passedCount = results.stream().filter(BenchmarkResult::passed).count();
        int total = results.size();
        out.println("Accuracy: " + passedCount + "/" + total
            + " (" + Math.round(100.0 * passedCount / total) + "%)");
    }

    private static String describe(AssessmentDisposition disposition, Optional<FailureCategory> category) {
        return disposition == AssessmentDisposition.DETERMINED ? category.orElseThrow().name() : "NEEDS_INVESTIGATION";
    }

    /**
     * Two-level walk ({@code <root>/<category>/<case-id>/}), sorted by category then case id for
     * deterministic, reproducible output across runs.
     */
    private static List<BenchmarkCase> discoverCases(Path benchmarkRoot) throws IOException {
        List<BenchmarkCase> cases = new ArrayList<>();
        try (DirectoryStream<Path> categoryDirs = Files.newDirectoryStream(benchmarkRoot, Files::isDirectory)) {
            for (Path categoryDir : categoryDirs) {
                try (DirectoryStream<Path> caseDirs = Files.newDirectoryStream(categoryDir, Files::isDirectory)) {
                    for (Path caseDir : caseDirs) {
                        readCase(categoryDir, caseDir).ifPresent(cases::add);
                    }
                }
            }
        }
        cases.sort(Comparator.comparing(BenchmarkCase::category).thenComparing(BenchmarkCase::caseId));
        return cases;
    }

    private static Optional<BenchmarkCase> readCase(Path categoryDir, Path caseDir) throws IOException {
        Path reportPath = caseDir.resolve("report.xml");
        Path expectedPath = caseDir.resolve("expected-assessment.json");
        if (!Files.isRegularFile(reportPath) || !Files.isRegularFile(expectedPath)) {
            return Optional.empty();
        }

        ExpectedAssessment expected = JSON_MAPPER.readValue(expectedPath.toFile(), ExpectedAssessment.class);
        return Optional.of(new BenchmarkCase(
            categoryDir.getFileName().toString(),
            caseDir.getFileName().toString(),
            reportPath,
            existingFile(caseDir.resolve("changes.json")),
            existingFile(caseDir.resolve("execution.json")),
            existingFile(caseDir.resolve("history.json")),
            expected));
    }

    private static Optional<Path> existingFile(Path path) {
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private record BenchmarkCase(
        String category, String caseId, Path reportPath,
        Optional<Path> changesPath, Optional<Path> executionPath, Optional<Path> historyPath,
        ExpectedAssessment expected) {
    }

    private record BenchmarkResult(BenchmarkCase testCase, RootCauseAssessment actual, boolean passed) {
    }

    /**
     * The fixture format for {@code expected-assessment.json} — mirrors
     * {@code RootCauseAssessment}'s own disposition/selectedCategory invariant (a {@code
     * DETERMINED} expectation must name a category; {@code NEEDS_INVESTIGATION} must not) so a
     * malformed fixture fails at load time, not silently mid-comparison.
     */
    private record ExpectedAssessment(AssessmentDisposition disposition, Optional<FailureCategory> category) {

        private ExpectedAssessment {
            Objects.requireNonNull(disposition, "disposition is mandatory");
            Objects.requireNonNull(category, "category is mandatory (use Optional.empty(), not null)");
            if (disposition == AssessmentDisposition.DETERMINED && category.isEmpty()) {
                throw new IllegalArgumentException("DETERMINED expectation must name a category");
            }
            if (disposition == AssessmentDisposition.NEEDS_INVESTIGATION && category.isPresent()) {
                throw new IllegalArgumentException("NEEDS_INVESTIGATION expectation must not name a category");
            }
        }
    }

    private BenchmarkCommand() {
    }
}
