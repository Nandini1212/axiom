package com.axiom.investigation.file.reader;

import com.axiom.correlation.adapter.HistoryAdaptationResult;
import com.axiom.correlation.adapter.HistoryFileAdapter;
import com.axiom.correlation.adapter.HistoryInput;
import com.axiom.correlation.adapter.HistoryWarning;
import com.axiom.correlation.model.HistoricalExecutionEvidence;
import com.axiom.correlation.model.TestIdentity;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.CollectionWarningType;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads {@code history.json} and delegates matching/branch-scoping/deduplication to the existing,
 * unchanged {@link HistoryFileAdapter} — this reader's only job is the file/JSON boundary.
 */
public final class HistoricalExecutionEvidenceReader {

    public HistoricalReadResult read(
            Path historyPath, TestIdentity testIdentity, Optional<String> currentBranch,
            String collectorId, Clock clock) {
        Objects.requireNonNull(historyPath, "historyPath is mandatory");
        Objects.requireNonNull(testIdentity, "testIdentity is mandatory");
        Objects.requireNonNull(currentBranch, "currentBranch is mandatory (use Optional.empty(), not null)");
        Objects.requireNonNull(collectorId, "collectorId is mandatory");
        Objects.requireNonNull(clock, "clock is mandatory");

        List<CollectionWarning> warnings = new ArrayList<>();
        Optional<HistoryInput> input =
            JsonFileReading.readOrWarn(historyPath, HistoryInput.class, collectorId, warnings);
        if (input.isEmpty()) {
            return new HistoricalReadResult(Optional.empty(), warnings);
        }

        HistoryAdaptationResult adapted = new HistoryFileAdapter().adapt(
            input.get(), testIdentity, currentBranch,
            "historical-" + testIdentity.canonicalName(), clock.instant());

        for (HistoryWarning historyWarning : adapted.warnings()) {
            warnings.add(new CollectionWarning(collectorId, CollectionWarningType.OPERATIONAL_FAILURE,
                "History: " + historyWarning.message()));
        }
        return new HistoricalReadResult(adapted.evidence(), warnings);
    }

    public record HistoricalReadResult(Optional<HistoricalExecutionEvidence> evidence, List<CollectionWarning> warnings) {

        public HistoricalReadResult {
            Objects.requireNonNull(evidence, "evidence is mandatory (use Optional.empty(), not null)");
            Objects.requireNonNull(warnings, "warnings is mandatory");
            warnings = List.copyOf(warnings);
        }
    }
}
