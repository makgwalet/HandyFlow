package za.co.handyflow.platform.clinic.domain.question;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionRuleEngineTest {

    static Map<String, Object> q(String code, String op, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put("q", code); m.put("op", op);
        if (v != null) m.put("value", v);
        return m;
    }
    static Map<String, Object> ctx(String f, String op, Object v) { return Map.of("ctx", f, "op", op, "value", v); }

    final QuestionDef cough = QuestionDef.of("has_cough", AnswerType.YES_NO);
    final QuestionDef days = QuestionDef.of("cough_days", AnswerType.NUMBER).withRange(0, 365).withRules(
            RuleDef.of(RuleKind.SHOW_WHEN, q("has_cough", "EQ", true)),
            RuleDef.of(RuleKind.REQUIRED_WHEN, q("has_cough", "EQ", true)),
            RuleDef.warning(q("cough_days", "GTE", 14), "Cough for two weeks or more"),
            RuleDef.trigger(q("cough_days", "GTE", 14), "CHRONIC_COUGH"));
    final QuestionDef lmp = QuestionDef.of("lmp", AnswerType.DATE).withRules(RuleDef.of(RuleKind.SHOW_WHEN,
            Map.of("all", List.of(ctx("sexAtBirth", "EQ", "FEMALE"), ctx("ageMonths", "GTE", 144)))));
    final List<QuestionDef> form = List.of(cough, days, lmp);
    final RedFlagDef flag = new RedFlagDef("RF1", "Breathing", "URGENT", q("cough_days", "GTE", 30), "Needs prompt attention");
    final RuleContext adultFemale = new RuleContext(360, "FEMALE", null, "CONSULTATION");

    @Test
    @DisplayName("a dependent question stays hidden until its parent answer reveals it")
    void revealsOnParentAnswer() {
        assertThat(QuestionRuleEngine.evaluate(form, List.of(), Map.of(), adultFemale).visible())
                .doesNotContain("cough_days");
        var r = QuestionRuleEngine.evaluate(form, List.of(), Map.of("has_cough", true), adultFemale);
        assertThat(r.visible()).contains("cough_days");
        assertThat(r.required()).contains("cough_days");
        assertThat(r.missingRequired()).containsExactly("cough_days");
    }

    @Test
    @DisplayName("patient context drives visibility; unknown sex shows nothing sex-specific")
    void contextVisibility() {
        assertThat(QuestionRuleEngine.evaluate(form, List.of(), Map.of(), adultFemale).visible()).contains("lmp");
        assertThat(QuestionRuleEngine.evaluate(form, List.of(), Map.of(), new RuleContext(360, "MALE", null, null)).visible())
                .doesNotContain("lmp");
        assertThat(QuestionRuleEngine.evaluate(form, List.of(), Map.of(), RuleContext.empty()).visible())
                .doesNotContain("lmp");
    }

    @Test
    @DisplayName("warnings and group triggers fire on the answer; a red flag needs its own threshold")
    void warningsTriggersAndFlags() {
        var r = QuestionRuleEngine.evaluate(form, List.of(flag), Map.of("has_cough", true, "cough_days", 20), adultFemale);
        assertThat(r.warnings()).containsKey("cough_days");
        assertThat(r.triggeredGroups()).contains("CHRONIC_COUGH");
        assertThat(r.redFlags()).isEmpty();
        assertThat(r.missingRequired()).isEmpty();

        r = QuestionRuleEngine.evaluate(form, List.of(flag), Map.of("has_cough", true, "cough_days", "45"), adultFemale);
        assertThat(r.hasUrgentFlag()).isTrue();
    }

    @Test
    @DisplayName("answers to hidden questions are ignored, so stale answers cannot trigger anything")
    void staleAnswersIgnored() {
        var r = QuestionRuleEngine.evaluate(form, List.of(flag), Map.of("has_cough", false, "cough_days", 45), adultFemale);
        assertThat(r.redFlags()).isEmpty();
        assertThat(r.triggeredGroups()).isEmpty();
        assertThat(r.effectiveAnswers()).doesNotContainKey("cough_days");
    }

    @Test
    @DisplayName("chains of reveals resolve whatever order the questions are listed in")
    void chainedReveals() {
        QuestionDef a = QuestionDef.of("a", AnswerType.YES_NO);
        QuestionDef b = QuestionDef.of("b", AnswerType.YES_NO).withRules(RuleDef.of(RuleKind.SHOW_WHEN, q("a", "EQ", true)));
        QuestionDef c = QuestionDef.of("c", AnswerType.TEXT).withRules(RuleDef.of(RuleKind.SHOW_WHEN, q("b", "EQ", true)));

        var off = QuestionRuleEngine.evaluate(List.of(c, b, a), List.of(), Map.of("a", false, "b", true, "c", "x"), null);
        assertThat(off.visible()).containsExactlyInAnyOrder("a");
        var on = QuestionRuleEngine.evaluate(List.of(c, b, a), List.of(), Map.of("a", true, "b", true, "c", "x"), null);
        assertThat(on.visible()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    @DisplayName("a disabled required question is not reported missing")
    void disabledNotMissing() {
        QuestionDef a = QuestionDef.of("a", AnswerType.YES_NO);
        QuestionDef e = QuestionDef.of("e", AnswerType.TEXT).required()
                .withRules(RuleDef.of(RuleKind.ENABLE_WHEN, q("a", "EQ", true)));

        var r = QuestionRuleEngine.evaluate(List.of(a, e), List.of(), Map.of("a", false), null);

        assertThat(r.disabled()).contains("e");
        assertThat(r.missingRequired()).isEmpty();
    }

    @Test
    @DisplayName("operators: missing answers never match, lists and measurements behave")
    void operators() {
        RuleContext none = RuleContext.empty();
        assertThat(QuestionRuleEngine.matches(q("x", "NE", "a"), Map.of(), none)).isFalse();
        assertThat(QuestionRuleEngine.matches(q("x", "IN", List.of("a", "b")), Map.of("x", "b"), none)).isTrue();
        assertThat(QuestionRuleEngine.matches(q("x", "IN", List.of("a", "b")), Map.of("x", List.of("c", "b")), none)).isTrue();
        assertThat(QuestionRuleEngine.matches(q("x", "CONTAINS", "c"), Map.of("x", List.of("c", "b")), none)).isTrue();
        assertThat(QuestionRuleEngine.matches(q("x", "GT", 100), Map.of("x", Map.of("value", 120, "unit", "mmHg")), none)).isTrue();
        assertThat(QuestionRuleEngine.matches(Map.of("not", q("x", "EQ", 1)), Map.of("x", 2), none)).isTrue();
        assertThat(QuestionRuleEngine.matches(Map.of("any", List.of(q("x", "EQ", 1), q("y", "EQ", 2))), Map.of("y", 2), none)).isTrue();
        assertThat(QuestionRuleEngine.matches(q("x", "EMPTY", null), Map.of("x", "  "), none)).isTrue();
    }

    @Test
    @DisplayName("malformed expressions are rejected with a reason before they can be activated")
    void validatesExpressions() {
        List<Map<String, Object>> bad = List.of(
                Map.of(),
                Map.of("q", "x", "ctx", "ageMonths", "op", "EQ", "value", 1),
                q("x", "LIKE", 1), q("x", "EQ", null), q("x", "IN", "a"),
                Map.of("ctx", "shoeSize", "op", "EQ", "value", 1));
        for (Map<String, Object> e : bad) {
            assertThatThrownBy(() -> QuestionRuleEngine.validateExpression(e)).isInstanceOf(IllegalArgumentException.class);
        }
        QuestionRuleEngine.validateExpression(Map.of("all", List.of(q("a", "EQ", true), Map.of("not", ctx("sexAtBirth", "EQ", "MALE")))));
    }

    @Test
    @DisplayName("content only reaches clinicians through review: DRAFT cannot jump to ACTIVE")
    void statusWorkflow() {
        assertThat(ContentStatus.DRAFT.canMoveTo(ContentStatus.ACTIVE)).isFalse();
        assertThat(ContentStatus.CLINICAL_REVIEW.canMoveTo(ContentStatus.APPROVED)).isTrue();
        assertThat(ContentStatus.APPROVED.canMoveTo(ContentStatus.ACTIVE)).isTrue();
        assertThat(ContentStatus.RETIRED.canMoveTo(ContentStatus.DRAFT)).isFalse();
        assertThat(Set.of(ContentStatus.values())).hasSize(7);
    }
}
