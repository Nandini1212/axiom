package com.axiom.classifier.rule;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads {@link RuleDefinition}s from a single YAML file shaped as {@code rules: [...]}.
 * <p>
 * Takes a single file, not a directory: directory-scanning (recursion, filename ordering,
 * cross-file concatenation order, empty-directory semantics) is real untested surface area
 * deferred until an actual multi-file rule set needs it. Constructing a {@code YamlRuleSource}
 * does no I/O; all file access happens in {@link #loadRules()}, so the same instance can be
 * re-invoked later without reconstruction.
 */
public final class YamlRuleSource implements RuleSource {

    private static final ObjectMapper MAPPER = YAMLMapper.builder()
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .build();

    private final Path ruleFile;

    public YamlRuleSource(Path ruleFile) {
        this.ruleFile = Objects.requireNonNull(ruleFile, "ruleFile is mandatory");
    }

    @Override
    public List<RuleDefinition> loadRules() {
        if (!Files.isRegularFile(ruleFile)) {
            throw new RuleSourceException(
                "Rule file does not exist or is not a regular file: " + ruleFile);
        }

        try {
            RuleFile parsed = MAPPER.readValue(ruleFile.toFile(), RuleFile.class);
            return parsed.rules() == null ? List.of() : List.copyOf(parsed.rules());
        } catch (IOException e) {
            throw new RuleSourceException("Failed to load rules from " + ruleFile, e);
        }
    }

    /** Binding-only shape for the YAML file's top-level {@code rules:} key. */
    private record RuleFile(List<RuleDefinition> rules) {
    }
}
