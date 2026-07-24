package com.axiom.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@code axiom benchmark}, exercising the real Investigation pipeline
 * against fixture directories and verifying pass/fail/accuracy reporting.
 */
class BenchmarkCommandTest {

    private record RunResult(int exitCode, String out, String err) {
    }

    private static Path resource(String path) {
        URL url = BenchmarkCommandTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static RunResult runCli(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exitCode = AxiomCli.run(args, out, err, Map.of());

        return new RunResult(
            exitCode, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void allFourCategoriesPassAndReportFullAccuracy() {
        RunResult result = runCli("benchmark",
            "--rules", resource("benchmark/rules.yaml").toString(),
            resource("benchmark").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("application/case-1"));
        assertTrue(result.out().contains("infrastructure/case-1"));
        assertTrue(result.out().contains("flaky/case-1"));
        assertTrue(result.out().contains("unknown/case-1"));
        assertTrue(result.out().contains("Accuracy: 4/4 (100%)"));
        assertTrue(result.out().lines().noneMatch("FAIL"::equals),
            "no case should report FAIL -- note INFRASTRUCTURE_FAILURE itself contains the substring \"FAIL\"");
    }

    @Test
    void mismatchedExpectationReturnsExitCode1AndReportsFail() {
        RunResult result = runCli("benchmark",
            "--rules", resource("benchmark/rules.yaml").toString(),
            resource("benchmark-mismatch").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.out().contains("Actual:   APPLICATION_BUG"));
        assertTrue(result.out().contains("Expected: INFRASTRUCTURE_FAILURE"));
        assertTrue(result.out().contains("FAIL"));
        assertTrue(result.out().contains("Accuracy: 0/1 (0%)"));
    }

    @Test
    void missingRulesFlagReturnsUsageErrorExitCode2() {
        RunResult result = runCli("benchmark", resource("benchmark").toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("--rules"));
    }

    @Test
    void missingDirectoryArgumentReturnsUsageErrorExitCode2() {
        RunResult result = runCli("benchmark", "--rules", resource("benchmark/rules.yaml").toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("benchmark-dir"));
    }

    @Test
    void nonexistentDirectoryReturnsUsageErrorExitCode2() {
        RunResult result = runCli("benchmark",
            "--rules", resource("benchmark/rules.yaml").toString(), "does-not-exist");

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("Not a directory"));
    }

    @Test
    void allCasesIncompleteReturnsUsageErrorExitCode2() {
        RunResult result = runCli("benchmark",
            "--rules", resource("benchmark/rules.yaml").toString(),
            resource("benchmark-incomplete").toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("No valid benchmark cases found"));
    }

    @Test
    void incompleteCaseFailsDatasetValidationByDefaultEvenWhenOtherCasesPass() {
        RunResult result = runCli("benchmark",
            "--rules", resource("benchmark/rules.yaml").toString(),
            resource("benchmark-mixed").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.out().contains("Skipped: incomplete/case-1"));
        assertTrue(result.out().contains("Reason: expected-assessment.json missing"));
        assertTrue(result.out().contains("valid/case-1"));
        assertTrue(result.out().contains("PASS"));
        assertTrue(result.out().contains("Cases discovered: 2"));
        assertTrue(result.out().contains("Cases executed: 1"));
        assertTrue(result.out().contains("Cases skipped: 1"));
        assertTrue(result.err().contains("Dataset validation failed"));
    }

    @Test
    void skipIncompleteFlagRunsValidCasesAndIgnoresTheIncompleteOne() {
        RunResult result = runCli("benchmark",
            "--rules", resource("benchmark/rules.yaml").toString(),
            "--skip-incomplete",
            resource("benchmark-mixed").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("valid/case-1"));
        assertTrue(result.out().contains("Accuracy: 1/1 (100%)"));
        assertTrue(result.out().contains("Cases discovered: 2"));
        assertTrue(result.out().contains("Cases executed: 1"));
        assertTrue(result.out().contains("Cases skipped: 1"));
        assertFalse(result.out().contains("Skipped: incomplete/case-1"),
            "--skip-incomplete silently ignores incomplete cases rather than reporting them");
        assertFalse(result.err().contains("Dataset validation failed"));
    }
}
