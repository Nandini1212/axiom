package com.axiom.investigation.file.reader;

import com.axiom.investigation.model.CollectionWarning;
import com.axiom.investigation.model.CollectionWarningType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Shared JSON-deserialization helper for the {@code *EvidenceReader} classes — the one place
 * Jackson is visible in this module, so a future non-file {@code EvidenceCollector} (GitHub,
 * GitLab, etc.) never needs to know it exists. {@link IOException} (missing file, malformed JSON —
 * {@code JsonProcessingException} is an {@code IOException}) becomes a warning; the collector
 * failure contract is enforced by each reader's caller, not duplicated here.
 */
final class JsonFileReading {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .addModule(new Jdk8Module())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .build();

    private JsonFileReading() {
    }

    static <T> Optional<T> readOrWarn(
            Path path, Class<T> type, String collectorId, List<CollectionWarning> warnings) {
        try {
            return Optional.of(MAPPER.readValue(path.toFile(), type));
        } catch (IOException e) {
            warnings.add(new CollectionWarning(collectorId, CollectionWarningType.OPERATIONAL_FAILURE,
                "Failed to read/parse " + path + ": " + e.getMessage()));
            return Optional.empty();
        }
    }
}
