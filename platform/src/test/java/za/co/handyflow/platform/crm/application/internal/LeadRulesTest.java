package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Facts;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

class LeadRulesTest {
    private static UUID id(int i) { return new UUID(0, i); }
    private static final Facts F = new Facts(LeadSource.WEBSITE, LeadStage.NEW, new BigDecimal("60000"), true, false, false, Set.of("vip", "gauteng"));
    private static Condition c(ConditionField f, ConditionOp o, String v) { return new Condition(f, o, v); }

    @Test
    void conditionsMatchWhatTheyDescribe() {
        assertThat(LeadConditions.matches(c(ConditionField.SOURCE, ConditionOp.EQ, "website"), F)).isTrue();
        assertThat(LeadConditions.matches(c(ConditionField.SOURCE, ConditionOp.EQ, "REFERRAL"), F)).isFalse();
        assertThat(LeadConditions.matches(c(ConditionField.DEAL_VALUE, ConditionOp.GTE, "50000"), F)).isTrue();
        assertThat(LeadConditions.matches(c(ConditionField.DEAL_VALUE, ConditionOp.LTE, "50000"), F)).isFalse();
        assertThat(LeadConditions.matches(c(ConditionField.DEAL_VALUE, ConditionOp.GTE, "1"), new Facts(null, null, null, false, false, false, Set.of()))).isFalse();
        assertThat(LeadConditions.matches(c(ConditionField.HAS_EMAIL, ConditionOp.EQ, "true"), F)).isTrue();
        assertThat(LeadConditions.matches(c(ConditionField.HAS_PHONE, ConditionOp.EQ, "false"), F)).isTrue();
        assertThat(LeadConditions.matches(c(ConditionField.TAG, ConditionOp.EQ, " VIP "), F)).isTrue();
        assertThat(LeadConditions.matches(null, F)).isTrue();
    }

