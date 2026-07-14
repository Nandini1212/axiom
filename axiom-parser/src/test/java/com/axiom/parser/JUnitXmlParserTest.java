package com.axiom.parser;

import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JUnitXmlParserTest {

    private final JUnitXmlParser parser = new JUnitXmlParser();

    private InputStream fixture(String name) {
        InputStream stream = getClass().getResourceAsStream("/junit/" + name);
        assertNotNull(stream, "missing test fixture: " + name);
        return stream;
    }

    @Test
    void singleFailureIsParsed() {
        ParserResult result = parser.parse(fixture("single-failure.xml"));

        assertEquals(1, result.failures().size());
        assertTrue(result.warnings().isEmpty());

        FailureEvent event = result.failures().get(0);
        assertEquals("shouldReturnUser", event.testName());
        assertEquals("com.example.UserServiceTest", event.className());
        assertEquals("UserServiceTest", event.suiteName());
        assertEquals(SourceFormat.JUNIT, event.sourceFormat());
        assertEquals(FailureStatus.FAILED, event.status());
        assertEquals("Connection refused", event.message());
        assertEquals("java.net.ConnectException: Connection refused", event.stackTrace());
        assertEquals(340L, event.durationMillis());
        assertEquals(Instant.parse("2026-07-13T10:00:00Z"), event.occurredAt());
        assertNull(event.pipelineContext());
        assertTrue(event.metadata().isEmpty());
    }

    @Test
    void singleErrorIsParsed() {
        ParserResult result = parser.parse(fixture("single-error.xml"));

        assertEquals(1, result.failures().size());
        assertEquals(FailureStatus.ERROR, result.failures().get(0).status());
    }

    @Test
    void singleSkippedIsParsedWithNullStackTrace() {
        ParserResult result = parser.parse(fixture("single-skipped.xml"));

        FailureEvent event = result.failures().get(0);
        assertEquals(FailureStatus.SKIPPED, event.status());
        assertEquals("disabled", event.message());
        assertNull(event.stackTrace());
    }

    @Test
    void passedOnlyProducesNoFailureEvents() {
        ParserResult result = parser.parse(fixture("passed-only.xml"));

        assertTrue(result.failures().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void emptySuiteProducesNoFailureEvents() {
        ParserResult result = parser.parse(fixture("empty-suite.xml"));

        assertTrue(result.failures().isEmpty());
    }

    @Test
    void multipleFailuresInOneSuiteAreAllParsedInDocumentOrder() {
        ParserResult result = parser.parse(fixture("multiple-failures.xml"));

        assertEquals(2, result.failures().size());
        assertEquals("testA", result.failures().get(0).testName());
        assertEquals("testB", result.failures().get(1).testName());
    }

    @Test
    void siblingSuitesUnderTestsuitesRootAreAllParsed() {
        ParserResult result = parser.parse(fixture("multiple-sibling-suites.xml"));

        assertEquals(2, result.failures().size());
        assertEquals("SuiteA", result.failures().get(0).suiteName());
        assertEquals("SuiteB", result.failures().get(1).suiteName());
    }

    @Test
    void nestedTestsuiteUsesImmediateEnclosingSuiteName() {
        ParserResult result = parser.parse(fixture("nested-suites.xml"));

        assertEquals(2, result.failures().size());

        FailureEvent inner = result.failures().stream()
            .filter(e -> e.testName().equals("innerTest")).findFirst().orElseThrow();
        FailureEvent outer = result.failures().stream()
            .filter(e -> e.testName().equals("outerTest")).findFirst().orElseThrow();

        assertEquals("Inner", inner.suiteName());
        assertEquals("Outer", outer.suiteName());
    }

    @Test
    void missingOptionalAttributesResultInNullFields() {
        ParserResult result = parser.parse(fixture("missing-optional-attributes.xml"));

        FailureEvent event = result.failures().get(0);
        assertNull(event.message());
        assertNull(event.durationMillis());
        assertNull(event.occurredAt());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void bothFailureAndErrorPresentPrefersErrorAndWarns() {
        ParserResult result = parser.parse(fixture("both-failure-and-error.xml"));

        FailureEvent event = result.failures().get(0);
        assertEquals(FailureStatus.ERROR, event.status());
        assertEquals("error msg", event.message());

        assertEquals(1, result.warnings().size());
        assertEquals(WarningType.AMBIGUOUS_STATUS, result.warnings().get(0).type());
        assertEquals("ambiguousTest", result.warnings().get(0).testcaseName());
    }

    @Test
    void missingIdentifyingAttributesSkipsOnlyThatTestcase() {
        ParserResult result = parser.parse(fixture("missing-identifying-attributes.xml"));

        assertEquals(1, result.failures().size());
        assertEquals("validTest", result.failures().get(0).testName());

        assertEquals(1, result.warnings().size());
        assertEquals(WarningType.MISSING_ATTRIBUTE, result.warnings().get(0).type());
    }

    @Test
    void invalidTimestampProducesNullOccurredAtAndWarning() {
        ParserResult result = parser.parse(fixture("invalid-timestamp.xml"));

        assertNull(result.failures().get(0).occurredAt());
        assertEquals(1, result.warnings().size());
        assertEquals(WarningType.INVALID_TIMESTAMP, result.warnings().get(0).type());
    }

    @Test
    void malformedXmlThrowsParsingException() {
        assertThrows(ParsingException.class, () -> parser.parse(fixture("malformed.xml")));
    }

    @Test
    void xxeAttemptIsRejected() {
        // Any DOCTYPE at all is rejected outright, regardless of what the entity would resolve to.
        assertThrows(ParsingException.class, () -> parser.parse(fixture("xxe-attempt.xml")));
    }

    @Test
    void retriedTestsWithIdenticalContentGetDistinctIds() {
        ParserResult result = parser.parse(fixture("retry-duplicate.xml"));

        assertEquals(2, result.failures().size());
        assertNotEquals(result.failures().get(0).id(), result.failures().get(1).id());
    }

    @Test
    void parsingTheSameDocumentTwiceProducesIdenticalIds() {
        ParserResult first = parser.parse(fixture("single-failure.xml"));
        ParserResult second = parser.parse(fixture("single-failure.xml"));

        assertEquals(first.failures().get(0).id(), second.failures().get(0).id());
    }

    @Test
    void largeReportParsesCompletely() {
        StringBuilder xml = new StringBuilder("<testsuite name=\"Large\">");
        int count = 5000;
        for (int i = 0; i < count; i++) {
            xml.append("<testcase name=\"test").append(i).append("\" classname=\"com.example.Large\">")
                .append("<failure message=\"boom\">stack</failure></testcase>");
        }
        xml.append("</testsuite>");

        InputStream input = new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8));
        ParserResult result = parser.parse(input);

        assertEquals(count, result.failures().size());
    }

    @Test
    void doesNotCloseTheInputStream() {
        InputStream tracking = new ByteArrayInputStream(
            readAll(fixture("single-failure.xml"))) {
            @Override
            public void close() throws IOException {
                fail("Parser must not close the input stream");
            }
        };

        parser.parse(tracking);
    }

    private static byte[] readAll(InputStream input) {
        try {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void throwsWhenInputIsNull() {
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }
}
