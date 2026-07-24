package com.axiom.investigation.file.reader;

import com.axiom.correlation.model.ExecutionEvidence;
import com.axiom.correlation.model.ExecutionInput;
import com.axiom.investigation.model.CollectionWarning;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads {@code execution.json} into an {@link ExecutionEvidence}. */
public final class ExecutionEvidenceReader {

    public ExecutionReadResult read(Path executionPath, String collectorId, Clock clock) {
        Objects.requireNonNull(executionPath, "executionPath is mandatory");
        Objects.requireNonNull(collectorId, "collectorId is mandatory");
        Objects.requireNonNull(clock, "clock is mandatory");

        List<CollectionWarning> warnings = new ArrayList<>();
        Optional<ExecutionInput> input =
            JsonFileReading.readOrWarn(executionPath, ExecutionInput.class, collectorId, warnings);
        Optional<ExecutionEvidence> evidence = input.map(i ->
            ExecutionEvidence.from("execution-1", clock.instant(), i));
        return new ExecutionReadResult(evidence, warnings);
    }

    public record ExecutionReadResult(Optional<ExecutionEvidence> evidence, List<CollectionWarning> warnings) {

        public ExecutionReadResult {
            Objects.requireNonNull(evidence, "evidence is mandatory (use Optional.empty(), not null)");
            Objects.requireNonNull(warnings, "warnings is mandatory");
            warnings = List.copyOf(warnings);
        }
    }
}