    @Test
    void badConditionsAreRefusedWithAReadableReason() {
        assertThatThrownBy(() -> LeadConditions.check(null)).hasMessage("Choose what to check");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.SOURCE, null, "x"))).hasMessage("Choose how to compare");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.SOURCE, ConditionOp.EQ, " "))).hasMessage("Give the value to compare with");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.SOURCE, ConditionOp.GTE, "WEBSITE"))).hasMessage("Source can only be compared with \"is\"");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.SOURCE, ConditionOp.EQ, "RADIO"))).hasMessage("Choose a lead source");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.STAGE, ConditionOp.EQ, "MAYBE"))).hasMessage("Choose a pipeline stage");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.HAS_EMAIL, ConditionOp.EQ, "maybe"))).hasMessage("Choose yes or no");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.DEAL_VALUE, ConditionOp.EQ, "5"))).hasMessage("A deal value is compared as at least or at most");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.DEAL_VALUE, ConditionOp.GTE, "abc"))).hasMessage("The deal value must be a number");
        assertThatThrownBy(() -> LeadConditions.check(c(ConditionField.DEAL_VALUE, ConditionOp.GTE, "-5"))).hasMessage("The deal value cannot be negative");
        LeadConditions.check(c(ConditionField.DEAL_VALUE, ConditionOp.GTE, "5000.50"));
        assertThat(LeadConditions.describe(c(ConditionField.SOURCE, ConditionOp.EQ, "WALK_IN"))).isEqualTo("Source is Walk in");
        assertThat(LeadConditions.describe(null)).isEqualTo("Every lead");
    }

    @Test
    void scoreIsTheSumOfMatchingPointsBetweenZeroAndAHundred() {
        var rules = List.of(new LeadScoring.Rule("Came from website", c(ConditionField.SOURCE, ConditionOp.EQ, "WEBSITE"), 20),
                new LeadScoring.Rule("Big deal", c(ConditionField.DEAL_VALUE, ConditionOp.GTE, "50000"), 30),
                new LeadScoring.Rule("No phone", c(ConditionField.HAS_PHONE, ConditionOp.EQ, "false"), -10),
                new LeadScoring.Rule("Referral", c(ConditionField.SOURCE, ConditionOp.EQ, "REFERRAL"), 40));
        var r = LeadScoring.score(rules, F);
        assertThat(r.score()).isEqualTo(40);
        assertThat(r.band()).isEqualTo("WARM");
        assertThat(r.reasons()).containsExactly("Came from website (+20)", "Big deal (+30)", "No phone (-10)");
        assertThat(LeadScoring.score(List.of(new LeadScoring.Rule("x", null, -50)), F).score()).isZero();
        assertThat(LeadScoring.score(List.of(new LeadScoring.Rule("x", null, 80), new LeadScoring.Rule("y", null, 80)), F).band()).isEqualTo("HOT");
        assertThat(List.of(LeadScoring.band(29), LeadScoring.band(30), LeadScoring.band(59), LeadScoring.band(60))).containsExactly("COLD", "WARM", "WARM", "HOT");
    }

    @Test
    void scoringRulesAreChecked() {
        Condition ok = c(ConditionField.TAG, ConditionOp.EQ, "a");
        assertThatThrownBy(() -> LeadScoring.checkRule(" ", ok, 5)).hasMessage("Name the scoring rule");
        assertThatThrownBy(() -> LeadScoring.checkRule("x", ok, 0)).hasMessage("Points must be between -100 and 100, and not zero");
        assertThatThrownBy(() -> LeadScoring.checkRule("x", ok, 101)).hasMessage("Points must be between -100 and 100, and not zero");
        LeadScoring.checkRule("x", ok, -100);
    }

    @Test
    void routingTakesTheFirstMatchingRuleAndHandsOutInTurn() {
        var web = new LeadRouting.Rule(id(1), 2, c(ConditionField.SOURCE, ConditionOp.EQ, "WEBSITE"), List.of(id(10), id(11)), 0);
        var all = new LeadRouting.Rule(id(2), 9, null, List.of(id(12)), 0);
        var d = LeadRouting.choose(List.of(all, web), F, u -> true).orElseThrow();
        assertThat(d.ruleId()).isEqualTo(id(1));
        assertThat(d.assignee()).isEqualTo(id(10));
        assertThat(d.newIndex()).isEqualTo(1);
        var d2 = LeadRouting.choose(List.of(new LeadRouting.Rule(id(1), 2, web.condition(), web.assignees(), 1)), F, u -> true).orElseThrow();
        assertThat(d2.assignee()).isEqualTo(id(11));
        assertThat(d2.newIndex()).isZero();
    }

    @Test
    void routingSkipsPeopleWhoCannotTakeLeadsAndFallsThroughToTheNextRule() {
        var web = new LeadRouting.Rule(id(1), 2, c(ConditionField.SOURCE, ConditionOp.EQ, "WEBSITE"), List.of(id(10), id(11)), 0);
        var all = new LeadRouting.Rule(id(2), 9, null, List.of(id(12)), 0);
        assertThat(LeadRouting.choose(List.of(web, all), F, u -> !u.equals(id(10))).orElseThrow().assignee()).isEqualTo(id(11));
        assertThat(LeadRouting.choose(List.of(web, all), F, u -> u.equals(id(12))).orElseThrow().ruleId()).isEqualTo(id(2));
        assertThat(LeadRouting.choose(List.of(web), new Facts(LeadSource.PHONE, null, null, false, false, false, Set.of()), u -> true)).isEmpty();
        assertThat(LeadRouting.choose(List.of(web), F, u -> false)).isEmpty();
        assertThat(LeadRouting.choose(List.of(new LeadRouting.Rule(id(3), 1, null, List.of(), 0)), F, u -> true)).isEmpty();
    }

    @Test
    void routingRulesAreChecked() {
        assertThat(LeadRouting.parseIds(id(1) + "," + id(2))).containsExactly(id(1), id(2));
        assertThat(LeadRouting.parseIds(" ")).isEmpty();
        assertThat(LeadRouting.joinIds(List.of(id(1), id(2)))).isEqualTo(id(1) + "," + id(2));
        assertThatThrownBy(() -> LeadRouting.checkRule("", null, List.of(id(1)))).hasMessage("Name the routing rule");
        assertThatThrownBy(() -> LeadRouting.checkRule("x", null, List.of())).hasMessage("Choose at least one person to receive the leads");
        assertThatThrownBy(() -> LeadRouting.checkRule("x", null, List.of(id(1), id(1)))).hasMessage("Each person can be listed once");
        assertThatThrownBy(() -> LeadRouting.checkRule("x", c(ConditionField.TAG, ConditionOp.GTE, "a"), List.of(id(1)))).hasMessage("Tag can only be compared with \"is\"");
        LeadRouting.checkRule("x", null, List.of(id(1)));
    }
}
