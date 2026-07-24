package com.axiom.investigation.engine;

import com.axiom.correlation.engine.CorrelationEngine;
import com.axiom.correlation.model.AssessmentDisposition;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.ExecutionEvidence;
import com.axiom.investigation.model.CollectedEvidence;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.CollectionWarningType;
import com.axiom.investigation.model.Investigation;
import com.axiom.investigation.model.InvestigationContext;
import com.axiom.investigation.model.TriggerType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct tests for {@link InvestigationRunner}'s orchestration contract — see
 * {@code 17-investigation-architecture.md} §3. Uses a real, rule-less {@link CorrelationEngine}
 * rather than a mock, matching this codebase's fixture-based testing convention throughout.
 */
class InvestigationRunnerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-24T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final InvestigationContext CONTEXT =
        new InvestigationContext(TriggerType.MANUAL, null);

    private static CorrelationEngine ruleLessEngine() {
        return new CorrelationEngine(List.of(), List.of());
    }

    private static ExecutionEvidence executionEvidence(String evidenceId) {
        return new ExecutionEvidence(evidenceId, FIXED_NOW, true, false, 0);
    }

    private record FakeCollector(String id, CollectedEvidence result) implements EvidenceCollector {
        @Override
        public CollectedEvidence collect(InvestigationContext context) {
            return result;
        }
    }

    @Test
    void usesInjectedIdGeneratorAndClockRatherThanCallingThemDirectly() {
        InvestigationRunner runner = new InvestigationRunner(
            List.of(), ruleLessEngine(), () -> "fixed-investigation-id", FIXED_CLOCK);

        Investigation investigation = runner.run(CONTEXT);

        assertEquals("fixed-investigation-id", investigation.investigationId());
        assertEquals(FIXED_NOW, investigation.startedAt());
    }

    @Test
    void emptyCollectorListStillRunsTheEngineWithNoEvidence() {
        InvestigationRunner runner = new InvestigationRunner(
            List.of(), ruleLessEngine(), () -> "id", FIXED_CLOCK);

        Investigation investigation = runner.run(CONTEXT);

        assertTrue(investigation.evidence().isEmpty());
        assertTrue(investigation.collectionWarnings().isEmpty());
        assertTrue(investigation.assessment().rankedHypotheses().isEmpty());
        assertEquals(AssessmentDisposition.NEEDS_INVESTIGATION, investigation.assessment().disposition());
        assertTrue(investigation.assessment().missingEvidence().contains("execution evidence not supplied"));
    }

    @Test
    void oneCollectorsEvidenceReachesTheEngine() {
        CollectedEvidence collected = new CollectedEvidence(List.of(executionEvidence("ev-1")), List.of());
        InvestigationRunner runner = new InvestigationRunner(
            List.of(new FakeCollector("execution-file-collector", collected)),
            ruleLessEngine(), () -> "id", FIXED_CLOCK);

        Investigation investigation = runner.run(CONTEXT);

        assertEquals(1, investigation.evidence().size());
        assertEquals("ev-1", investigation.evidence().get(0).evidenceId());
        assertFalse(investigation.assessment().missingEvidence().contains("execution evidence not supplied"));
        assertTrue(investigation.assessment().missingEvidence().contains("source-change evidence not supplied"));
    }

    @Test
    void multipleCollectorsEvidenceAndWarningsAreMergedInRegistrationOrder() {
        CollectionWarning warningOne = new CollectionWarning("collector-a",
            CollectionWarningType.OPERATIONAL_FAILURE, "first collector warning");
        CollectionWarning warningTwo = new CollectionWarning("collector-b",
            CollectionWarningType.OPERATIONAL_FAILURE, "second collector warning");
        FakeCollector first = new FakeCollector("collector-a",
            new CollectedEvidence(List.of(executionEvidence("ev-a")), List.of(warningOne)));
        FakeCollector second = new FakeCollector("collector-b",
            new CollectedEvidence(List.of(executionEvidence("ev-b")), List.of(warningTwo)));

        InvestigationRunner runner = new InvestigationRunner(
            List.of(first, second), ruleLessEngine(), () -> "id", FIXED_CLOCK);

        Investigation investigation = runner.run(CONTEXT);

        assertEquals(List.of("ev-a", "ev-b"),
            investigation.evidence().stream().map(CorrelationEvidence::evidenceId).toList());
        assertEquals(List.of(warningOne, warningTwo), investigation.collectionWarnings());
    }

    @Test
    void duplicateEvidenceIdAcrossCollectorsBecomesAWarningAndFirstOccurrenceIsRetained() {
        ExecutionEvidence firstEvidence = executionEvidence("shared-id");
        ExecutionEvidence secondEvidence = new ExecutionEvidence("shared-id", FIXED_NOW, false, true, 2);
        FakeCollector first = new FakeCollector("collector-a",
            new CollectedEvidence(List.of(firstEvidence), List.of()));
        FakeCollector second = new FakeCollector("collector-b",
            new CollectedEvidence(List.of(secondEvidence), List.of()));

        InvestigationRunner runner = new InvestigationRunner(
            List.of(first, second), ruleLessEngine(), () -> "id", FIXED_CLOCK);

        Investigation investigation = runner.run(CONTEXT);

        assertEquals(1, investigation.evidence().size());
        assertEquals(firstEvidence, investigation.evidence().get(0),
            "the first occurrence is retained only to be deterministic, not because it is more trustworthy");
        assertEquals(1, investigation.collectionWarnings().size());
        CollectionWarning warning = investigation.collectionWarnings().get(0);
        assertEquals("collector-b", warning.collectorId());
        assertEquals(CollectionWarningType.DUPLICATE_EVIDENCE_ID, warning.type());
    }

    @Test
    void unexpectedExceptionFromACollectorPropagatesRatherThanBecomingAWarning() {
        EvidenceCollector throwingCollector = new EvidenceCollector() {
            @Override
            public String id() {
                return "broken-collector";
            }

            @Override
            public CollectedEvidence collect(InvestigationContext context) {
                throw new IllegalStateException("programming error, not an operational failure");
            }
        };
        InvestigationRunner runner = new InvestigationRunner(
            List.of(throwingCollector), ruleLessEngine(), () -> "id", FIXED_CLOCK);

        assertThrows(IllegalStateException.class, () -> runner.run(CONTEXT));
    }

    @Test
    void repeatedRunWithFixedIdGeneratorAndClockIsIdentical() {
        CollectedEvidence collected = new CollectedEvidence(List.of(executionEvidence("ev-1")), List.of());
        InvestigationRunner runner = new InvestigationRunner(
            List.of(new FakeCollector("collector-a", collected)),
            ruleLessEngine(), () -> "fixed-id", FIXED_CLOCK);

        Investigation first = runner.run(CONTEXT);
        Investigation second = runner.run(CONTEXT);

        assertEquals(first, second);
    }
}
