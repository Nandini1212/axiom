package com.axiom.classifier.rule;

/**
 * The human-readable evidence message a {@link RuleDefinition} attaches to its classification
 * when it matches.
 */
public record EvidenceSpec(String message) {
}
