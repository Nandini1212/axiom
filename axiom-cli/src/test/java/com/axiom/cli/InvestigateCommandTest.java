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
 * End-to-end tests for {@code axiom investigate}, exercising the real Investigation pipeline
 * (InvestigationRunner -> FileEvidenceCollector -> CorrelationEngine) via {@link AxiomCli#run}.
 */
class InvestigateCommandTest {

    private record RunResult(int exitCode, String out, String err) {
    }

    private static Path resource(String path) {
        URL url = InvestigateCommandTest.class.getResource("/" + path);
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
    void reportOnlyProducesAnAssessmentWithNoCollectionWarnings() {
        RunResult result = runCli("investigate",
            "--rules", resource("rules/connection-refused.yaml").toString(),
            "--report", resource("reports/single-failure.xml").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("shouldLogin"));
        assertTrue(result.out().contains("Collection warnings: none"));
    }

    @Test
    void allFourSourcesPresentRunsTheFullPipeline() {
        RunResult result = runCli("investigate",
            "--rules", resource("rules/connection-refused.yaml").toString(),
            "--report", resource("reports/single-failure.xml").toString(),
            "--changes", resource("correlation/changes.json").toString(),
            "--execution", resource("correlation/execution.json").toString(),
            "--history", resource("correlation/history.json").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("shouldLogin"));
        assertTrue(result.out().contains("Collection warnings: none"));
    }

    @Test
    void missingRulesFlagReturnsUsageErrorExitCode2() {
        RunResult result = runCli("investigate",
            "--report", resource("reports/single-failure.xml").toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("--rules"));
    }

    @Test
    void missingReportFlagReturnsUsageErrorExitCode2() {
        RunResult result = runCli("investigate",
            "--rules", resource("rules/connection-refused.yaml").toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("--report"));
    }

    @Test
    void unknownFlagReturnsUsageErrorExitCode2() {
        RunResult result = runCli("investigate", "--bogus", "value");

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("Unknown flag"));
    }

    @Test
    void malformedRulesYamlReturnsExitCode1() {
        RunResult result = runCli("investigate",
            "--rules", resource("rules/malformed-rules.yaml").toString(),
            "--report", resource("reports/single-failure.xml").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.err().contains("Error"));
    }

    @Test
    void missingOptionalFileSurfacesAsACollectionWarningNotAnExitFailure() {
        RunResult result = runCli("investigate",
            "--rules", resource("rules/connection-refused.yaml").toString(),
            "--report", resource("reports/single-failure.xml").toString(),
            "--changes", "does-not-exist.json");

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Collection warnings:"));
        assertFalse(result.out().contains("Collection warnings: none"));
    }

    @Test
    void classicCommandIsUnaffectedByTheNewSubcommand() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/single-failure.xml").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Detected 1 failure(s)"));
    }
}
