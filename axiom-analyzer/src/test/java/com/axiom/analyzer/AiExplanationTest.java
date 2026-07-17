package com.axiom.analyzer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiExplanationTest {

    @Test
    void constructsWithAllFieldsPresent() {
        AiExplanation explanation = new AiExplanation(
            "Summary text", "Root cause text", List.of("Step 1", "Step 2"), "Confidence text");

        assertEquals("Summary text", explanation.summary());
        assertEquals("Root cause text", explanation.rootCause());
        assertEquals(List.of("Step 1", "Step 2"), explanation.suggestedNextSteps());
        assertEquals("Confidence text", explanation.confidenceExplanation());
    }

    @Test
    void suggestedNextStepsMayBeEmpty() {
        AiExplanation explanation = new AiExplanation("Summary", "Root cause", List.of(), "Confidence");

        assertTrue(explanation.suggestedNextSteps().isEmpty());
    }

    @Test
    void throwsWhenSummaryIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            new AiExplanation("   ", "Root cause", List.of(), "Confidence"));
    }

    @Test
    void throwsWhenRootCauseIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            new AiExplanation("Summary", "", List.of(), "Confidence"));
    }

    @Test
    void throwsWhenConfidenceExplanationIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            new AiExplanation("Summary", "Root cause", List.of(), " "));
    }

    @Test
    void throwsWhenSuggestedNextStepsIsNull() {
        assertThrows(NullPointerException.class, () ->
            new AiExplanation("Summary", "Root cause", null, "Confidence"));
    }
}
