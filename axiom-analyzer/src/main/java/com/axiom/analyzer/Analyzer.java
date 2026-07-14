package com.axiom.analyzer;

import java.io.InputStream;

/**
 * Orchestrates a {@code Parser} and the classifier ({@code RuleEngine} +
 * {@code ClassificationStrategy}) into one call: report bytes in, a complete
 * {@link AnalysisResult} out. Deliberately AI-free — a future AI-enhanced implementation of this
 * same interface (mirroring {@code DeterministicStrategy}/{@code AIEnhancedStrategy}) adds
 * explanation on top without this interface, or any caller of it, needing to change.
 */
public interface Analyzer {

    AnalysisResult analyze(InputStream report);
}
