package com.axiom.investigation.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvestigationContextTest {

    @Test
    void triggerTypeIsMandatory() {
        assertThrows(NullPointerException.class, () -> new InvestigationContext(null, null));
    }

    @Test
    void pipelineContextMayBeNull() {
        InvestigationContext context = new InvestigationContext(TriggerType.MANUAL, null);
        assertNull(context.pipelineContext());
    }
}
