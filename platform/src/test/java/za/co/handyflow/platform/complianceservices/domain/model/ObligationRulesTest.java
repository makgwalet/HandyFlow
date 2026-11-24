package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObligationRulesTest {

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    @Test @DisplayName("a code is tidied and checked")
    void code() {
        assertThat(ObligationRules.code(" cipc annual-return ")).isEqualTo("CIPC-ANNUAL-RETURN");
        assertThatThrownBy(() -> ObligationRules.code(" ")).hasMessageContaining("required");
        assertThatThrownBy(() -> ObligationRules.code("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.code("BAD/CODE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("each due rule asks for what it needs")
    void dueRules() {
        ObligationRules.dueRule("NONE", null, null, null, null);
        ObligationRules.dueRule("ANNUAL_FIXED", 6, 30, null, null);
        ObligationRules.dueRule("MONTHLY_DAY", null, 7, null, null);
        ObligationRules.dueRule("ANNUAL_AFTER_FYE", null, null, null, 4);
        ObligationRules.dueRule("AFTER_EVENT", null, null, 21, null);
        assertThatThrownBy(() -> ObligationRules.dueRule("ANNUAL_FIXED", 13, 1, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.dueRule("ANNUAL_FIXED", 2, 30, null, null)).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> ObligationRules.dueRule("MONTHLY_DAY", null, 32, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.dueRule("ANNIVERSARY", null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.dueRule("BEFORE_EXPIRY", null, null, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.dueRule("WHENEVER", null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("conditions are checked against the field they test")
    void conditions() {
        assertThat(ObligationRules.condition("ENTITY_TYPE", "in", "pty_ltd, npc, pty_ltd").value()).isEqualTo("PTY_LTD,NPC");
        assertThat(ObligationRules.condition("EMPLOYEE_COUNT", "GTE", " 50 ").value()).isEqualTo("50");
        assertThat(ObligationRules.condition("VAT_REGISTERED", "IS_TRUE", "ignored").value()).isNull();
        assertThat(ObligationRules.condition("INDUSTRY", "IN", "Construction, Mining").value()).isEqualTo("Construction,Mining");
        assertThatThrownBy(() -> ObligationRules.condition("ENTITY_TYPE", "IN", "SPACESHIP")).hasMessageContaining("not a valid");
        assertThatThrownBy(() -> ObligationRules.condition("ENTITY_TYPE", "GTE", "NPC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.condition("EMPLOYEE_COUNT", "GTE", "many")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.condition("EMPLOYEE_COUNT", "IN", "5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligationRules.condition("SHOE_SIZE", "IN", "9")).hasMessageContaining("Unknown");
        assertThatThrownBy(() -> ObligationRules.condition("TURNOVER_BAND", "IN", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("conditions that cannot all be met are caught")
    void consistent() {
        ObligationRules.consistent(List.of(ObligationRules.condition("EMPLOYEE_COUNT", "GTE", "10"), ObligationRules.condition("EMPLOYEE_COUNT", "LTE", "50")));
        assertThatThrownBy(() -> ObligationRules.consistent(List.of(ObligationRules.condition("EMPLOYEE_COUNT", "GTE", "50"), ObligationRules.condition("EMPLOYEE_COUNT", "LTE", "10"))))
                .hasMessageContaining("cannot both");
        assertThatThrownBy(() -> ObligationRules.consistent(List.of(ObligationRules.condition("VAT_REGISTERED", "IS_TRUE", null), ObligationRules.condition("VAT_REGISTERED", "IS_FALSE", null))))
                .hasMessageContaining("VAT_REGISTERED");
    }

    @Test @DisplayName("evidence needs a name")
    void evidence() {
        assertThat(ObligationRules.evidence(" Tax clearance ", true).documentType()).isEqualTo("Tax clearance");
        assertThatThrownBy(() -> ObligationRules.evidence(" ", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("the version in force is the latest approved one that has started and not ended")
    void effective() {
        var v1 = new ObligationRules.Span(1, true, d(2025, 1, 1), d(2026, 3, 31));
        var v2 = new ObligationRules.Span(2, true, d(2026, 4, 1), null);
        var draft = new ObligationRules.Span(3, false, null, null);
        var future = new ObligationRules.Span(4, true, d(2027, 1, 1), null);
        List<ObligationRules.Span> all = List.of(v1, v2, draft, future);
        assertThat(ObligationRules.effectiveOn(all, d(2025, 6, 1))).isEqualTo(1);
        assertThat(ObligationRules.effectiveOn(all, d(2026, 3, 31))).isEqualTo(1);
        assertThat(ObligationRules.effectiveOn(all, d(2026, 4, 1))).isEqualTo(2);
        assertThat(ObligationRules.effectiveOn(all, d(2026, 12, 31))).isEqualTo(2);
        assertThat(ObligationRules.effectiveOn(all, d(2027, 1, 1))).isEqualTo(4);
        assertThat(ObligationRules.effectiveOn(all, d(2024, 12, 31))).isNull();
    }

    @Test @DisplayName("a new version ends the one before it the day before it starts")
    void endOfPrevious() {
        assertThat(ObligationRules.endOfPrevious(d(2025, 1, 1), d(2026, 4, 1))).isEqualTo(d(2026, 3, 31));
        assertThatThrownBy(() -> ObligationRules.endOfPrevious(d(2026, 4, 1), d(2026, 4, 1))).hasMessageContaining("after the one before");
        assertThatThrownBy(() -> ObligationRules.endOfPrevious(d(2026, 5, 1), d(2026, 4, 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
