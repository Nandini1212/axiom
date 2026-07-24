package com.axiom.investigation.engine;

import com.axiom.correlation.engine.CorrelationEngine;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.RootCauseAssessment;
import com.axiom.investigation.model.CollectedEvidence;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.Investigation;
import com.axiom.investigation.model.InvestigationContext;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Orchestrates {@link EvidenceCollector}s and the existing, unchanged {@link CorrelationEngine}
 * into one {@link Investigation} — see {@code 17-investigation-architecture.md} §3.
 * {@code CorrelationEngine} never becomes aware that an {@code Investigation} exists; its input
 * is still {@code List<CorrelationEvidence>}, its output is still {@code RootCauseAssessment}.
 * <p>
 * Collectors execute in the order supplied to the constructor (composition-root registration
 * order); evidence and warnings preserve that order. Ordering affects presentation only — it
 * must never affect hypothesis scores or the selected category.
 * <p>
 * Constructed with a {@link Supplier}&lt;String&gt; id generator and a {@link Clock} rather than
 * calling {@code UUID.randomUUID()}/{@code Instant.now()} directly, so tests can supply a fixed
 * id and a fixed clock — the same testability discipline as the rest of Axiom.
 */
public final class InvestigationRunner {

    private final List<EvidenceCollector> collectors;
    private final CorrelationEngine engine;
    private final Supplier<String> investigationIdGenerator;
    private final Clock clock;

    public InvestigationRunner(
            List<EvidenceCollector> collectors,
            CorrelationEngine engine,
            Supplier<String> investigationIdGenerator,
            Clock clock) {
        this.collectors = List.copyOf(collectors);
        this.engine = Objects.requireNonNull(engine, "engine is mandatory");
        this.investigationIdGenerator =
            Objects.requireNonNull(investigationIdGenerator, "investigationIdGenerator is mandatory");
        this.clock = Objects.requireNonNull(clock, "clock is mandatory");
    }

    public Investigation run(InvestigationContext context) {
        Objects.requireNonNull(context, "context is mandatory");

        List<CorrelationEvidence> evidence = new ArrayList<>();
        List<CollectionWarning> warnings = new ArrayList<>();
        Set<String> seenEvidenceIds = new HashSet<>();

        for (EvidenceCollector collector : collectors) {
            CollectedEvidence collected = collector.collect(context);
            warnings.addAll(collected.warnings());
            for (CorrelationEvidence item : collected.evidence()) {
                if (!seenEvidenceIds.add(item.evidenceId())) {
                    warnings.add(CollectionWarning.duplicateEvidenceId(collector.id(), item.evidenceId()));
                    continue;
                }
                evidence.add(item);
            }
        }

        RootCauseAssessment assessment = engine.assess(evidence);
        return new Investigation(
            investigationIdGenerator.get(), context, clock.instant(), evidence, warnings, assessment);
    }
}
