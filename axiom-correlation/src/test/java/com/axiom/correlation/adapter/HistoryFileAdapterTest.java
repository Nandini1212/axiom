package com.axiom.correlation.adapter;

import com.axiom.correlation.model.HistoricalExecutionEvidence;
import com.axiom.correlation.model.HistoricalOutcome;
import com.axiom.correlation.model.TestIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HistoryFileAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final TestIdentity CURRENT_TEST =
        new TestIdentity("com.example.PaymentServiceTest", "testCharge");
    private final HistoryFileAdapter adapter = new HistoryFileAdapter();

    private static HistoricalTestInput testInput(String className, String testName, HistoricalRunInput... runs) {
        return new HistoricalTestInput(className, testName, List.of(runs));
    }

    private static HistoricalRunInput run(String runId, Instant timestamp, HistoricalOutcome outcome) {
        return new HistoricalRunInput(runId, timestamp, outcome);
    }

    @Test
    void matchesTestByExactClassNameAndTestName() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        assertTrue(result.evidence().isPresent());
        assertEquals(1, result.evidence().get().runs().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void noMatchingTestProducesEmptyEvidenceNotAnError() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.OtherTest", "testSomethingElse",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        assertTrue(result.evidence().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void matchingIsExactNotFuzzy() {
        // Different case, must not match - TestIdentity's own matching is case-sensitive.
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.paymentservicetest", "testcharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void matchingBranchesAreUsable() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.of("main"), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.of("main"), "evidence-history", NOW);

        assertTrue(result.evidence().isPresent());
        assertEquals(Optional.of("main"), result.evidence().get().branch());
    }

    @Test
    void mismatchedBranchesAreUnusableNotNegativeEvidence() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.of("main"), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.of("feature/x"), "evidence-history", NOW);

        assertTrue(result.evidence().isEmpty());
        assertTrue(result.warnings().isEmpty(), "a branch mismatch is absence, not a warning-worthy problem");
    }

    @Test
    void unscopedHistoryIsUsableRegardlessOfCurrentBranch() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.of("feature/x"), "evidence-history", NOW);

        assertTrue(result.evidence().isPresent());
        assertTrue(result.evidence().get().branch().isEmpty());
    }

    @Test
    void historyBranchUsableWhenCurrentBranchUnknown() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.of("main"), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        assertTrue(result.evidence().isPresent());
    }

    @Test
    void duplicateRunIdKeepsFirstOccurrenceAndWarns() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1042", NOW, HistoricalOutcome.PASSED),
                run("build-1042", NOW.minusSeconds(60), HistoricalOutcome.FAILED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        HistoricalExecutionEvidence evidence = result.evidence().orElseThrow();
        assertEquals(1, evidence.runs().size());
        assertEquals(HistoricalOutcome.PASSED, evidence.runs().get(0).outcome());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("build-1042"));
    }

    @Test
    void producedEvidenceIsAlreadyNewestFirst() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge",
                run("build-1030", NOW.minusSeconds(7200), HistoricalOutcome.PASSED),
                run("build-1042", NOW, HistoricalOutcome.FAILED),
                run("build-1041", NOW.minusSeconds(3600), HistoricalOutcome.PASSED))));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        List<String> runIds = result.evidence().orElseThrow().runs().stream()
            .map(run -> run.runId())
            .toList();
        assertEquals(List.of("build-1042", "build-1041", "build-1030"), runIds);
    }

    @Test
    void emptyRunsForMatchedTestProducesEvidenceWithEmptyRuns() {
        HistoryInput input = new HistoryInput("1.0", NOW, Optional.empty(), List.of(
            testInput("com.example.PaymentServiceTest", "testCharge")));

        HistoryAdaptationResult result = adapter.adapt(
            input, CURRENT_TEST, Optional.empty(), "evidence-history", NOW);

        assertTrue(result.evidence().isPresent());
        assertTrue(result.evidence().get().runs().isEmpty());
    }
}
