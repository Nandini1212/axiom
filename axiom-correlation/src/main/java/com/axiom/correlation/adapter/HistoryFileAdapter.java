package com.axiom.correlation.adapter;

import com.axiom.correlation.model.HistoricalExecutionEvidence;
import com.axiom.correlation.model.HistoricalTestRun;
import com.axiom.correlation.model.TestIdentity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts an already-deserialized {@link HistoryInput} into {@link HistoricalExecutionEvidence}
 * for one specific test — matching, branch-scoping, and deduplication live here, not in the
 * domain model itself (same boundary {@code CorrelationEngine} holds for the other three evidence
 * sources: it never sees JSON or a file path). Takes {@code HistoryInput}, not a file path or raw
 * JSON — actually reading {@code history.json} and deserializing it (e.g. via Jackson) is deferred
 * to whenever CLI wiring for this module is built, same as {@code ChangeSetInput}/
 * {@code ExecutionInput} today; this module has no JSON-library dependency yet and doesn't need
 * one for this class.
 */
public final class HistoryFileAdapter {

    public HistoryAdaptationResult adapt(
            HistoryInput input,
            TestIdentity currentTest,
            Optional<String> currentBranch,
            String evidenceId,
            Instant observedAt) {
        Objects.requireNonNull(input, "input is mandatory");
        Objects.requireNonNull(currentTest, "currentTest is mandatory");
        Objects.requireNonNull(currentBranch, "currentBranch is mandatory (use Optional.empty(), not null)");
        Objects.requireNonNull(evidenceId, "evidenceId is mandatory");
        Objects.requireNonNull(observedAt, "observedAt is mandatory");

        Optional<HistoricalTestInput> matchingTest = input.tests().stream()
            .filter(test -> test.className().equals(currentTest.className())
                && test.testName().equals(currentTest.testName()))
            .findFirst();

        if (matchingTest.isEmpty() || isBranchMismatch(input.branch(), currentBranch)) {
            return new HistoryAdaptationResult(Optional.empty(), List.of());
        }

        List<HistoryWarning> warnings = new ArrayList<>();
        List<HistoricalTestRun> runs = deduplicate(matchingTest.get().runs(), warnings);

        HistoricalExecutionEvidence evidence = new HistoricalExecutionEvidence(
            evidenceId, observedAt, currentTest, input.branch(), runs);

        return new HistoryAdaptationResult(Optional.of(evidence), warnings);
    }

    /**
     * Branch mismatch is unusable evidence, not negative evidence (see
     * {@code docs/15-historical-execution-evidence-design.md} §7) — only fires when
     * <b>both</b> sides declare a branch and they differ. If either side doesn't know its branch,
     * that's not a mismatch, just missing context; the history stays usable (recorded as whatever
     * the file itself declares, unscoped from the current run's perspective when the current run
     * has no branch context of its own).
     */
    private static boolean isBranchMismatch(Optional<String> historyBranch, Optional<String> currentBranch) {
        return historyBranch.isPresent() && currentBranch.isPresent()
            && !historyBranch.get().equals(currentBranch.get());
    }

    /** Keeps the first occurrence of each runId; every later duplicate produces one warning. */
    private static List<HistoricalTestRun> deduplicate(List<HistoricalRunInput> runs, List<HistoryWarning> warnings) {
        Map<String, HistoricalRunInput> byRunId = new LinkedHashMap<>();
        for (HistoricalRunInput run : runs) {
            if (byRunId.containsKey(run.runId())) {
                warnings.add(HistoryWarning.duplicateRunId(run.runId()));
                continue;
            }
            byRunId.put(run.runId(), run);
        }
        return byRunId.values().stream()
            .map(run -> new HistoricalTestRun(run.runId(), run.timestamp(), run.outcome()))
            .toList();
    }
}
