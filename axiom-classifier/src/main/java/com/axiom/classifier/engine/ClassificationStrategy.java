package com.axiom.classifier.engine;

import com.axiom.classifier.model.ClassificationResult;
import com.axiom.classifier.model.RuleMatch;

import java.util.List;

/**
 * Decides which {@link RuleMatch} (if any) becomes the final {@link ClassificationResult} for a
 * {@code FailureEvent}. Kept separate from {@link RuleEngine} deliberately: {@code RuleEngine}
 * answers "which rules matched," this answers "which matching rule wins" — a policy expected to
 * evolve (AI-assisted tie-breaking, hybrid strategies) without touching evaluation.
 */
public interface ClassificationStrategy {

    /**
     * @param matches every rule that matched, in any order — implementations must not assume
     *                 the list is pre-sorted by anything
     * @return the winning match's fields, or an {@code UNKNOWN}-category result with zero
     *         confidence if {@code matches} is empty
     */
    ClassificationResult classify(List<RuleMatch> matches);
}
