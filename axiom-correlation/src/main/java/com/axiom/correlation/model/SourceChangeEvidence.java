package com.axiom.correlation.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The domain evidence type built from a {@link ChangeSetInput} — distinct from that wire-format
 * DTO the same way {@code axiom-classifier}'s {@code PreparedRule} is distinct from
 * {@code RuleDefinition}. {@code affectedModules} from the original design sketch is deliberately
 * not included: nothing in this slice's signals or rules consumes it yet.
 */
public record SourceChangeEvidence(
        String evidenceId,
        Instant observedAt,
        String commitSha,
        List<String> changedFiles
) implements CorrelationEvidence {

    public SourceChangeEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId is mandatory");
        Objects.requireNonNull(observedAt, "observedAt is mandatory");
        Objects.requireNonNull(commitSha, "commitSha is mandatory");
        Objects.requireNonNull(changedFiles, "changedFiles is mandatory");
        changedFiles = List.copyOf(changedFiles);
    }

    @Override
    public EvidenceType type() {
        return EvidenceType.SOURCE_CHANGE;
    }

    public static SourceChangeEvidence from(String evidenceId, Instant observedAt, ChangeSetInput input) {
        Objects.requireNonNull(input, "input is mandatory");
        return new SourceChangeEvidence(evidenceId, observedAt, input.commitSha(), input.changedFiles());
    }
}
