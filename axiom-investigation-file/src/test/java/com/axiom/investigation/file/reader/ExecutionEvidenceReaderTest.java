package com.axiom.investigation.file.reader;

import com.axiom.investigation.file.reader.ExecutionEvidenceReader.ExecutionReadResult;
import com.axiom.investigation.model.CollectionWarningType;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionEvidenceReaderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-24T09:00:00Z"), ZoneOffset.UTC);

    private static Path resource(String path) {
        URL url = ExecutionEvidenceReaderTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void validFileProducesEvidence() {
        ExecutionReadResult result = new ExecutionEvidenceReader()
            .read(resource("correlation/execution.json"), "test-collector", FIXED_CLOCK);

        assertTrue(result.warnings().isEmpty());
        assertTrue(result.evidence().orElseThrow().retryAttempted());
        assertFalse(result.evidence().orElseThrow().retryPassed());
    }

    @Test
    void malformedFileProducesAWarningNotAnException() {
        ExecutionReadResult result = new ExecutionEvidenceReader()
            .read(resource("correlation/malformed.json"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, result.warnings().get(0).type());
    }
}
