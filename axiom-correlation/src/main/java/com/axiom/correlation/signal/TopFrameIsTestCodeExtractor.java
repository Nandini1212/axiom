package com.axiom.correlation.signal;

import com.axiom.correlation.model.CorrelationEvidence;
import com.axiom.correlation.model.EvidenceType;
import com.axiom.correlation.model.TestFailureEvidence;

import java.util.List;
import java.util.Optional;

/**
 * Present when the topmost stack frame belongs to test code — a strong signal the failure is in
 * the test itself, not the production code under test.
 */
public final class TopFrameIsTestCodeExtractor implements SignalExtractor {

    @Override
    public List<Signal> extract(List<CorrelationEvidence> evidence) {
        Optional<TestFailureEvidence> testFailure =
            EvidenceLookup.find(evidence, EvidenceType.TEST_FAILURE, TestFailureEvidence.class);
        if (testFailure.isEmpty()) {
            return List.of(new Signal(SignalType.TOP_FRAME_IS_TEST_CODE, false, List.of()));
        }

        List<String> frames = StackFrames.classNames(testFailure.get().failureEvent().stackTrace());
        boolean topIsTest = !frames.isEmpty() && StackFrames.isTestClass(frames.get(0));

        return List.of(new Signal(
            SignalType.TOP_FRAME_IS_TEST_CODE,
            topIsTest,
            topIsTest ? List.of(testFailure.get().evidenceId()) : List.of()));
    }
}
