package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceAgreementTest {

    private static final UUID BY = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2027, 3, 1);

    private static ComplianceAgreement.Terms terms(String freq, LocalDate end) {
        return new ComplianceAgreement.Terms("  Annual compliance  ", START, end, freq, new BigDecimal("1500"), START, false, 30, "notes", "terms");
    }

    private static ComplianceAgreement draft() {
        return ComplianceAgreement.draft(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "CA-000001", new BigDecimal("18000"),
                new BigDecimal("20700"), "ZAR", terms("monthly", START.plusMonths(11)), BY);
    }

    private static ComplianceAgreement active() {
        ComplianceAgreement a = draft();
        a.activate(BY, "Signed by the director, emailed back");
        return a;
    }

    @Test
    @DisplayName("A draft cleans the title, upper-cases the frequency and starts as a draft")
    void drafting() {
        ComplianceAgreement a = draft();
        assertThat(a.getStatus()).isEqualTo("DRAFT");
        assertThat(a.getTitle()).isEqualTo("Annual compliance");
        assertThat(a.getBillingFrequency()).isEqualTo("MONTHLY");
        assertThat(a.getBillingAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Bad terms are refused when drafting")
    void badTerms() {
        assertThatThrownBy(() -> ComplianceAgreement.draft(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "CA-1", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                terms("WEEKLY", null), BY)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("frequency");
        assertThatThrownBy(() -> ComplianceAgreement.draft(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "CA-1", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                terms("MONTHLY", START.minusDays(1)), BY)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("before the start");
    }

    @Test
    @DisplayName("A draft can be edited; an active agreement cannot")
    void editing() {
        ComplianceAgreement a = draft();
        a.edit(terms("QUARTERLY", START.plusMonths(11)));
        assertThat(a.getBillingFrequency()).isEqualTo("QUARTERLY");
        a.activate(BY, "Confirmed by phone and email");
        assertThatThrownBy(() -> a.edit(terms("MONTHLY", null))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Activating needs a note saying how the client confirmed")
    void activating() {
        ComplianceAgreement a = draft();
        assertThatThrownBy(() -> a.activate(BY, "ok")).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getStatus()).isEqualTo("DRAFT");
        a.activate(BY, "Signed by the director, emailed back");
        assertThat(a.getStatus()).isEqualTo("ACTIVE");
        assertThat(a.getActivatedBy()).isEqualTo(BY);
        assertThat(a.getActivatedAt()).isNotNull();
        assertThatThrownBy(() -> a.activate(BY, "Signed by the director again")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Suspend needs a reason; resume brings it back and clears the reason")
    void suspendResume() {
        ComplianceAgreement a = active();
        assertThatThrownBy(() -> a.suspend("short")).isInstanceOf(IllegalArgumentException.class);
        a.suspend("Client asked for a pause while they restructure");
        assertThat(a.getStatus()).isEqualTo("SUSPENDED");
        assertThat(a.getSuspendedReason()).contains("restructure");
        a.resume();
        assertThat(a.getStatus()).isEqualTo("ACTIVE");
        assertThat(a.getSuspendedReason()).isNull();
        assertThatThrownBy(a::resume).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ending records the date and who, and works from active or suspended only")
    void ending() {
        ComplianceAgreement a = active();
        assertThatThrownBy(() -> a.end(BY, START.minusDays(1), "The client left the practice")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.end(BY, null, "The client left the practice")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.end(BY, START.plusMonths(2), "no")).isInstanceOf(IllegalArgumentException.class);
        a.end(BY, START.plusMonths(2), "The client left the practice");
        assertThat(a.getStatus()).isEqualTo("ENDED");
        assertThat(a.getEndDate()).isEqualTo(START.plusMonths(2));
        assertThat(a.getEndedBy()).isEqualTo(BY);
        assertThatThrownBy(() -> a.suspend("Pausing it after the end")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> draft().end(BY, START, "Never started")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Only a draft can be cancelled, with a reason")
    void cancelling() {
        ComplianceAgreement a = draft();
        assertThatThrownBy(() -> a.cancel("x")).isInstanceOf(IllegalArgumentException.class);
        a.cancel("Client chose another provider");
        assertThat(a.getStatus()).isEqualTo("CANCELLED");
        assertThat(a.getEndReason()).contains("another provider");
        assertThatThrownBy(() -> active().cancel("Too late for this one")).isInstanceOf(IllegalStateException.class);
    }
}
