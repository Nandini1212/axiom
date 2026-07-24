package com.axiom.cli.investigation;

import com.axiom.analyzer.AnalysisResult;
import com.axiom.analyzer.AnalyzedFailure;
import com.axiom.analyzer.Analyzer;
import com.axiom.common.model.PipelineContext;
import com.axiom.correlation.adapter.HistoryAdaptationResult;
import com.axiom.correlation.adapter.HistoryFileAdapter;
import com.axiom.correlation.adapter.HistoryInput;
import com.axiom.correlation.adapter.HistoryWarning;
import com.axiom.correlation.model.ChangeSetInput;
import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.ExecutionEvidence;
import com.axiom.correlation.model.ExecutionInput;
import com.axiom.correlation.model.FailureAnalysisInput;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.correlation.model.TestIdentity;
import com.axiom.investigation.engine.EvidenceCollector;
import com.axiom.investigation.model.CollectedEvidence;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.CollectionWarningType;
import com.axiom.investigation.model.InvestigationContext;
import com.axiom.parser.ParserWarning;
import com.axiom.parser.ParsingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The first (and today, only) {@link EvidenceCollector} implementation — wraps
 * {@code report.xml}/{@code changes.json}/{@code execution.json}/{@code history.json} behind the
 * generic collector shape, per {@code 17-investigation-architecture.md} §5's "orchestrate, don't
 * rewrite" principle: {@link Analyzer} (parsing + classification), {@link SourceChangeEvidence}/
 * {@link ExecutionEvidence}, and {@link HistoryFileAdapter} all do exactly what they already do;
 * only the interface wrapping them is new. This is also where the JSON deserialization for the
 * three wire-format inputs finally happens — deliberately deferred everywhere else in
 * {@code axiom-correlation} ("future CLI wiring," per {@code ChangeSetInput}/{@code ExecutionInput}/
 * {@code HistoryInput}'s own javadoc) until this collector.
 * <p>
 * {@code changesPath}/{@code executionPath}/{@code historyPath} are each optional — a missing one
 * simply means that evidence source contributes nothing (visible later via
 * {@code RootCauseAssessment.missingEvidence}), not an error. {@code reportPath} is mandatory:
 * an Investigation is scoped to exactly one engineering event
 * ({@code 16-investigation-domain-model.md} §3), so a report containing zero or more than one
 * failure is an operational problem (a warning, per the collector failure contract in
 * {@code 17-investigation-architecture.md} §3) rather than a guess at which failure was meant.
 */
public final class FileEvidenceCollector implements EvidenceCollector {

    public static final String COLLECTOR_ID = "file-evidence-collector";

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .addModule(new Jdk8Module())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .build();

    private final Analyzer analyzer;
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
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer is mandatory");
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

        Optional<AnalyzedFailure> targetFailure = collectTestFailure(evidence, warnings);
        collectSourceChange(evidence, warnings);
        collectExecution(evidence, warnings);
        targetFailure.ifPresent(failure -> collectHistory(failure, context, evidence, warnings));

        return new CollectedEvidence(evidence, warnings);
    }

    /**
     * Expected operational failures (missing/unreadable file, malformed XML) become a warning
     * here — {@link IOException} and {@link ParsingException} specifically, never a blanket
     * {@code RuntimeException} catch, so an unrelated programming error elsewhere in the
     * classifier still propagates and fails fast, per the collector failure contract.
     */
    private Optional<AnalyzedFailure> collectTestFailure(
            List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {
        AnalysisResult result;
        try (InputStream reportStream = Files.newInputStream(reportPath)) {
            result = analyzer.analyze(reportStream);
        } catch (IOException e) {
            warnings.add(operationalFailure("Failed to read report " + reportPath + ": " + e.getMessage()));
            return Optional.empty();
        } catch (ParsingException e) {
            warnings.add(operationalFailure("Failed to parse report " + reportPath + ": " + e.getMessage()));
            return Optional.empty();
        }

        for (ParserWarning parserWarning : result.parserWarnings()) {
            warnings.add(operationalFailure("Parser warning: " + parserWarning.detail()));
        }

        List<AnalyzedFailure> failures = result.analyses();
        if (failures.isEmpty()) {
            warnings.add(operationalFailure("Report " + reportPath + " contained no failures to investigate"));
            return Optional.empty();
        }
        if (failures.size() > 1) {
            warnings.add(operationalFailure("Report " + reportPath + " contained " + failures.size()
                + " failures; an Investigation analyzes exactly one engineering event"
                + " (16-investigation-domain-model.md §3) -- none investigated"));
            return Optional.empty();
        }

        AnalyzedFailure failure = failures.get(0);
        evidence.add(TestFailureEvidence.from("test-failure-" + failure.event().id(), clock.instant(),
            new FailureAnalysisInput(failure.event(), failure.classification())));
        return Optional.of(failure);
    }

    private void collectSourceChange(List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {
        if (changesPath.isEmpty()) {
            return;
        }
        readJson(changesPath.get(), ChangeSetInput.class, warnings).ifPresent(input ->
            evidence.add(SourceChangeEvidence.from("source-change-" + input.commitSha(), clock.instant(), input)));
    }

    private void collectExecution(List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {
        if (executionPath.isEmpty()) {
            return;
        }
        readJson(executionPath.get(), ExecutionInput.class, warnings).ifPresent(input ->
            evidence.add(ExecutionEvidence.from("execution-1", clock.instant(), input)));
    }

    private void collectHistory(
            AnalyzedFailure failure, InvestigationContext context,
            List<CorrelationEvidence> evidence, List<CollectionWarning> warnings) {
        if (historyPath.isEmpty()) {
            return;
        }

        Optional<TestIdentity> testIdentity = TestIdentity.from(failure.event());
        if (testIdentity.isEmpty()) {
            warnings.add(operationalFailure(
                "Cannot match history.json: failure has no test identity (suite-level failure only)"));
            return;
        }

        Optional<HistoryInput> historyInput = readJson(historyPath.get(), HistoryInput.class, warnings);
        if (historyInput.isEmpty()) {
            return;
        }

        PipelineContext pipelineContext = context.pipelineContext();
        Optional<String> currentBranch = pipelineContext == null
            ? Optional.empty() : Optional.ofNullable(pipelineContext.branch());

        HistoryAdaptationResult adapted = new HistoryFileAdapter().adapt(
            historyInput.get(), testIdentity.get(), currentBranch,
            "historical-" + testIdentity.get().canonicalName(), clock.instant());

        adapted.evidence().ifPresent(evidence::add);
        for (HistoryWarning historyWarning : adapted.warnings()) {
            warnings.add(operationalFailure("History: " + historyWarning.message()));
        }
    }

    /** Also catches malformed JSON — {@code JsonProcessingException} is an {@link IOException}. */
    private <T> Optional<T> readJson(Path path, Class<T> type, List<CollectionWarning> warnings) {
        try {
            return Optional.of(JSON_MAPPER.readValue(path.toFile(), type));
        } catch (IOException e) {
            warnings.add(operationalFailure("Failed to read/parse " + path + ": " + e.getMessage()));
            return Optional.empty();
        }
    }

    private CollectionWarning operationalFailure(String message) {
        return new CollectionWarning(COLLECTOR_ID, CollectionWarningType.OPERATIONAL_FAILURE, message);
    }
}
