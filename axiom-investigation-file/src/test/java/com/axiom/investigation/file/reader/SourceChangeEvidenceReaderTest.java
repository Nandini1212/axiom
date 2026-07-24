package com.axiom.investigation.file.reader;

import com.axiom.investigation.file.reader.SourceChangeEvidenceReader.SourceChangeReadResult;
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

class SourceChangeEvidenceReaderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-24T09:00:00Z"), ZoneOffset.UTC);

    private static Path resource(String path) {
        URL url = SourceChangeEvidenceReaderTest.class.getResource("/" + path);
        assertNotNull(url, "missing test resource: " + path);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void validFileProducesEvidence() {
        SourceChangeReadResult result = new SourceChangeEvidenceReader()
            .read(resource("correlation/changes.json"), "test-collector", FIXED_CLOCK);

        assertTrue(result.warnings().isEmpty());
        assertEquals("abc123", result.evidence().orElseThrow().commitSha());
    }

    @Test
    void malformedFileProducesAWarningNotAnException() {
        SourceChangeReadResult result = new SourceChangeEvidenceReader()
            .read(resource("correlation/malformed.json"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals(CollectionWarningType.OPERATIONAL_FAILURE, result.warnings().get(0).type());
        assertEquals("test-collector", result.warnings().get(0).collectorId());
    }

    @Test
    void missingFileProducesAWarningNotAnException() {
        SourceChangeReadResult result = new SourceChangeEvidenceReader()
            .read(Path.of("does-not-exist.json"), "test-collector", FIXED_CLOCK);

        assertTrue(result.evidence().isEmpty());
        assertEquals(1, result.warnings().size());
    }
}
