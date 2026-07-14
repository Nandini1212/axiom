package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleDefinitionTest {

    private static final MatchGroup SOME_MATCH =
        new MatchGroup(List.of(new Condition(RuleField.MESSAGE, Operator.CONTAINS, "boom", null)), null);

    private static final ClassificationSpec SOME_CLASSIFICATION =
        new ClassificationSpec(FailureCategory.INFRASTRUCTURE_FAILURE, 0.9);

    @Test
    void constructsWithAllFieldsPresent() {
        EvidenceSpec evidence = new EvidenceSpec("Dependent service unavailable");

        RuleDefinition rule = new RuleDefinition(
            "connection-refused", "Detects unavailable services", 100, true,
            SOME_MATCH, SOME_CLASSIFICATION, evidence);

        assertEquals("connection-refused", rule.id());
        assertEquals("Detects unavailable services", rule.description());
        assertEquals(100, rule.priority());
        assertEquals(Boolean.TRUE, rule.enabled());
        assertEquals(SOME_MATCH, rule.match());
        assertEquals(SOME_CLASSIFICATION, rule.classification());
        assertEquals(evidence, rule.evidence());
    }

    @Test
    void constructsWithOnlyMandatoryFieldsPresent() {
        RuleDefinition rule = new RuleDefinition(
            "connection-refused", null, null, null, SOME_MATCH, SOME_CLASSIFICATION, null);

        assertNull(rule.description());
        assertNull(rule.priority());
        assertNull(rule.enabled());
        assertNull(rule.evidence());
    }

    @Test
    void throwsWhenIdIsNull() {
        assertThrows(NullPointerException.class, () ->
            new RuleDefinition(null, null, null, null, SOME_MATCH, SOME_CLASSIFICATION, null));
    }

    @Test
    void throwsWhenIdIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new RuleDefinition("   ", null, null, null, SOME_MATCH, SOME_CLASSIFICATION, null));
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    void throwsWhenMatchIsNull() {
        assertThrows(NullPointerException.class, () ->
            new RuleDefinition("connection-refused", null, null, null, null, SOME_CLASSIFICATION, null));
    }

    @Test
    void throwsWhenClassificationIsNull() {
        assertThrows(NullPointerException.class, () ->
            new RuleDefinition("connection-refused", null, null, null, SOME_MATCH, null, null));
    }
}
