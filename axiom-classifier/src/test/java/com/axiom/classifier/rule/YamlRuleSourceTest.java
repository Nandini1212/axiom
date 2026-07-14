package com.axiom.classifier.rule;

import com.axiom.classifier.model.FailureCategory;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlRuleSourceTest {

    private Path fixture(String name) {
        URL resource = getClass().getResource("/rules/" + name);
        assertNotNull(resource, "missing test fixture: " + name);
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void loadsValidMultiRuleFileInFileOrder() {
        List<RuleDefinition> rules = new YamlRuleSource(fixture("valid-rules.yaml")).loadRules();

        assertEquals(2, rules.size());

        RuleDefinition first = rules.get(0);
        assertEquals("connection-refused", first.id());
        assertEquals("Detects failures caused by unavailable services", first.description());
        assertEquals(100, first.priority());
        assertEquals(Boolean.TRUE, first.enabled());
        assertEquals(List.of(), first.match().all());
        assertEquals(1, first.match().any().size());
        assertEquals(RuleField.MESSAGE, first.match().any().get(0).field());
        assertEquals(Operator.CONTAINS, first.match().any().get(0).operator());
        assertEquals("Connection refused", first.match().any().get(0).value());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, first.classification().category());
        assertEquals(0.95, first.classification().confidence());
        assertEquals("Dependent service unavailable", first.evidence().message());

        RuleDefinition second = rules.get(1);
        assertEquals("flaky-timeout-in-integration-suite", second.id());
        assertNull(second.description());
        assertNull(second.priority());
        assertNull(second.enabled());
        assertEquals(List.of(), second.match().any());
        assertEquals(2, second.match().all().size());
        assertEquals(RuleField.SUITE_NAME, second.match().all().get(0).field());
        assertEquals(FailureCategory.FLAKY_TEST, second.classification().category());
        assertNull(second.evidence());
    }

    @Test
    void throwsWhenFileDoesNotExist() {
        Path missing = Paths.get("/nonexistent/rules.yaml");

        RuleSourceException ex = assertThrows(RuleSourceException.class, () ->
            new YamlRuleSource(missing).loadRules());
        assertTrue(ex.getMessage().contains(missing.toString()));
    }

    @Test
    void throwsWhenPathIsDirectory() {
        Path directory = fixture("valid-rules.yaml").getParent();

        assertThrows(RuleSourceException.class, () -> new YamlRuleSource(directory).loadRules());
    }

    @Test
    void throwsWhenYamlHasUnknownKey() {
        assertThrows(RuleSourceException.class, () ->
            new YamlRuleSource(fixture("unknown-key.yaml")).loadRules());
    }

    @Test
    void throwsWhenMatchGroupHasBothAnyAndAll() {
        assertThrows(RuleSourceException.class, () ->
            new YamlRuleSource(fixture("both-any-and-all.yaml")).loadRules());
    }

    @Test
    void throwsWhenMatchGroupHasNeitherAnyNorAll() {
        assertThrows(RuleSourceException.class, () ->
            new YamlRuleSource(fixture("neither-any-nor-all.yaml")).loadRules());
    }

    @Test
    void throwsWhenOperatorIsInvalid() {
        assertThrows(RuleSourceException.class, () ->
            new YamlRuleSource(fixture("invalid-operator.yaml")).loadRules());
    }

    @Test
    void constructorDoesNoIoAndDoesNotThrowForMissingFile() {
        // Constructing must not touch the filesystem; only loadRules() does.
        assertDoesNotThrow(() -> new YamlRuleSource(Paths.get("/nonexistent/rules.yaml")));
    }
}
