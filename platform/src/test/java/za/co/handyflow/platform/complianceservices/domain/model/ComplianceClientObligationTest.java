package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceClientObligationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final UUID ASSESSOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final String WHY = "Client has no employees, confirmed by payroll letter";

    private static ApplicabilityRules.Evaluation eval(String type) {
        return ApplicabilityRules.evaluate(List.of(ObligationRules.condition("ENTITY_TYPE", "IN", "PTY_LTD")),
                new ApplicabilityRules.Facts(type, null, null, null, null), Set.of("entityType"));
    }

    private static ComplianceClientObligation suggested(String type) {
        return ComplianceClientObligation.suggested(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, eval(type));
    }

    @Test @DisplayName("a new assignment follows the suggestion and has no decision")
    void starts() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        assertThat(a.getState()).isEqualTo("APPLICABLE");
        assertThat(a.isDecidedByPerson()).isFalse();
        assertThat(a.getApprovalStatus()).isEqualTo("NOT_REQUIRED");
    }

    @Test @DisplayName("a suggestion that changes moves the state while no one has decided")
    void followsSuggestion() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        assertThat(a.resuggest(UUID.randomUUID(), 1, eval("NPC"))).isTrue();
        assertThat(a.getState()).isEqualTo("NOT_APPLICABLE");
        assertThat(a.resuggest(UUID.randomUUID(), 1, eval("NPC"))).isFalse();
    }

    @Test @DisplayName("a person's decision stays when the suggestion changes")
    void decisionStays() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        a.decide("NOT_APPLICABLE", WHY, "Payroll letter 2026-11-01", null, TODAY.plusMonths(6), ASSESSOR, TODAY);
        UUID v2 = UUID.randomUUID();
        a.resuggest(v2, 2, eval("PTY_LTD"));
        assertThat(a.getState()).isEqualTo("NOT_APPLICABLE");
        assertThat(a.getSuggestedState()).isEqualTo("APPLICABLE");
        assertThat(a.getVersionNumber()).isEqualTo(2);
        assertThat(a.getDecidedVersionNumber()).isEqualTo(1);
    }

    @Test @DisplayName("removing an obligation awaits approval, and until then it still counts as applicable")
    void removalAwaits() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        a.decide("not_applicable", WHY, "Payroll letter", null, TODAY.plusMonths(6), ASSESSOR, TODAY);
        assertThat(a.getState()).isEqualTo("NOT_APPLICABLE");
        assertThat(a.getApprovalStatus()).isEqualTo("AWAITING");
        assertThat(a.effectiveState()).isEqualTo("APPLICABLE");
        a.approve(OTHER, null);
        assertThat(a.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(a.effectiveState()).isEqualTo("NOT_APPLICABLE");
        assertThat(a.getApprovalExceptionReason()).isNull();
    }

    @Test @DisplayName("the person who made the decision needs a reason to approve it themselves")
    void selfApproval() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        a.decide("NOT_APPLICABLE", WHY, null, null, TODAY.plusMonths(6), ASSESSOR, TODAY);
        assertThatThrownBy(() -> a.approve(ASSESSOR, null)).hasMessageContaining("someone else");
        assertThatThrownBy(() -> a.approve(ASSESSOR, "short")).isInstanceOf(IllegalArgumentException.class);
        a.approve(ASSESSOR, "Sole practitioner, no second person");
        assertThat(a.getApprovalExceptionReason()).isEqualTo("Sole practitioner, no second person");
    }

    @Test @DisplayName("nothing to approve unless a removal is waiting")
    void nothingToApprove() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        assertThatThrownBy(() -> a.approve(OTHER, null)).isInstanceOf(IllegalStateException.class);
        a.decide("APPLICABLE", null, null, null, null, ASSESSOR, TODAY);
        assertThat(a.getApprovalStatus()).isEqualTo("NOT_REQUIRED");
        assertThatThrownBy(() -> a.approve(OTHER, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a new decision clears an earlier approval")
    void newDecisionResets() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        a.decide("NOT_APPLICABLE", WHY, null, null, TODAY.plusMonths(6), ASSESSOR, TODAY);
        a.approve(OTHER, null);
        a.decide("NOT_APPLICABLE", WHY, null, null, TODAY.plusMonths(3), ASSESSOR, TODAY);
        assertThat(a.getApprovalStatus()).isEqualTo("AWAITING");
        assertThat(a.getApprovedBy()).isNull();
    }

    @Test @DisplayName("confirming the suggestion needs no reason, an override does")
    void reasons() {
        ComplianceClientObligation a = suggested("PTY_LTD");
        a.decide("APPLICABLE", null, null, null, null, ASSESSOR, TODAY);
        assertThat(a.isDecidedByPerson()).isTrue();
        assertThatThrownBy(() -> a.decide("PENDING", "short", null, null, null, ASSESSOR, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getState()).isEqualTo("APPLICABLE");
    }
}
