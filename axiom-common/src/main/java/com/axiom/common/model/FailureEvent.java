package com.axiom.common.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A single normalized test failure, independent of the report format it came from
 * and of any downstream consumer (rule engine, GitHub integration, AI analyzer, or
 * future persistence layer).
 * <p>
 * <b>Mandatory fields</b> — a {@link FailureEvent} cannot be constructed without these,
 * because without them the record is not identifiable or classifiable at all:
 * <ul>
 *   <li>{@code id} — unique identifier for this failure occurrence; must be non-blank</li>
 *   <li>{@code status} — what kind of failure this was</li>
 *   <li>{@code sourceFormat} — which parser produced this event</li>
 *   <li>at least one of {@code testName}, {@code className}, or {@code suiteName} —
 *       some identifying label. A suite-level failure (e.g. container startup failure
 *       with no individual test method) may have only {@code suiteName}. A blank string
 *       does not count as present for this check.</li>
 * </ul>
 * <p>
 * <b>Optional fields</b> — genuinely absent in some source reports, so nullable rather
 * than defaulted to a misleading sentinel:
 * <ul>
 *   <li>{@code testName}, {@code className}, {@code suiteName} — see above; any two
 *       of the three may be absent as long as one is present</li>
 *   <li>{@code message}, {@code stackTrace} — a SKIPPED test typically has neither</li>
 *   <li>{@code durationMillis} — {@code null} means "unknown," not zero. A source
 *       report that omits duration is not the same as a test that ran in 0ms</li>
 *   <li>{@code occurredAt} — not every source report includes a timestamp</li>
 *   <li>{@code pipelineContext} — {@code null} when the failure has no CI/CD context
 *       at all (e.g. a local run); see {@link PipelineContext} for why there is no
 *       "empty" sentinel value</li>
 * </ul>
 * <p>
 * {@code metadata} always defaults to an immutable empty map rather than being
 * nullable, since callers commonly iterate over it without a null check. Null keys
 * or values within a supplied metadata map are rejected explicitly rather than left
 * to fail inside {@link Map#copyOf}.
 * <p>
 * A {@link PipelineContext} whose fields are all {@code null} carries no more
 * information than {@code pipelineContext} being {@code null} itself (see
 * {@link PipelineContext}), so it is normalized to {@code null} here rather than
 * being stored as a hollow, all-null instance.
 */
public record FailureEvent(
    String id,
    String testName,
    String className,
    String suiteName,
    SourceFormat sourceFormat,
    FailureStatus status,
    String message,
    String stackTrace,
    Long durationMillis,
    Instant occurredAt,
    PipelineContext pipelineContext,
    Map<String, String> metadata
) {

    public FailureEvent {
        Objects.requireNonNull(id, "id is mandatory");
        Objects.requireNonNull(status, "status is mandatory");
        Objects.requireNonNull(sourceFormat, "sourceFormat is mandatory");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        boolean hasTestName = testName != null && !testName.isBlank();
        boolean hasClassName = className != null && !className.isBlank();
        boolean hasSuiteName = suiteName != null && !suiteName.isBlank();
        if (!hasTestName && !hasClassName && !hasSuiteName) {
            throw new IllegalArgumentException(
                "At least one of testName, className, or suiteName is required");
        }

        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new IllegalArgumentException(
                        "metadata must not contain null keys or values");
                }
            }
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        if (pipelineContext != null
            && pipelineContext.provider() == null
            && pipelineContext.repository() == null
            && pipelineContext.workflow() == null
            && pipelineContext.job() == null
            && pipelineContext.branch() == null
            && pipelineContext.commitSha() == null
            && pipelineContext.pullRequestNumber() == null
            && pipelineContext.runId() == null) {
            pipelineContext = null;
        }
    }
}
