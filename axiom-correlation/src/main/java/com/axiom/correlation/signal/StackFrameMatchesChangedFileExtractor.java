package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.SourceChangeEvidence;
import com.axiom.correlation.model.TestFailureEvidence;

import java.util.List;
import java.util.Optional;

/**
 * Present when a non-test ("production") stack frame's derived source file matches one of the
 * changed files. Only production frames are checked — a stack frame that is itself test code
 * matching a changed test file is {@link TopFrameIsTestCodeExtractor}'s concern, not this one's.
 */
public final class StackFrameMatchesChangedFileExtractor implements SignalExtractor {

    @Override
    public List<Signal> extract(List<CorrelationEvidence> evidence) {
        Optional<TestFailureEvidence> testFailure =
            EvidenceLookup.find(evidence, EvidenceType.TEST_FAILURE, TestFailureEvidence.class);
        Optional<SourceChangeEvidence> sourceChange =
            EvidenceLookup.find(evidence, EvidenceType.SOURCE_CHANGE, SourceChangeEvidence.class);

        if (testFailure.isEmpty() || sourceChange.isEmpty()) {
            return List.of(new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, false, List.of()));
        }

        List<String> changedFiles = sourceChange.get().changedFiles();
        List<String> frames = StackFrames.classNames(testFailure.get().failureEvent().stackTrace());

        for (String frame : frames) {
            if (StackFrames.isTestClass(frame)) {
                continue;
            }
            String relativePath = StackFrames.relativeFilePath(frame);
            boolean matched = changedFiles.stream().anyMatch(file -> file.endsWith(relativePath));
            if (matched) {
                return List.of(new Signal(
                    SignalType.STACK_FRAME_MATCHES_CHANGED_FILE,
                    true,
                    List.of(testFailure.get().evidenceId(), sourceChange.get().evidenceId())));
            }
        }
        return List.of(new Signal(SignalType.STACK_FRAME_MATCHES_CHANGED_FILE, false, List.of()));
    }
}
