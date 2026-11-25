package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRulesTest {

    static final TenantId T = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private ApprovalRules.Rule rule(BigDecimal fees, String service, String risk, UUID client, String approver) {
        return new ApprovalRules.Rule("R", fees, service, risk, client, approver);
    }

    @Test
    void everyNamedConditionMustHold() {
        ApprovalRules.Rule r = rule(new BigDecimal("50000"), "CS-X", "HIGH", null, "COMPLIANCE_MANAGER");
        assertThat(ApprovalRules.matches(r, new BigDecimal("60000"), Set.of("CS-X"), "HIGH", CLIENT)).isTrue();
        assertThat(ApprovalRules.matches(r, new BigDecimal("49999"), Set.of("CS-X"), "HIGH", CLIENT)).isFalse();
        assertThat(ApprovalRules.matches(r, new BigDecimal("60000"), Set.of("CS-Y"), "HIGH", CLIENT)).isFalse();
        assertThat(ApprovalRules.matches(r, new BigDecimal("60000"), Set.of("CS-X"), "LOW", CLIENT)).isFalse();
        assertThat(ApprovalRules.matches(r, new BigDecimal("60000"), Set.of("CS-X"), null, CLIENT)).isFalse();
    }

    @Test
    void aRuleWithNoConditionNeverMatches() {
        assertThat(ApprovalRules.matches(rule(null, null, null, null, "PRACTICE_OWNER"), BigDecimal.TEN, Set.of("X"), "HIGH", CLIENT)).isFalse();
    }

    @Test
    void aRuleCanOnlyRaiseTheApprover() {
        List<ApprovalRules.Rule> rules = List.of(rule(null, null, "HIGH", null, "COMPLIANCE_MANAGER"));
        ApprovalRules.Outcome none = ApprovalRules.apply("CONSULTANT", rules, BigDecimal.ONE, Set.of(), "LOW", CLIENT);
        assertThat(none.requiredApprover().equals("CONSULTANT") && none.basis() == null).isTrue();
        ApprovalRules.Outcome raised = ApprovalRules.apply("CONSULTANT", rules, BigDecimal.ONE, Set.of(), "HIGH", CLIENT);
        assertThat(raised.requiredApprover().equals("COMPLIANCE_MANAGER")).isTrue();
        assertThat(raised.basis().contains("Approval rules that applied")).isTrue();
        ApprovalRules.Outcome kept = ApprovalRules.apply("PRACTICE_OWNER", rules, BigDecimal.ONE, Set.of(), "HIGH", CLIENT);
        assertThat(kept.requiredApprover().equals("PRACTICE_OWNER")).isTrue();
    }

    @Test
    void strictestOfSeveralRulesWins() {
        List<ApprovalRules.Rule> rules = List.of(rule(null, null, null, CLIENT, "COMPLIANCE_MANAGER"), rule(new BigDecimal("1"), null, null, null, "PRACTICE_OWNER"));
        assertThat(ApprovalRules.apply("CONSULTANT", rules, BigDecimal.TEN, Set.of(), null, CLIENT).requiredApprover().equals("PRACTICE_OWNER")).isTrue();
    }

    @Test
    void proposalChecks() {
        assertThat(ApprovalRules.problem("Big", null, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look") != null).isTrue();
        assertThat(ApprovalRules.problem("Big", BigDecimal.ZERO, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look") != null).isTrue();
        assertThat(ApprovalRules.problem("Big", BigDecimal.TEN, null, null, null, "CONSULTANT", "Large quotes need a second look") != null).isTrue();
        assertThat(ApprovalRules.problem("Big", BigDecimal.TEN, null, "EXTREME", null, "PRACTICE_OWNER", "Large quotes need a second look") != null).isTrue();
        assertThat(ApprovalRules.problem("Big", BigDecimal.TEN, null, null, null, "PRACTICE_OWNER", "short") != null).isTrue();
        assertThat(ApprovalRules.problem("Big", BigDecimal.TEN, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look") == null).isTrue();
    }

    @Test
    void anotherAdministratorMustActivate() {
        ComplianceApprovalRule r = ComplianceApprovalRule.propose(T, "Big", BigDecimal.TEN, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look", A);
        assertThatThrownBy(() -> r.activate(A)).isInstanceOf(IllegalStateException.class);
        assertThat(r.getStatus().equals("PROPOSED")).isTrue();
        r.activate(B);
        assertThat(r.getStatus().equals("ACTIVE")).isTrue();
        assertThatThrownBy(() -> r.activate(B)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retireNeedsAReasonAndHappensOnce() {
        ComplianceApprovalRule r = ComplianceApprovalRule.propose(T, "Big", BigDecimal.TEN, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look", A);
        assertThatThrownBy(() -> r.retire(B, "no")).isInstanceOf(IllegalArgumentException.class);
        r.retire(B, "Replaced by a new fee rule");
        assertThat(r.getStatus().equals("RETIRED")).isTrue();
        assertThatThrownBy(() -> r.retire(B, "Replaced by a new fee rule")).isInstanceOf(IllegalStateException.class);
    }
}
