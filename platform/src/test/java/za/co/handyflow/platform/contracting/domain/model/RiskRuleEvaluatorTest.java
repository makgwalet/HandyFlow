package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RiskRuleEvaluatorTest {

    ContractRiskRule rule(String type, String text, Number num) {
        return ContractRiskRule.create(UUID.randomUUID(), "R", null, type, text, num == null ? null : new BigDecimal(num.toString()), null, "HIGH", false, "Failed", null, null, null, null);
    }

    RiskRuleEvaluator.Facts facts(String body, BigDecimal value, LocalDate start, LocalDate end, boolean autoRenew, int notice, Set<String> cats) {
        return new RiskRuleEvaluator.Facts("SERVICE", body, value, start, end, autoRenew, notice, cats);
    }

    RiskRuleEvaluator.Facts plain() { return facts("<p>Hello</p>", null, null, null, false, 30, Set.of()); }

    @Test
    void requiredClauseCategoryFailsWhenMissingAndPassesWhenPresent() {
        ContractRiskRule r = rule("REQUIRES_CLAUSE_CATEGORY", "force_majeure", null);
        assertTrue(RiskRuleEvaluator.evaluate(r, plain()).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(r, facts("", null, null, null, false, 30, Set.of("FORCE_MAJEURE"))).isEmpty());
    }

    @Test
    void phraseRulesIgnoreMarkupCaseAndSpacing() {
        RiskRuleEvaluator.Facts f = facts("<p>The   <b>Governing</b> Law</p>", null, null, null, false, 30, Set.of());
        assertTrue(RiskRuleEvaluator.evaluate(rule("REQUIRES_PHRASE", "governing law", null), f).isEmpty());
        assertTrue(RiskRuleEvaluator.evaluate(rule("REQUIRES_PHRASE", "arbitration", null), f).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(rule("FORBIDS_PHRASE", "GOVERNING LAW", null), f).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(rule("FORBIDS_PHRASE", "arbitration", null), f).isEmpty());
    }

    @Test
    void nullBodyIsTreatedAsEmpty() {
        assertTrue(RiskRuleEvaluator.evaluate(rule("REQUIRES_PHRASE", "x", null), facts(null, null, null, null, false, 30, Set.of())).isPresent());
    }

    @Test
    void valueRules() {
        ContractRiskRule above = rule("VALUE_ABOVE", null, 1000);
        assertTrue(RiskRuleEvaluator.evaluate(above, facts("", new BigDecimal("1000.01"), null, null, false, 30, Set.of())).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(above, facts("", new BigDecimal("1000"), null, null, false, 30, Set.of())).isEmpty());
        assertTrue(RiskRuleEvaluator.evaluate(above, plain()).isEmpty(), "no value cannot exceed a limit");
        assertTrue(RiskRuleEvaluator.evaluate(rule("REQUIRES_VALUE", null, null), plain()).isPresent());
    }

    @Test
    void termAndEndDateRules() {
        LocalDate s = LocalDate.of(2026, 1, 1);
        ContractRiskRule longer = rule("TERM_LONGER_THAN_MONTHS", null, 12);
        assertTrue(RiskRuleEvaluator.evaluate(longer, facts("", null, s, s.plusMonths(13), false, 30, Set.of())).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(longer, facts("", null, s, s.plusMonths(12), false, 30, Set.of())).isEmpty());
        assertTrue(RiskRuleEvaluator.evaluate(longer, plain()).isEmpty());
        assertTrue(RiskRuleEvaluator.evaluate(rule("REQUIRES_END_DATE", null, null), plain()).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(rule("REQUIRES_END_DATE", null, null), facts("", null, s, s.plusDays(5), false, 30, Set.of())).isEmpty());
    }

    @Test
    void autoRenewNoticeRuleOnlyAppliesToAutoRenewingContracts() {
        ContractRiskRule r = rule("AUTO_RENEW_NOTICE_BELOW_DAYS", null, 60);
        assertTrue(RiskRuleEvaluator.evaluate(r, facts("", null, null, null, true, 30, Set.of())).isPresent());
        assertTrue(RiskRuleEvaluator.evaluate(r, facts("", null, null, null, true, 60, Set.of())).isEmpty());
        assertTrue(RiskRuleEvaluator.evaluate(r, facts("", null, null, null, false, 30, Set.of())).isEmpty());
    }

    @Test
    void failureReturnsTheRulesOwnMessage() {
        assertEquals("Failed", RiskRuleEvaluator.evaluate(rule("REQUIRES_VALUE", null, null), plain()).orElseThrow());
    }
}
