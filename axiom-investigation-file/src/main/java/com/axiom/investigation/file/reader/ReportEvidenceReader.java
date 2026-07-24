package com.axiom.investigation.file.reader;

import com.axiom.analyzer.AnalysisResult;
import com.axiom.analyzer.AnalyzedFailure;
import com.axiom.analyzer.Analyzer;
import com.axiom.common.model.FailureEvent;
import com.axiom.correlation.model.FailureAnalysisInput;
import com.axiom.correlation.model.TestFailureEvidence;
import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.CollectionWarningType;
import com.axiom.parser.ParserWarning;
import com.axiom.parser.ParsingException;

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
 * Reads {@code report.xml} into exactly one {@link TestFailureEvidence} — reuses the existing,
 * unchanged {@link Analyzer} (parser + classifier) rather than reimplementing either.
 * <p>
 * <b>Multi-failure reports are not yet supported, not rejected as invalid.</b> A PR failing three
 * unrelated tests is legitimately still one engineering event; this reader just can't represent
 * that yet, because every existing {@code CorrelationRule} finds its test failure via
 * {@code evidence.stream().filter(TEST_FAILURE).findFirst()} — reasoning over several
 * simultaneous failures needs those rules (and {@code RootCauseAssessment}) redesigned first, an
 * explicitly open question since {@code 13-evidence-correlation-design.md} §18. Until that
 * redesign happens, a report with zero or more than one failure produces a warning naming the gap
 * rather than guessing which failure was meant — a capability boundary, not a domain rule that a
 * multi-failure report is somehow wrong. Revisit this reader once multi-failure investigations are
 * designed, not before.
 */
public final class ReportEvidenceReader {

    private final Analyzer analyzer;

    public ReportEvidenceReader(Analyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer is mandatory");
    }

    /**
     * {@link IOException} (missing/unreadable file) and {@link ParsingException} (malformed XML)
     * become a warning here — never a blanket {@code RuntimeException} catch, so an unrelated
     * programming error elsewhere in the classifier still propagates and fails fast.
     */
    public ReportReadResult read(Path reportPath, String collectorId, Clock clock) {
        Objects.requireNonNull(reportPath, "reportPath is mandatory");
        Objects.requireNonNull(collectorId, "collectorId is mandatory");
        Objects.requireNonNull(clock, "clock is mandatory");

        List<CollectionWarning> warnings = new ArrayList<>();
        AnalysisResult result;
        try (InputStream reportStream = Files.newInputStream(reportPath)) {
            result = analyzer.analyze(reportStream);
        } catch (IOException e) {
            warnings.add(warning(collectorId, "Failed to read report " + reportPath + ": " + e.getMessage()));
            return new ReportReadResult(Optional.empty(), Optional.empty(), warnings);
        } catch (ParsingException e) {
            warnings.add(warning(collectorId, "Failed to parse report " + reportPath + ": " + e.getMessage()));
            return new ReportReadResult(Optional.empty(), Optional.empty(), warnings);
        }

        for (ParserWarning parserWarning : result.parserWarnings()) {
            warnings.add(warning(collectorId, "Parser warning: " + parserWarning.detail()));
        }

        List<AnalyzedFailure> failures = result.analyses();
        if (failures.isEmpty()) {
            warnings.add(warning(collectorId, "Report " + reportPath + " contained no failures to investigate"));
            return new ReportReadResult(Optional.empty(), Optional.empty(), warnings);
        }
        if (failures.size() > 1) {
            warnings.add(warning(collectorId, "Report " + reportPath + " contained " + failures.size()
                + " failures; multi-failure investigations are not yet supported (would require"
                + " redesigning how CorrelationRule/RootCauseAssessment reason across several"
                + " failures, see 13-evidence-correlation-design.md §18) -- none investigated"));
            return new ReportReadResult(Optional.empty(), Optional.empty(), warnings);
        }

        AnalyzedFailure failure = failures.get(0);
        TestFailureEvidence evidence = TestFailureEvidence.from("test-failure-" + failure.event().id(),
            clock.instant(), new FailureAnalysisInput(failure.event(), failure.classification()));
        return new ReportReadResult(Optional.of(evidence), Optional.of(failure.event()), warnings);
    }

    private static CollectionWarning warning(String collectorId, String message) {
        return new CollectionWarning(collectorId, CollectionWarningType.OPERATIONAL_FAILURE, message);
    }

    /**
     * {@code failureEvent} is carried alongside {@code evidence} (not re-derivable from it) so a
     * caller building {@link com.axiom.correlation.model.TestIdentity} for history-matching
     * doesn't need to re-parse the report or unpack {@code TestFailureEvidence} to get it.
     */
    public record ReportReadResult(
            Optional<TestFailureEvidence> evidence,
            Optional<FailureEvent> failureEvent,
            List<CollectionWarning> warnings) {

        public ReportReadResult {
            Objects.requireNonNull(evidence, "evidence is mandatory (use Optional.empty(), not null)");
            Objects.requireNonNull(failureEvent, "failureEvent is mandatory (use Optional.empty(), not null)");
            Objects.requireNonNull(warnings, "warnings is mandatory");
            warnings = List.copyOf(warnings);
        }
    }
}
