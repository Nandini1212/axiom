package com.axiom.investigation.file.reader;

import com.axiom.correlation.model.TestIdentity;
import com.axiom.investigation.file.reader.HistoricalExecutionEvidenceReader.HistoricalReadResult;
import com.axiom.investigation.model.CollectionWarningType;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalExecutionEvidenceReaderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-24T09:00:00Z"), ZoneOffset.UTC);
    private static final TestIdentity MATCHING_TEST = new TestIdentity("com.example.LoginTest", "shouldLogin");

    private static Path resource(String path) {
        URL url = HistoricalExecutionEvidenceReaderTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void matchingTestOnMatchingBranchProducesEvidenceNewestFirst() {
        HistoricalReadResult result = new HistoricalExecutionEvidenceReader().read(
            resource("correlation/history.json"), MATCHING_TEST, Optional.of("main"), "test-collector", FIXED_CLOCK);

        assertTrue(result.warnings().isEmpty());
        assertEquals(2, result.evidence().orElseThrow().runs().size());
    }

    @Test
    void branchMismatchProducesNoEvidenceAndNoWarning() {
        HistoricalReadResult result = new HistoricalExecutionEvidenceReader().read(
            resource("correlation/history.json"), MATCHING_TEST, Optional.of("feature-x"),
            "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty(), "branch mismatch is unusable evidence, not negative evidence");
        assertTrue(result.warnings().isEmpty(), "not an error -- legitimate absence of usable history");
    }

    @Test
    void unmatchedTestProducesNoEvidenceAndNoWarning() {
        TestIdentity unrelatedTest = new TestIdentity("com.example.Other", "somethingElse");
        HistoricalReadResult result = new HistoricalExecutionEvidenceReader().read(
            resource("correlation/history.json"), unrelatedTest, Optional.of("main"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void malformedFileProducesAWarningNotAnException() {
        HistoricalReadResult result = new HistoricalExecutionEvidenceReader().read(
            resource("correlation/malformed.json"), MATCHING_TEST, Optional.of("main"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, result.warnings().get(0).type());
    }
}
