package com.axiom.correlation.model;

import com.axiom.common.model.FailureEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * A stable identity for matching "the same logical test" across executions — needed for
 * historical-evidence lookups, and to replace the ad hoc {@code className + "." + testName}
 * string-building that already existed independently in the presentation layer before this type
 * did. Deliberately excludes the failure message/stack trace (those describe one occurrence, not
 * the logical test) and, for v0.1, anything beyond className+testName: parameterized/dynamic
 * tests sharing the same className+testName, and renamed/moved tests, are not distinguished under
 * this scheme — a documented limitation, not solved here.
 * <p>
 * {@link #canonicalName()} is for matching/machine use, not display — it deliberately does not
 * back {@code toString()} or any renderer's display format, the same "domain type doesn't own
 * presentation formatting" principle applied to {@code RootCauseHypothesis}.
 */
public record TestIdentity(String className, String testName) {

    public TestIdentity {
        className = requireNonBlank(className, "className");
        testName = requireNonBlank(testName, "testName");
    }

    /** {@code #}, not {@code .} — distinguishes the class/method boundary more clearly. */
    public String canonicalName() {
        return className + "#" + testName;
    }

    /**
     * Empty when {@code event} lacks either a className or a testName — a suite-level failure
     * (only {@code suiteName} present) has no logical *test* identity to match history against at
     * all. Returning empty is the honest answer; substituting {@code suiteName} as a stand-in
     * className would misrepresent identity, not preserve it.
     */
    public static Optional<TestIdentity> from(FailureEvent event) {
        Objects.requireNonNull(event, "event is mandatory");
        String className = event.className();
        String testName = event.testName();
        if (className == null || className.isBlank() || testName == null || testName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new TestIdentity(className, testName));
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is mandatory");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
