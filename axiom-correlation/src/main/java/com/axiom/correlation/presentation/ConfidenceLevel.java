package com.axiom.correlation.presentation;

/**
 * A user-facing bucketing of a hypothesis's raw confidence score. The 0.70 boundary between
 * {@code MODERATE} and {@code LOW} deliberately matches
 * {@code AssessmentSelector.CONFIDENCE_THRESHOLD} — a hypothesis below that line could never have
 * been selected, so {@code LOW} here means the same thing as "did not clear the bar," not an
 * arbitrary separate scale.
 */
public enum ConfidenceLevel {
    HIGH,
    MODERATE,
    LOW;

    public static ConfidenceLevel forConfidence(double confidence) {
        if (confidence >= 0.85) {
            return HIGH;
        }
        if (confidence >= 0.70) {
            return MODERATE;
        }
        return LOW;
    }

    public String displayName() {
        return switch (this) {
            case HIGH -> "High";
            case MODERATE -> "Moderate";
            case LOW -> "Low";
        };
    }
}
