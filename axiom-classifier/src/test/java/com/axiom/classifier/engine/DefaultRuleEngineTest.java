package com.axiom.classifier.engine;

import com.axiom.classifier.model.Evidence;
import com.axiom.classifier.model.FailureCategory;
import com.axiom.classifier.model.RuleMatch;
import com.axiom.classifier.rule.Operator;
import com.axiom.classifier.rule.PreparedCondition;
import com.axiom.classifier.rule.PreparedMatchGroup;
import com.axiom.classifier.rule.PreparedRule;
import com.axiom.classifier.rule.RuleField;
import com.axiom.common.model.FailureEvent;
import com.axiom.common.model.FailureStatus;
import com.axiom.common.model.SourceFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DefaultRuleEngineTest {

    private static FailureEvent event(String message, String stackTrace) {
        return new FailureEvent(
            "evt-1", "someTest", null, null, SourceFormat.JUNIT, FailureStatus.FAILED,
            message, stackTrace, null, null, null, null);
    }

    private static PreparedCondition condition(
        RuleField field, Operator operator, String value, boolean caseSensitive) {
        Pattern pattern = operator == Operator.REGEX
            ? Pattern.compile(value, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE)
            : null;
        return new PreparedCondition(field, operator, value, caseSensitive, pattern);
    }

    private static PreparedRule anyRule(String id, int priority, PreparedCondition... conditions) {
        return new PreparedRule(
            id, priority, new PreparedMatchGroup(List.of(conditions), null),
            FailureCategory.INFRASTRUCTURE_FAILURE, 0.9, "evidence message");
    }

    private static PreparedRule allRule(String id, int priority, PreparedCondition... conditions) {
        return new PreparedRule(
            id, priority, new PreparedMatchGroup(null, List.of(conditions)),
            FailureCategory.INFRASTRUCTURE_FAILURE, 0.9, "evidence message");
    }

    @Test
    void containsOperatorMatches() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "Connection refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("Connection refused: db down", null));

        assertEquals(1, matches.size());
        assertEquals("r1", matches.get(0).ruleId());
    }

    @Test
    void containsOperatorDoesNotMatchWhenAbsent() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "Connection refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("assertion failed", null));

        assertTrue(matches.isEmpty());
    }

    @Test
    void containsIsCaseInsensitiveByDefault() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "connection refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("CONNECTION REFUSED: db down", null));

        assertEquals(1, matches.size());
    }

    @Test
    void containsRespectsExplicitCaseSensitive() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "connection refused", true));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        assertTrue(engine.evaluate(event("CONNECTION REFUSED: db down", null)).isEmpty());
        assertEquals(1, engine.evaluate(event("connection refused: db down", null)).size());
    }

    @Test
    void equalsOperatorMatchesExactValueOnly() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.EQUALS, "boom", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        assertEquals(1, engine.evaluate(event("boom", null)).size());
        assertTrue(engine.evaluate(event("boomtown", null)).isEmpty());
    }

    @Test
    void startsWithOperatorMatches() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.STARTS_WITH, "Connection", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        assertEquals(1, engine.evaluate(event("Connection refused", null)).size());
        assertTrue(engine.evaluate(event("refused: Connection", null)).isEmpty());
    }

    @Test
    void endsWithOperatorMatches() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.ENDS_WITH, "refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        assertEquals(1, engine.evaluate(event("Connection refused", null)).size());
        assertTrue(engine.evaluate(event("refused connection", null)).isEmpty());
    }

    @Test
    void regexOperatorMatchesPartialString() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.REGEX, "conn.*refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("error: connection refused today", null));

        assertEquals(1, matches.size());
    }

    @Test
    void regexOperatorIsCaseInsensitiveByDefault() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.REGEX, "conn.*refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        assertEquals(1, engine.evaluate(event("CONNECTION REFUSED", null)).size());
    }

    @Test
    void fieldAbsentOnEventMeansConditionDoesNotMatch() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.STACK_TRACE, Operator.CONTAINS, "NullPointerException", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        // event has no stack trace at all
        List<RuleMatch> matches = engine.evaluate(event("some message", null));

        assertTrue(matches.isEmpty());
    }

    @Test
    void anyGroupMatchesWhenAtLeastOneConditionMatches() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "timeout", false),
            condition(RuleField.MESSAGE, Operator.CONTAINS, "refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("Connection refused", null));

        assertEquals(1, matches.size());
        // only the satisfied condition produces evidence, not the unsatisfied one
        assertEquals(1, matches.get(0).evidence().size());
        assertEquals("refused", matches.get(0).evidence().get(0).expectedValue());
    }

    @Test
    void anyGroupProducesEvidenceForEverySatisfiedCondition() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "connection", false),
            condition(RuleField.MESSAGE, Operator.CONTAINS, "refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("Connection refused", null));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).evidence().size());
    }

    @Test
    void allGroupRequiresEveryConditionToMatch() {
        PreparedRule rule = allRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "connection", false),
            condition(RuleField.MESSAGE, Operator.CONTAINS, "timeout", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        // only "connection" is present, not "timeout" -> all-group must not match
        assertTrue(engine.evaluate(event("connection refused", null)).isEmpty());
    }

    @Test
    void allGroupMatchesWhenEveryConditionMatchesAndProducesEvidenceForEachOne() {
        PreparedRule rule = allRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "connection", false),
            condition(RuleField.MESSAGE, Operator.CONTAINS, "refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        List<RuleMatch> matches = engine.evaluate(event("connection refused", null));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).evidence().size());
    }

    @Test
    void evidenceContentIsCorrectlyPopulated() {
        PreparedRule rule = anyRule("connection-refused", 100,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "Connection refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        RuleMatch match = engine.evaluate(event("Connection refused: db down", null)).get(0);
        Evidence evidence = match.evidence().get(0);

        assertEquals(RuleField.MESSAGE, evidence.field());
        assertEquals(Operator.CONTAINS, evidence.operator());
        assertEquals("Connection refused", evidence.expectedValue());
        assertEquals("Connection refused: db down", evidence.actualValue());
        assertEquals("evidence message", evidence.explanation());
        assertEquals(100, match.priority());
        assertEquals(FailureCategory.INFRASTRUCTURE_FAILURE, match.category());
        assertEquals(0.9, match.confidence());
    }

    @Test
    void multipleRulesCanMatchTheSameFailure() {
        PreparedRule first = anyRule("r1", 100,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "connection", false));
        PreparedRule second = anyRule("r2", 50,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "refused", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(first, second));

        List<RuleMatch> matches = engine.evaluate(event("connection refused", null));

        assertEquals(2, matches.size());
    }

    @Test
    void resultOrderFollowsInputRuleOrderNotReSorted() {
        // Rules deliberately supplied out of priority order; engine must not re-sort —
        // that ordering decision belongs to RuleProcessor, not RuleEngine.
        PreparedRule lowFirst = anyRule("low", 1,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "boom", false));
        PreparedRule highSecond = anyRule("high", 100,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "boom", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(lowFirst, highSecond));

        List<RuleMatch> matches = engine.evaluate(event("boom", null));

        assertEquals(List.of("low", "high"), matches.stream().map(RuleMatch::ruleId).toList());
    }

    @Test
    void noRulesMatchProducesEmptyList() {
        PreparedRule rule = anyRule("r1", 0,
            condition(RuleField.MESSAGE, Operator.CONTAINS, "nope", false));
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of(rule));

        assertTrue(engine.evaluate(event("something else", null)).isEmpty());
    }

    @Test
    void emptyRuleListProducesEmptyResult() {
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of());

        assertTrue(engine.evaluate(event("anything", null)).isEmpty());
    }

    @Test
    void throwsWhenEventIsNull() {
        DefaultRuleEngine engine = new DefaultRuleEngine(List.of());

        assertThrows(NullPointerException.class, () -> engine.evaluate(null));
    }

    @Test
    void throwsWhenRulesListIsNull() {
        assertThrows(NullPointerException.class, () -> new DefaultRuleEngine(null));
    }
}
