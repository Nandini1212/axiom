package com.axiom.common.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FailureEventTest {

    @Nested
    class ConstructionTest {

        @Test
        void constructsWithAllFieldsPresent() {
            PipelineContext ctx = new PipelineContext(
                "github", "org/repo", "ci.yml", "test", "main",
                "abc123", "42", "run-1");

            FailureEvent event = new FailureEvent(
                "evt-1",
                "shouldReturnUser",
                "com.example.UserServiceTest",
                "UserServiceSuite",
                SourceFormat.JUNIT,
                FailureStatus.FAILED,
                "expected <200> but was <500>",
                "java.lang.AssertionError: ...",
                150L,
                Instant.parse("2026-07-13T10:00:00Z"),
                ctx,
                Map.of("retryCount", "1"));

            assertEquals("evt-1", event.id());
            assertEquals("shouldReturnUser", event.testName());
            assertEquals("com.example.UserServiceTest", event.className());
            assertEquals("UserServiceSuite", event.suiteName());
            assertEquals(SourceFormat.JUNIT, event.sourceFormat());
            assertEquals(FailureStatus.FAILED, event.status());
            assertEquals("expected <200> but was <500>", event.message());
            assertEquals("java.lang.AssertionError: ...", event.stackTrace());
            assertEquals(150L, event.durationMillis());
            assertEquals(Instant.parse("2026-07-13T10:00:00Z"), event.occurredAt());
            assertEquals(ctx, event.pipelineContext());
            assertEquals("1", event.metadata().get("retryCount"));
        }

        @Test
        void constructsWithOnlyTestNameWhenClassNameAndSuiteNameAbsent() {
            FailureEvent event = new FailureEvent(
                "evt-4", "shouldReturnUser", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, null, null);

            assertEquals("shouldReturnUser", event.testName());
            assertNull(event.className());
            assertNull(event.suiteName());
        }

        @Test
        void constructsWithTestNameAndClassNameWhenSuiteNameAbsent() {
            FailureEvent event = new FailureEvent(
                "evt-5", "shouldReturnUser", "com.example.UserServiceTest", null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, null, null);

            assertEquals("shouldReturnUser", event.testName());
            assertEquals("com.example.UserServiceTest", event.className());
            assertNull(event.suiteName());
        }

        @Test
        void constructsWithOnlySuiteNameWhenTestNameAndClassNameAbsent() {
            // Suite-level failure: e.g. container failed to start, no individual test ran.
            FailureEvent event = new FailureEvent(
                "evt-2", null, null, "integration-suite",
                SourceFormat.JUNIT, FailureStatus.ERROR,
                "Container failed during startup", null,
                null, null, null, null);

            assertNull(event.testName());
            assertNull(event.className());
            assertEquals("integration-suite", event.suiteName());
        }

        @Test
        void constructsWithOnlyClassNameWhenTestNameAndSuiteNameAbsent() {
            FailureEvent event = new FailureEvent(
                "evt-3", null, "com.example.SomeTest", null,
                SourceFormat.TESTNG, FailureStatus.ERROR,
                null, null, null, null, null, null);

            assertEquals("com.example.SomeTest", event.className());
        }
    }

    @Nested
    class MandatoryFieldValidationTest {

        @Test
        void throwsWhenIdIsNull() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new FailureEvent(null, "test", null, null,
                    SourceFormat.JUNIT, FailureStatus.FAILED,
                    null, null, null, null, null, null));
            assertTrue(ex.getMessage().contains("id"));
        }

        @Test
        void throwsWhenStatusIsNull() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new FailureEvent("evt-1", "test", null, null,
                    SourceFormat.JUNIT, null,
                    null, null, null, null, null, null));
            assertTrue(ex.getMessage().contains("status"));
        }

        @Test
        void throwsWhenSourceFormatIsNull() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new FailureEvent("evt-1", "test", null, null,
                    null, FailureStatus.FAILED,
                    null, null, null, null, null, null));
            assertTrue(ex.getMessage().contains("sourceFormat"));
        }

        @Test
        void throwsWhenTestNameClassNameAndSuiteNameAllAbsent() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FailureEvent("evt-1", null, null, null,
                    SourceFormat.JUNIT, FailureStatus.FAILED,
                    "some message", null, null, null, null, null));
            assertTrue(ex.getMessage().contains("testName")
                || ex.getMessage().contains("At least one"));
        }

        @Test
        void throwsWhenIdIsBlank() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FailureEvent("   ", "test", null, null,
                    SourceFormat.JUNIT, FailureStatus.FAILED,
                    null, null, null, null, null, null));
            assertTrue(ex.getMessage().contains("id"));
        }

        @Test
        void throwsWhenTestNameClassNameAndSuiteNameAreAllBlank() {
            // Blank strings must not satisfy the "at least one identifying label" rule.
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FailureEvent("evt-1", "  ", "", " ",
                    SourceFormat.JUNIT, FailureStatus.FAILED,
                    null, null, null, null, null, null));
            assertTrue(ex.getMessage().contains("testName")
                || ex.getMessage().contains("At least one"));
        }

        @Test
        void throwsWhenMetadataContainsNullKey() {
            Map<String, String> badMetadata = new HashMap<>();
            badMetadata.put(null, "value");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FailureEvent("evt-1", "test", null, null,
                    SourceFormat.JUNIT, FailureStatus.FAILED,
                    null, null, null, null, null, badMetadata));
            assertTrue(ex.getMessage().contains("metadata"));
        }

        @Test
        void throwsWhenMetadataContainsNullValue() {
            Map<String, String> badMetadata = new HashMap<>();
            badMetadata.put("key", null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new FailureEvent("evt-1", "test", null, null,
                    SourceFormat.JUNIT, FailureStatus.FAILED,
                    null, null, null, null, null, badMetadata));
            assertTrue(ex.getMessage().contains("metadata"));
        }
    }

    @Nested
    class OptionalFieldSemanticsTest {

        @Test
        void durationMillisIsNullWhenNotProvided_notZero() {
            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.SKIPPED,
                null, null, null, null, null, null);

            // Must be able to distinguish "unknown duration" from "ran in 0ms".
            assertNull(event.durationMillis());
        }

        @Test
        void durationMillisPreservesExplicitZero() {
            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, 0L, null, null, null);

            assertEquals(0L, event.durationMillis());
        }

        @Test
        void pipelineContextIsNullWhenNotProvided_noEmptySentinel() {
            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, null, null);

            assertNull(event.pipelineContext());
        }

        @Test
        void occurredAtIsNullWhenNotProvided() {
            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, null, null);

            assertNull(event.occurredAt());
        }

        @Test
        void allNullPipelineContextIsNormalizedToNull() {
            // A PipelineContext with every field null carries no more information than
            // pipelineContext being null itself, so it must not be stored as-is.
            PipelineContext hollow = new PipelineContext(
                null, null, null, null, null, null, null, null);

            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, hollow, null);

            assertNull(event.pipelineContext());
        }

        @Test
        void pipelineContextWithOneNonNullFieldIsPreserved() {
            PipelineContext partial = new PipelineContext(
                "github", null, null, null, null, null, null, null);

            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, partial, null);

            assertEquals(partial, event.pipelineContext());
        }
    }

    @Nested
    class MetadataDefaultingTest {

        @Test
        void metadataDefaultsToEmptyImmutableMapWhenNull() {
            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, null, null);

            assertNotNull(event.metadata());
            assertTrue(event.metadata().isEmpty());
            assertThrows(UnsupportedOperationException.class,
                () -> event.metadata().put("k", "v"));
        }

        @Test
        void metadataIsDefensivelyCopiedAndImmutable() {
            Map<String, String> mutable = new HashMap<>();
            mutable.put("attempt", "1");

            FailureEvent event = new FailureEvent(
                "evt-1", "test", null, null,
                SourceFormat.JUNIT, FailureStatus.FAILED,
                null, null, null, null, null, mutable);

            mutable.put("attempt", "2"); // mutate the original after construction

            // Event's copy must be unaffected by the caller's later mutation.
            assertEquals("1", event.metadata().get("attempt"));
            assertThrows(UnsupportedOperationException.class,
                () -> event.metadata().put("k", "v"));
        }
    }
}
