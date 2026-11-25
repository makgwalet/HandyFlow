package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceBillingEventTest {

    private static final UUID BY = UUID.randomUUID();
    private static final LocalDate DUE = LocalDate.of(2027, 4, 1);

    private static ComplianceBillingEvent event() {
        TenantId t = TenantId.generate();
        ComplianceAgreement a = ComplianceAgreement.draft(t, UUID.randomUUID(), UUID.randomUUID(), "CA-000001", new BigDecimal("18000"), new BigDecimal("20700"), "ZAR",
                new ComplianceAgreement.Terms("Annual", LocalDate.of(2027, 3, 1), null, "MONTHLY", new BigDecimal("1500"), LocalDate.of(2027, 3, 1), false, 30, null, null), BY);
        return ComplianceBillingEvent.schedule(t, a, DUE);
    }

    @Test
    @DisplayName("An event takes its amount and wording from the agreement")
    void scheduling() {
        ComplianceBillingEvent e = event();
        assertThat(e.getStatus()).isEqualTo("SCHEDULED");
        assertThat(e.getAmount()).isEqualByComparingTo("1500");
        assertThat(e.getDescription()).contains("CA-000001").contains("2027-04-01");
        assertThat(e.getInvoiceId()).isNull();
    }

    @Test
    @DisplayName("Raising records the invoice, and cannot be done twice")
    void raising() {
        ComplianceBillingEvent e = event();
        UUID inv = UUID.randomUUID();
        e.invoiced(BY, inv, "INV-00042");
        assertThat(e.getStatus()).isEqualTo("INVOICED");
        assertThat(e.getInvoiceId()).isEqualTo(inv);
        assertThat(e.getInvoiceNumber()).isEqualTo("INV-00042");
        assertThat(e.getRaisedBy()).isEqualTo(BY);
        assertThatThrownBy(() -> e.invoiced(BY, UUID.randomUUID(), "INV-00043")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event().invoiced(BY, null, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Skip needs a reason and can be undone; an invoiced bill cannot be skipped")
    void skipping() {
        ComplianceBillingEvent e = event();
        assertThatThrownBy(() -> e.skip("no")).isInstanceOf(IllegalArgumentException.class);
        e.skip("Client prepaid this month by EFT");
        assertThat(e.getStatus()).isEqualTo("SKIPPED");
        assertThat(e.getSkipReason()).contains("prepaid");
        e.restore();
        assertThat(e.getStatus()).isEqualTo("SCHEDULED");
        assertThat(e.getSkipReason()).isNull();
        assertThatThrownBy(e::restore).isInstanceOf(IllegalStateException.class);
        e.invoiced(BY, UUID.randomUUID(), "INV-1");
        assertThatThrownBy(() -> e.skip("Too late to skip this one")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Release works only when the invoice is cancelled or gone, keeps the reason, and lets the bill be raised again")
    void releasing() {
        ComplianceBillingEvent e = event();
        assertThatThrownBy(() -> e.release(null, "Wrong amount on the draft")).isInstanceOf(IllegalStateException.class);
        e.invoiced(BY, UUID.randomUUID(), "INV-00042");
        assertThatThrownBy(() -> e.release("ISSUED", "Wrong amount on the draft")).isInstanceOf(IllegalStateException.class).hasMessageContaining("issued");
        assertThatThrownBy(() -> e.release("CANCELLED", "bad")).isInstanceOf(IllegalArgumentException.class);
        e.release("CANCELLED", "Wrong amount on the draft");
        assertThat(e.getStatus()).isEqualTo("SCHEDULED");
        assertThat(e.getInvoiceId()).isNull();
        assertThat(e.getReleaseNote()).contains("Wrong amount").contains("INV-00042");
        e.invoiced(BY, UUID.randomUUID(), "INV-00044");
        assertThat(e.getStatus()).isEqualTo("INVOICED");
    }
}
