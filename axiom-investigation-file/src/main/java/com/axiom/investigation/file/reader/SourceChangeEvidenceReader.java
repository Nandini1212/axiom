package com.axiom.investigation.file.reader;

import com.axiom.correlation.model.ChangeSetInput;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.investigation.model.CollectionWarning;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads {@code changes.json} into a {@link SourceChangeEvidence}. */
public final class SourceChangeEvidenceReader {

    public SourceChangeReadResult read(Path changesPath, String collectorId, Clock clock) {
        Objects.requireNonNull(changesPath, "changesPath is mandatory");
        Objects.requireNonNull(collectorId, "collectorId is mandatory");
        Objects.requireNonNull(clock, "clock is mandatory");

        List<CollectionWarning> warnings = new ArrayList<>();
        Optional<ChangeSetInput> input = JsonFileReading.readOrWarn(changesPath, ChangeSetInput.class, collectorId, warnings);
        Optional<SourceChangeEvidence> evidence = input.map(i ->
            SourceChangeEvidence.from("source-change-" + i.commitSha(), clock.instant(), i));
        return new SourceChangeReadResult(evidence, warnings);
    }

    public record SourceChangeReadResult(Optional<SourceChangeEvidence> evidence, List<CollectionWarning> warnings) {

        public SourceChangeReadResult {
            Objects.requireNonNull(evidence, "evidence is mandatory (use Optional.empty(), not null)");
            Objects.requireNonNull(warnings, "warnings is mandatory");
            warnings = List.copyOf(warnings);
        }
    }
}
