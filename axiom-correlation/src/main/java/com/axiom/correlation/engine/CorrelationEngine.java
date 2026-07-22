package com.axiom.correlation.engine;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.correlation.signal.Signal;
import com.axiom.correlation.signal.SignalExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Constructed with its {@link SignalExtractor}s and {@link CorrelationRule}s — rules/extractors
 * are configuration, loaded once; only the evidence varies per call. Evaluates every registered
 * rule and never picks a winner itself; that's {@link AssessmentSelector}'s job, the same split
 * {@code RuleEngine}/{@code ClassificationStrategy} already have in {@code axiom-classifier}.
 * <p>
 * Takes normalized {@code List<CorrelationEvidence>} only — never a file path or raw JSON.
 * Building that evidence from {@code ChangeSetInput}/{@code ExecutionInput}/
 * {@code FailureAnalysisInput} happens strictly before this point, one layer up.
 */
public final class CorrelationEngine {

    private final List<SignalExtractor> extractors;
    private final List<CorrelationRule> rules;

    public CorrelationEngine(List<SignalExtractor> extractors, List<CorrelationRule> rules) {
        this.extractors = List.copyOf(extractors);
        this.rules = List.copyOf(rules);
    }

    public RootCauseAssessment assess(List<CorrelationEvidence> evidence) {
        Objects.requireNonNull(evidence, "evidence is mandatory");

        List<Signal> signals = extractors.stream()
            .flatMap(extractor -> extractor.extract(evidence).stream())
            .toList();
        CorrelationContext context = new CorrelationContext(signals, evidence);

        List<ScoredEvaluation> scored = new ArrayList<>();
        for (CorrelationRule rule : rules) {
            Optional<RuleEvaluation> evaluation = rule.evaluate(context);
            evaluation.ifPresent(ruleEvaluation -> scored.add(new ScoredEvaluation(
                rule.id(), ruleEvaluation, HypothesisScorer.score(ruleEvaluation.contributions()))));
        }

        return AssessmentSelector.select(scored, missingEvidence(evidence));
    }

    private static List<String> missingEvidence(List<CorrelationEvidence> evidence) {
        List<String> missing = new ArrayList<>();
        if (evidence.stream().noneMatch(e -> e.type() == EvidenceType.SOURCE_CHANGE)) {
            missing.add("source-change evidence not supplied");
        }
        if (evidence.stream().noneMatch(e -> e.type() == EvidenceType.EXECUTION)) {
            missing.add("execution evidence not supplied");
        }
        return missing;
    }
}
