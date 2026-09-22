package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for EmailTemplates.remittanceAdvice — migrated from
 * ApRemittanceEmailService's own inline HTML now that tenant branding is
 * a resolved platform decision (strategic roadmap backlog, Part 0,
 * Decision 2) and ap's module boundary was widened specifically to allow
 * it. Correctly built on wrapForTenant(), not wrap() — a remittance
 * advice is the tenant's own AP department paying a supplier.
 */
class EmailTemplatesRemittanceAdviceTest {

    @Test
    @DisplayName("shows the tenant's own company name in the header, not HandyFlow")
    void showsTenantBrandedHeader() {
        String html = EmailTemplates.remittanceAdvice(
                "Zeta Earthmoving (Pty) Ltd", "Acme Supplies", new BigDecimal("1000.00"), "REF123");

        assertThat(html).contains("<h1>Zeta Earthmoving (Pty) Ltd</h1>");
        assertThat(html).doesNotContain("<h1>HandyFlow</h1>");
        assertThat(html).contains("Powered by"); // footer still credits the platform, same as every wrapForTenant() template
    }

    @Test
    @DisplayName("escapes supplierName")
    void escapesSupplierName() {
        String payload = "<script>alert(1)</script>";
        String html = EmailTemplates.remittanceAdvice("Zeta Earthmoving", payload, new BigDecimal("1000.00"), "REF123");

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("escapes paymentRef")
    void escapesPaymentRef() {
        String payload = "<script>alert(1)</script>";
        String html = EmailTemplates.remittanceAdvice("Zeta Earthmoving", "Acme Supplies", new BigDecimal("1000.00"), payload);

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("tolerates a null amount and null paymentRef")
    void tolerateNulls() {
        String html = EmailTemplates.remittanceAdvice("Zeta Earthmoving", "Acme Supplies", null, null);
        assertThat(html).contains("Acme Supplies");
        assertThat(html).doesNotContain("Payment reference");
    }
}
