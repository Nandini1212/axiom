package com.axiom.cli;

import com.axiom.analyzer.Analyzer;
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

class AxiomCliTest {

    private record RunResult(int exitCode, String out, String err) {
    }

    private static Path resource(String path) {
        URL url = AxiomCliTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static RunResult runCli(String... args) {
        return runCli(Map.of(), args);
    }

    private static RunResult runCli(Map<String, String> env, String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exitCode = AxiomCli.run(args, out, err, env);

        return new RunResult(
            exitCode,
            outBytes.toString(StandardCharsets.UTF_8),
            errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void wrongArgumentCountReturnsUsageErrorExitCode2() {
        RunResult result = runCli("only-one-arg");

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("Usage"));
    }

    @Test
    void matchedFailureIsReportedWithCategoryConfidenceAndRule() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/single-failure.xml").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Detected 1 failure(s)"));
        assertTrue(result.out().contains("INFRASTRUCTURE_FAILURE"));
        assertTrue(result.out().contains("0.95"));
        assertTrue(result.out().contains("connection-refused"));
        assertTrue(result.out().contains("Warnings: none"));
    }

    @Test
    void unmatchedFailureIsReportedAsUnknownWithNoRuleMatched() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/unmatched-failure.xml").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("UNKNOWN"));
        assertTrue(result.out().contains("(no rule matched)"));
    }

    @Test
    void passedOnlyReportProducesZeroFailuresAndExitZero() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/passed-only.xml").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Detected 0 failure(s)"));
        assertTrue(result.out().contains("Warnings: none"));
    }

    @Test
    void nonexistentReportFileReturnsExitCode1() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            "/nonexistent/report.xml");

        assertEquals(1, result.exitCode());
        assertFalse(result.err().isEmpty());
    }

    @Test
    void malformedRulesYamlReturnsExitCode1() {
        RunResult result = runCli(
            resource("rules/malformed-rules.yaml").toString(),
            resource("reports/single-failure.xml").toString());

        assertEquals(1, result.exitCode());
    }

    @Test
    void malformedReportXmlReturnsExitCode1() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/malformed.xml").toString());

        assertEquals(1, result.exitCode());
    }

    @Test
    void reportWithParserWarningShowsWarningInOutput() {
        RunResult result = runCli(
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/missing-identifying-attributes.xml").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Detected 1 failure(s)"));
        assertTrue(result.out().contains("Warnings:"));
        assertFalse(result.out().contains("Warnings: none"));
    }

    @Test
    void aiFlagWithoutApiKeyReturnsExitCode1WithClearMessage() {
        RunResult result = runCli(
            Map.of(),
            "--ai",
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/single-failure.xml").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.err().contains("AXIOM_LLM_API_KEY"));
    }

    @Test
    void aiFlagWithUnsupportedProviderReturnsExitCode1WithClearMessage() {
        RunResult result = runCli(
            Map.of("AXIOM_LLM_PROVIDER", "not-a-real-provider", "AXIOM_LLM_API_KEY", "test-key"),
            "--ai",
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/single-failure.xml").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.err().contains("not-a-real-provider"));
    }

    @Test
    void aiFlagWithInvalidTimeoutReturnsExitCode1WithClearMessage() {
        RunResult result = runCli(
            Map.of("AXIOM_LLM_API_KEY", "test-key", "AXIOM_LLM_TIMEOUT_SECONDS", "not-a-number"),
            "--ai",
            resource("rules/connection-refused.yaml").toString(),
            resource("reports/single-failure.xml").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.err().contains("AXIOM_LLM_TIMEOUT_SECONDS"));
    }

    @Test
    void aiFlagWithApiKeyConstructsAnalyzerWithoutMakingNetworkCall() {
        // Deliberately does not call .analyze() on the returned Analyzer — that would attempt a
        // real network call to Anthropic's API, which must never happen in unit tests.
        Analyzer analyzer = AxiomCli.createAnalyzer(
            resource("rules/connection-refused.yaml"),
            true,
            Map.of("AXIOM_LLM_API_KEY", "test-key"));

        assertNotNull(analyzer);
    }

    @Test
    void wrongArgumentCountWithAiFlagStillReturnsUsageErrorExitCode2() {
        RunResult result = runCli(Map.of(), "--ai", "only-one-arg");

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("Usage"));
    }
}
