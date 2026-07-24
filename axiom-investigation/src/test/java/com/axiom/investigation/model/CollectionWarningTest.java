package com.axiom.investigation.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionWarningTest {

    @Test
    void fieldsAreMandatory() {
        assertThrows(NullPointerException.class,
            () -> new CollectionWarning(null, CollectionWarningType.OPERATIONAL_FAILURE, "message"));
        assertThrows(NullPointerException.class,
            () -> new CollectionWarning("collector-a", null, "message"));
        assertThrows(NullPointerException.class,
            () -> new CollectionWarning("collector-a", CollectionWarningType.OPERATIONAL_FAILURE, null));
    }

    @Test
    void duplicateEvidenceIdFactoryProducesAWarningNamingTheCollectorAndEvidenceId() {
        CollectionWarning warning = CollectionWarning.duplicateEvidenceId("collector-b", "ev-1");

        assertEquals("collector-b", warning.collectorId());
        assertEquals(CollectionWarningType.DUPLICATE_EVIDENCE_ID, warning.type());
        assertEquals("Duplicate evidenceId ev-1, first occurrence retained", warning.message());
    }
}
