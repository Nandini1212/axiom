package com.axiom.analyzer;

import com.axiom.classifier.engine.ClassificationStrategy;
import com.axiom.classifier.engine.RuleEngine;
import com.axiom.classifier.model.RuleMatch;
import com.axiom.common.model.FailureEvent;
import com.axiom.parser.Parser;
import com.axiom.parser.ParserResult;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Named parallel to {@code DeterministicStrategy}: this is the no-AI implementation of
 * {@link Analyzer}, wiring an already-constructed {@link Parser}, {@link RuleEngine}, and
 * {@link ClassificationStrategy} together. Owns none of their construction (no rule loading, no
 * XML dispatch) — purely composition of three independently-tested pieces.
 */
public final class DeterministicAnalyzer implements Analyzer {

    private final Parser parser;
    private final RuleEngine ruleEngine;
    private final ClassificationStrategy strategy;

    public DeterministicAnalyzer(Parser parser, RuleEngine ruleEngine, ClassificationStrategy strategy) {
        this.parser = Objects.requireNonNull(parser, "parser is mandatory");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine is mandatory");
        this.strategy = Objects.requireNonNull(strategy, "strategy is mandatory");
    }

    @Override
    public AnalysisResult analyze(InputStream report) {
        Objects.requireNonNull(report, "report is mandatory");

        ParserResult parsed = parser.parse(report);

        List<AnalyzedFailure> analyses = new ArrayList<>();
        for (FailureEvent event : parsed.failures()) {
            List<RuleMatch> matches = ruleEngine.evaluate(event);
            analyses.add(new AnalyzedFailure(event, strategy.classify(matches)));
        }

        return new AnalysisResult(analyses, parsed.warnings());
    }
}
