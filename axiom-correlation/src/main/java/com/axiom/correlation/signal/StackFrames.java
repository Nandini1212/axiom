package com.axiom.correlation.signal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared stack-trace parsing used by {@link StackFrameMatchesChangedFileExtractor} and
 * {@link TopFrameIsTestCodeExtractor}. A deliberately simple heuristic for v0.1 — no bytecode
 * inspection, no build-tool source-root awareness, just the class names a standard
 * {@code Throwable.printStackTrace()} line contains.
 */
final class StackFrames {

    private static final Pattern FRAME_PATTERN = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\(");

    /** Fully-qualified class names, one per stack frame, in top-to-bottom order. */
    static List<String> classNames(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return List.of();
        }
        List<String> classNames = new ArrayList<>();
        for (String line : stackTrace.split("\\R")) {
            Matcher matcher = FRAME_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                classNames.add(matcher.group(1));
            }
        }
        return classNames;
    }

    /** JUnit naming convention: a test class name ends in Test/Tests/IT. Simple, not exhaustive. */
    static boolean isTestClass(String fullyQualifiedClassName) {
        String simpleName = simpleNameOf(fullyQualifiedClassName);
        return simpleName.endsWith("Test") || simpleName.endsWith("Tests") || simpleName.endsWith("IT");
    }

    /**
     * A plausible relative source file path for a class name, e.g. {@code com.example.Foo} (or an
     * inner class {@code com.example.Foo$Bar}) becomes {@code com/example/Foo.java}. Matched
     * against changed-file paths with {@code endsWith}, since the derived path has no source-root
     * prefix (no {@code src/main/java/} guess baked in).
     */
    static String relativeFilePath(String fullyQualifiedClassName) {
        String outerClassName = fullyQualifiedClassName.contains("$")
            ? fullyQualifiedClassName.substring(0, fullyQualifiedClassName.indexOf('$'))
            : fullyQualifiedClassName;
        return outerClassName.replace('.', '/') + ".java";
    }

    private static String simpleNameOf(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedClassName.substring(lastDot + 1) : fullyQualifiedClassName;
    }

    private StackFrames() {
    }
}
