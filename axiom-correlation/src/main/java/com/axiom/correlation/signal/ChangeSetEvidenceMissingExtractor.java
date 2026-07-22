package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;

import java.util.List;

/**
 * Present when no {@code SourceChangeEvidence} was supplied at all — distinct from a
 * {@code SourceChangeEvidence} being present but showing no relevant changed files.
 */
public final class ChangeSetEvidenceMissingExtractor implements SignalExtractor {

    @Override
    public List<Signal> extract(List<CorrelationEvidence> evidence) {
        boolean missing = evidence.stream().noneMatch(e -> e.type() == EvidenceType.SOURCE_CHANGE);
        return List.of(new Signal(SignalType.CHANGE_SET_EVIDENCE_MISSING, missing, List.of()));
    }
}
