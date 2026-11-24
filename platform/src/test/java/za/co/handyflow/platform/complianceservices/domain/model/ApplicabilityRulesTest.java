package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicabilityRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final Set<String> ALL = Set.of("entityType", "industry", "employeeCount", "turnoverBand", "vatNumber");

    private static ObligationRules.Condition c(String f, String op, String v) { return ObligationRules.condition(f, op, v); }
    private static ApplicabilityRules.Facts facts(String type, String industry, Integer emp, String band, Boolean vat) {
        return new ApplicabilityRules.Facts(type, industry, emp, band, vat);
    }

    @Test @DisplayName("an obligation with no conditions is never assumed to apply")
    void noConditions() {
        var e = ApplicabilityRules.evaluate(List.of(), facts("PTY_LTD", null, null, null, null), ALL);
        assertThat(e.state()).isEqualTo("PENDING");
        assertThat(e.reasons()).contains("assessor");
    }

    @Test @DisplayName("every condition met means it applies")
    void applies() {
        var e = ApplicabilityRules.evaluate(List.of(c("ENTITY_TYPE", "IN", "PTY_LTD,NPC"), c("EMPLOYEE_COUNT", "GTE", "10")),
                facts("PTY_LTD", null, 25, null, null), ALL);
        assertThat(e.state()).isEqualTo("APPLICABLE");
        assertThat(e.basedOnUnconfirmed()).isFalse();
    }

    @Test @DisplayName("one condition not met means it does not apply, even if another fact is missing")
    void notApplicable() {
        var e = ApplicabilityRules.evaluate(List.of(c("ENTITY_TYPE", "IN", "NPC"), c("INDUSTRY", "IN", "Mining")),
                facts("PTY_LTD", null, null, null, null), ALL);
        assertThat(e.state()).isEqualTo("NOT_APPLICABLE");
        assertThat(e.reasons()).contains("not NPC");
    }

    @Test @DisplayName("a missing fact leaves it pending, never a quiet yes or no")
    void unknown() {
        var e = ApplicabilityRules.evaluate(List.of(c("ENTITY_TYPE", "IN", "PTY_LTD"), c("TURNOVER_BAND", "IN", "OVER_250M")),
                facts("PTY_LTD", null, null, null, null), ALL);
        assertThat(e.state()).isEqualTo("PENDING");
        assertThat(e.reasons()).contains("Turnover band is not recorded");
    }

    @Test @DisplayName("employee limits are checked at their edges")
    void employees() {
        var gte = List.of(c("EMPLOYEE_COUNT", "GTE", "50"));
        assertThat(ApplicabilityRules.evaluate(gte, facts(null, null, 50, null, null), ALL).state()).isEqualTo("APPLICABLE");
        assertThat(ApplicabilityRules.evaluate(gte, facts(null, null, 49, null, null), ALL).state()).isEqualTo("NOT_APPLICABLE");
        var lte = List.of(c("EMPLOYEE_COUNT", "LTE", "49"));
        assertThat(ApplicabilityRules.evaluate(lte, facts(null, null, 49, null, null), ALL).state()).isEqualTo("APPLICABLE");
        assertThat(ApplicabilityRules.evaluate(lte, facts(null, null, 50, null, null), ALL).state()).isEqualTo("NOT_APPLICABLE");
    }

    @Test @DisplayName("industry matches without regard to case")
    void industry() {
        var cs = List.of(c("INDUSTRY", "IN", "Construction, Mining"));
        assertThat(ApplicabilityRules.evaluate(cs, facts(null, " construction ", null, null, null), ALL).state()).isEqualTo("APPLICABLE");
        assertThat(ApplicabilityRules.evaluate(cs, facts(null, "Retail", null, null, null), ALL).state()).isEqualTo("NOT_APPLICABLE");
    }

    @Test @DisplayName("a VAT number means registered, and no number is not proof of the opposite")
    void vat() {
        var yes = List.of(c("VAT_REGISTERED", "IS_TRUE", null));
        var no = List.of(c("VAT_REGISTERED", "IS_FALSE", null));
        assertThat(ApplicabilityRules.evaluate(yes, facts(null, null, null, null, true), ALL).state()).isEqualTo("APPLICABLE");
        assertThat(ApplicabilityRules.evaluate(no, facts(null, null, null, null, true), ALL).state()).isEqualTo("NOT_APPLICABLE");
        assertThat(ApplicabilityRules.evaluate(yes, facts(null, null, null, null, null), ALL).state()).isEqualTo("PENDING");
        assertThat(ApplicabilityRules.evaluate(no, facts(null, null, null, null, null), ALL).state()).isEqualTo("PENDING");
    }

    @Test @DisplayName("a result built on facts nobody has confirmed is flagged")
    void unconfirmed() {
        var e = ApplicabilityRules.evaluate(List.of(c("ENTITY_TYPE", "IN", "PTY_LTD")), facts("PTY_LTD", null, null, null, null), Set.of());
        assertThat(e.state()).isEqualTo("APPLICABLE");
        assertThat(e.basedOnUnconfirmed()).isTrue();
        var unknownOnly = ApplicabilityRules.evaluate(List.of(c("ENTITY_TYPE", "IN", "PTY_LTD")), facts(null, null, null, null, null), Set.of());
        assertThat(unknownOnly.basedOnUnconfirmed()).isFalse();
    }

    @Test @DisplayName("a decision that differs from the suggestion needs a reason")
    void decisionReason() {
        ApplicabilityRules.checkDecision("APPLICABLE", "APPLICABLE", null, null, TODAY);
        assertThatThrownBy(() -> ApplicabilityRules.checkDecision("APPLICABLE", "PENDING", "short", null, TODAY)).hasMessageContaining("reason");
        ApplicabilityRules.checkDecision("APPLICABLE", "PENDING", "Client confirmed in writing that they employ 12 people", null, TODAY);
        assertThatThrownBy(() -> ApplicabilityRules.checkDecision("MAYBE", "PENDING", null, null, TODAY)).hasMessageContaining("state");
    }

    @Test @DisplayName("removing an obligation needs a review date within two years")
    void reviewDate() {
        String why = "Client has no employees, confirmed by payroll letter";
        assertThatThrownBy(() -> ApplicabilityRules.checkDecision("NOT_APPLICABLE", "APPLICABLE", why, null, TODAY)).hasMessageContaining("again");
        assertThatThrownBy(() -> ApplicabilityRules.checkDecision("NOT_APPLICABLE", "APPLICABLE", why, TODAY, TODAY)).hasMessageContaining("future");
        assertThatThrownBy(() -> ApplicabilityRules.checkDecision("NOT_APPLICABLE", "APPLICABLE", why, TODAY.plusMonths(25), TODAY)).hasMessageContaining("24 months");
        ApplicabilityRules.checkDecision("NOT_APPLICABLE", "APPLICABLE", why, TODAY.plusMonths(24), TODAY);
    }

    @Test @DisplayName("only removing something not already suggested as removed needs approval")
    void approval() {
        assertThat(ApplicabilityRules.needsApproval("NOT_APPLICABLE", "APPLICABLE")).isTrue();
        assertThat(ApplicabilityRules.needsApproval("NOT_APPLICABLE", "PENDING")).isTrue();
        assertThat(ApplicabilityRules.needsApproval("NOT_APPLICABLE", "NOT_APPLICABLE")).isFalse();
        assertThat(ApplicabilityRules.needsApproval("APPLICABLE", "PENDING")).isFalse();
    }

    @Test @DisplayName("an unapproved removal still counts as applicable")
    void effective() {
        assertThat(ApplicabilityRules.effectiveState("NOT_APPLICABLE", "AWAITING")).isEqualTo("APPLICABLE");
        assertThat(ApplicabilityRules.effectiveState("NOT_APPLICABLE", "APPROVED")).isEqualTo("NOT_APPLICABLE");
        assertThat(ApplicabilityRules.effectiveState("PENDING", "NOT_REQUIRED")).isEqualTo("PENDING");
    }

    @Test @DisplayName("a decision is due another look after a new version or on its review date")
    void review() {
        assertThat(ApplicabilityRules.reviewNeeded(1, 2, null, TODAY)).isTrue();
        assertThat(ApplicabilityRules.reviewNeeded(2, 2, TODAY, TODAY)).isTrue();
        assertThat(ApplicabilityRules.reviewNeeded(2, 2, TODAY.plusDays(1), TODAY)).isFalse();
        assertThat(ApplicabilityRules.reviewNeeded(2, 2, null, TODAY)).isFalse();
    }
}
