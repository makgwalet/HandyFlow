package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for EmailTemplates.subscriptionInvoiceEmail — migrated
 * from AdminInvoiceService's own private, fully-duplicated copy of
 * wrap()'s CSS (one of the 9 files with independent inline email HTML
 * flagged in PLATFORM-ENGINES-PROGRESS.md). Unlike every other template
 * migrated in this initiative, this one is CORRECTLY HandyFlow-branded —
 * HandyFlow genuinely is the sender of a tenant's own subscription
 * invoice — so it's built on wrap(), not wrapForTenant().
 */
class EmailTemplatesSubscriptionInvoiceTest {

    @Test
    @DisplayName("renders all the invoice details the original inline template showed")
    void rendersInvoiceDetails() {
        String html = EmailTemplates.subscriptionInvoiceEmail(
                "Zeta Earthmoving (Pty) Ltd", "HF-INV-00042", "September 2026", "R 1,495.00");

        assertThat(html).contains("Zeta Earthmoving (Pty) Ltd");
        assertThat(html).contains("HF-INV-00042");
        assertThat(html).contains("September 2026");
        assertThat(html).contains("R 1,495.00");
        assertThat(html).contains("First National Bank");
        assertThat(html).contains("billing@handyflow.co.za");
        // Correctly HandyFlow-branded, not tenant-branded -- this is the one
        // template in this migration family where that's the right choice.
        assertThat(html).contains("<h1>HandyFlow</h1>");
    }

    @Test
    @DisplayName("escapes tenantName")
    void escapesTenantName() {
        String payload = "<script>alert(1)</script>";
        String html = EmailTemplates.subscriptionInvoiceEmail(
                payload, "HF-INV-00042", "September 2026", "R 1,495.00");

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
