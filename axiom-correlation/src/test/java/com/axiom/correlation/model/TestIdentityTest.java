package com.axiom.correlation.model;

import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestIdentityTest {

    private static FailureEvent failureEvent(String testName, String className, String suiteName) {
        return new FailureEvent(
            "failure-1", testName, className, suiteName,
            SourceFormat.JUNIT, FailureStatus.FAILED, "message", "stack trace",
            null, null, null, null);
    }

    @Test
    void rejectsBlankClassName() {
        assertThrows(IllegalArgumentException.class, () -> new TestIdentity(" ", "testCharge"));
    }

    @Test
    void rejectsBlankTestName() {
        assertThrows(IllegalArgumentException.class, () -> new TestIdentity("com.example.Foo", " "));
    }

    @Test
    void rejectsNullFields() {
        assertThrows(NullPointerException.class, () -> new TestIdentity(null, "testCharge"));
        assertThrows(NullPointerException.class, () -> new TestIdentity("com.example.Foo", null));
    }

    @Test
    void equalityIsFieldBased() {
        TestIdentity first = new TestIdentity("com.example.PaymentServiceTest", "testCharge");
        TestIdentity second = new TestIdentity("com.example.PaymentServiceTest", "testCharge");
        TestIdentity different = new TestIdentity("com.example.PaymentServiceTest", "testRefund");

        assertEquals(first, second);
        assertNotEquals(first, different);
    }

    @Test
    void matchingIsExactAndCaseSensitive() {
        TestIdentity lower = new TestIdentity("com.example.PaymentServiceTest", "testcharge");
        TestIdentity upper = new TestIdentity("com.example.PaymentServiceTest", "testCharge");

        assertNotEquals(lower, upper);
    }

    @Test
    void canonicalNameUsesHashNotDot() {
        TestIdentity identity = new TestIdentity("com.example.PaymentServiceTest", "testCharge");

        assertEquals("com.example.PaymentServiceTest#testCharge", identity.canonicalName());
    }

    @Test
    void fromReturnsIdentityWhenBothFieldsPresent() {
        FailureEvent event = failureEvent("testCharge", "com.example.PaymentServiceTest", null);

        Optional<TestIdentity> identity = TestIdentity.from(event);

        assertTrue(identity.isPresent());
        assertEquals("com.example.PaymentServiceTest", identity.get().className());
        assertEquals("testCharge", identity.get().testName());
    }

    @Test
    void fromIsEmptyForSuiteLevelFailureWithNoClassOrTestName() {
        FailureEvent event = failureEvent(null, null, "PaymentSuite");

        assertTrue(TestIdentity.from(event).isEmpty());
    }

    @Test
    void fromIsEmptyWhenOnlyClassNamePresent() {
        FailureEvent event = failureEvent(null, "com.example.PaymentServiceTest", null);

        assertTrue(TestIdentity.from(event).isEmpty());
    }

    @Test
    void fromIsEmptyWhenOnlyTestNamePresent() {
        FailureEvent event = failureEvent("testCharge", null, null);

        assertTrue(TestIdentity.from(event).isEmpty());
    }
}
