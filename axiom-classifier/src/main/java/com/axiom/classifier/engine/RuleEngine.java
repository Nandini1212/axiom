package com.axiom.classifier.engine;

import com.axiom.classifier.model.RuleMatch;
import com.axiom.common.model.FailureEvent;

import java.util.List;

/**
 * Evaluates every rule it was constructed with against a given {@link FailureEvent}. The engine
 * never decides a winner among matches — that is {@code ClassificationStrategy}'s job, a
 * separate, later component. This engine only reports which rules matched and why.
 */
public interface RuleEngine {

    /**
     * @return one {@link RuleMatch} per rule that matched {@code event}, in the same order the
     *         engine's rules were supplied in (i.e. the rules' own priority-descending,
     *         id-ascending order — see {@code RuleProcessor}); empty if nothing matched
     */
    List<RuleMatch> evaluate(FailureEvent event);
}
