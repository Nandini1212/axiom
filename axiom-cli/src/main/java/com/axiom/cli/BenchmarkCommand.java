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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * {@code expected-assessment.json}.
 * <p>
 * <b>An incomplete case directory (missing either mandatory file) fails dataset validation by
 * default</b> — a benchmark whose job is catching regressions must not silently become easier
 * because a fixture went missing (e.g. an accidentally deleted {@code expected-assessment.json}
 * quietly raising reported accuracy). Pass {@code --skip-incomplete} to opt into the lenient,
 * development-only behavior of skipping incomplete directories instead.
 * <p>
 * {@code Clock}/investigation-id generation are fixed and deterministic
 * ({@code benchmark-<category>-<case-id>}, a fixed reference {@link Instant}), not
 * {@code Clock.systemUTC()}/{@code UUID.randomUUID()} — benchmark output should be reproducible
 * run to run, including timestamps and generated ids, not just the category comparison.
 */
final class BenchmarkCommand {

    private static final String USAGE =
        "Usage: axiom benchmark --rules <rules.yaml> [--skip-incomplete] <benchmark-dir>";

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC);

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
        .addModule(new Jdk8Module())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .build();

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path rulesPath = null;
        Path benchmarkRoot = null;
        boolean skipIncomplete = false;

        for (int i = 0; i < args.length; i++) {
            if ("--rules".equals(args[i])) {
                if (i + 1 >= args.length) {
                    err.println("Missing value for --rules");
                    err.println(USAGE);
                    return 2;
                }
                rulesPath = Path.of(args[++i]);
            } else if ("--skip-incomplete".equals(args[i])) {
                skipIncomplete = true;
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
            DiscoveryResult discovery = discoverCases(benchmarkRoot);
            int discovered = discovery.cases().size() + discovery.incomplete().size();

            if (discovered == 0) {
                err.println("No benchmark cases found under " + benchmarkRoot);
                return 2;
            }
            if (!discovery.incomplete().isEmpty() && !skipIncomplete) {
                printSkipped(discovery.incomplete(), out);
            }
            if (discovery.cases().isEmpty()) {
                err.println("No valid benchmark cases found under " + benchmarkRoot
                    + " (" + discovery.incomplete().size() + " incomplete)");
                return 2;
            }

            Analyzer analyzer = AxiomCli.createDeterministicAnalyzer(rulesPath);
            CorrelationEngine engine = InvestigateCommand.createCorrelationEngine();

            List<BenchmarkResult> results = new ArrayList<>();
            for (BenchmarkCase testCase : discovery.cases()) {
                results.add(runCase(testCase, analyzer, engine));
            }

            printResults(results, out);
            printCounts(discovered, results.size(), discovery.incomplete().size(), out);

            boolean allPassed = results.stream().allMatch(BenchmarkResult::passed);
            if (!discovery.incomplete().isEmpty() && !skipIncomplete) {
                err.println("Dataset validation failed: " + discovery.incomplete().size()
                    + " incomplete case(s) found (pass --skip-incomplete to ignore)");
                return 1;
            }
            return allPassed ? 0 : 1;
        } catch (IOException | RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static BenchmarkResult runCase(BenchmarkCase testCase, Analyzer analyzer, CorrelationEngine engine) {
        EvidenceCollector collector = new FileEvidenceCollector(
            analyzer, testCase.reportPath(), testCase.changesPath(), testCase.executionPath(),
            testCase.historyPath(), FIXED_CLOCK);
        String investigationId = "benchmark-" + testCase.category() + "-" + testCase.caseId();
        InvestigationRunner runner = new InvestigationRunner(
            List.of(collector), engine, () -> investigationId, FIXED_CLOCK);

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

    private static void printSkipped(List<IncompleteCase> incomplete, PrintStream out) {
        for (IncompleteCase skipped : incomplete) {
            out.println("Skipped: " + skipped.category() + "/" + skipped.caseId());
            out.println("Reason: " + skipped.reason());
            out.println();
        }
    }

    private static void printCounts(int discovered, int executed, int skipped, PrintStream out) {
        out.println("Cases discovered: " + discovered);
        out.println("Cases executed: " + executed);
        out.println("Cases skipped: " + skipped);
    }

    private static String describe(AssessmentDisposition disposition, Optional<FailureCategory> category) {
        return disposition == AssessmentDisposition.DETERMINED ? category.orElseThrow().name() : "NEEDS_INVESTIGATION";
    }

    /**
     * Two-level walk ({@code <root>/<category>/<case-id>/}), sorted by category then case id for
     * deterministic, reproducible output across runs.
     */
    private static DiscoveryResult discoverCases(Path benchmarkRoot) throws IOException {
        List<BenchmarkCase> cases = new ArrayList<>();
        List<IncompleteCase> incomplete = new ArrayList<>();

        try (DirectoryStream<Path> categoryDirs = Files.newDirectoryStream(benchmarkRoot, Files::isDirectory)) {
            for (Path categoryDir : categoryDirs) {
                try (DirectoryStream<Path> caseDirs = Files.newDirectoryStream(categoryDir, Files::isDirectory)) {
                    for (Path caseDir : caseDirs) {
                        readCase(categoryDir, caseDir, cases, incomplete);
                    }
                }
            }
        }
        cases.sort(Comparator.comparing(BenchmarkCase::category).thenComparing(BenchmarkCase::caseId));
        incomplete.sort(Comparator.comparing(IncompleteCase::category).thenComparing(IncompleteCase::caseId));
        return new DiscoveryResult(cases, incomplete);
    }

    private static void readCase(
            Path categoryDir, Path caseDir, List<BenchmarkCase> cases, List<IncompleteCase> incomplete)
            throws IOException {
        String category = categoryDir.getFileName().toString();
        String caseId = caseDir.getFileName().toString();
        Path reportPath = caseDir.resolve("report.xml");
        Path expectedPath = caseDir.resolve("expected-assessment.json");

        boolean hasReport = Files.isRegularFile(reportPath);
        boolean hasExpected = Files.isRegularFile(expectedPath);
        if (!hasReport || !hasExpected) {
            String missing = !hasReport && !hasExpected
                ? "report.xml and expected-assessment.json missing"
                : (!hasReport ? "report.xml missing" : "expected-assessment.json missing");
            incomplete.add(new IncompleteCase(category, caseId, missing));
            return;
        }

        ExpectedAssessment expected = JSON_MAPPER.readValue(expectedPath.toFile(), ExpectedAssessment.class);
        cases.add(new BenchmarkCase(
            category, caseId, reportPath,
            existingFile(caseDir.resolve("changes.json")),
            existingFile(caseDir.resolve("execution.json")),
            existingFile(caseDir.resolve("history.json")),
            expected));
    }

    private static Optional<Path> existingFile(Path path) {
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private record DiscoveryResult(List<BenchmarkCase> cases, List<IncompleteCase> incomplete) {
    }

    private record IncompleteCase(String category, String caseId, String reason) {
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
