package com.axiom.investigation.file;

import com.axiom.analyzer.Analyzer;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.PipelineContext;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.TestIdentity;
import com.axiom.investigation.engine.EvidenceCollector;
import com.axiom.investigation.file.reader.ExecutionEvidenceReader;
import com.axiom.investigation.file.reader.ExecutionEvidenceReader.ExecutionReadResult;
import com.axiom.investigation.file.reader.HistoricalExecutionEvidenceReader;
import com.axiom.investigation.file.reader.HistoricalExecutionEvidenceReader.HistoricalReadResult;
import com.axiom.investigation.file.reader.ReportEvidenceReader;
import com.axiom.investigation.file.reader.ReportEvidenceReader.ReportReadResult;
import com.axiom.investigation.file.reader.SourceChangeEvidenceReader;
import com.axiom.investigation.file.reader.SourceChangeEvidenceReader.SourceChangeReadResult;
import com.axiom.investigation.model.CollectedEvidence;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.CollectionWarningType;
import com.axiom.investigation.model.InvestigationContext;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The first (and today, only) {@link EvidenceCollector} implementation — a thin orchestrator over
 * four single-purpose readers (one per evidence source), each of which wraps an existing,
 * unchanged component ({@link Analyzer}, {@code SourceChangeEvidence.from}, {@code
 * ExecutionEvidence.from}, {@code HistoryFileAdapter}). This class's only job is calling each
 * reader that applies and merging the results — parsing, classification, JSON deserialization, and
 * history matching all live in the readers, not here.
 * <p>
 * {@code changesPath}/{@code executionPath}/{@code historyPath} are each optional — a missing one
 * simply means that evidence source contributes nothing (visible later via
 * {@code RootCauseAssessment.missingEvidence}), not an error. {@code reportPath} is mandatory.
 */
public final class FileEvidenceCollector implements EvidenceCollector {

    public static final String COLLECTOR_ID = "file-evidence-collector";

    private final ReportEvidenceReader reportReader;
    private final SourceChangeEvidenceReader sourceChangeReader;
    private final ExecutionEvidenceReader executionReader;
    private final HistoricalExecutionEvidenceReader historyReader;

    private final Path reportPath;
    private final Optional<Path> changesPath;
    private final Optional<Path> executionPath;
    private final Optional<Path> historyPath;
    private final Clock clock;

    public FileEvidenceCollector(
            Analyzer analyzer,
            Path reportPath,
            Optional<Path> changesPath,
            Optional<Path> executionPath,
            Optional<Path> historyPath,
            Clock clock) {
        this.reportReader = new ReportEvidenceReader(analyzer);
        this.sourceChangeReader = new SourceChangeEvidenceReader();
        this.executionReader = new ExecutionEvidenceReader();
        this.historyReader = new HistoricalExecutionEvidenceReader();
        this.reportPath = Objects.requireNonNull(reportPath, "reportPath is mandatory");
        this.changesPath = Objects.requireNonNull(changesPath,
            "changesPath is mandatory (use Optional.empty(), not null)");
        this.executionPath = Objects.requireNonNull(executionPath,
            "executionPath is mandatory (use Optional.empty(), not null)");
        this.historyPath = Objects.requireNonNull(historyPath,
            "historyPath is mandatory (use Optional.empty(), not null)");
        this.clock = Objects.requireNonNull(clock, "clock is mandatory");
    }

    @Override
    public String id() {
        return COLLECTOR_ID;
    }

    @Override
    public CollectedEvidence collect(InvestigationContext context) {
        Objects.requireNonNull(context, "context is mandatory");

        List<CorrelationEvidence> evidence = new ArrayList<>();
        List<CollectionWarning> warnings = new ArrayList<>();

        ReportReadResult report = reportReader.read(reportPath, COLLECTOR_ID, clock);
        report.evidence().ifPresent(evidence::add);
        warnings.addAll(report.warnings());

        if (changesPath.isPresent()) {
            SourceChangeReadResult result = sourceChangeReader.read(changesPath.get(), COLLECTOR_ID, clock);
            result.evidence().ifPresent(evidence::add);
            warnings.addAll(result.warnings());
        }

        if (executionPath.isPresent()) {
            ExecutionReadResult result = executionReader.read(executionPath.get(), COLLECTOR_ID, clock);
            result.evidence().ifPresent(evidence::add);
            warnings.addAll(result.warnings());
        }

        if (historyPath.isPresent() && report.failureEvent().isPresent()) {
            collectHistory(report.failureEvent().get(), context, evidence, warnings);
        }

        return new CollectedEvidence(evidence, warnings);
    }

    private void collectHistory(
            FailureEvent failureEvent, InvestigationContext context,
            List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {
        Optional<TestIdentity> testIdentity = TestIdentity.from(failureEvent);
        if (testIdentity.isEmpty()) {
            warnings.add(new CollectionWarning(COLLECTOR_ID, CollectionWarningType.OPERATIONAL_FAILURE,
                "Cannot match history.json: failure has no test identity (suite-level failure only)"));
            return;
        }

        PipelineContext pipelineContext = context.pipelineContext();
        Optional<String> currentBranch = pipelineContext == null
            ? Optional.empty() : Optional.ofNullable(pipelineContext.branch());

        HistoricalReadResult result = historyReader.read(
            historyPath.get(), testIdentity.get(), currentBranch, COLLECTOR_ID, clock);
        result.evidence().ifPresent(evidence::add);
        warnings.addAll(result.warnings());
    }
}
