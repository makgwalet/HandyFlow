package za.co.handyflow.platform.ap.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApEftBatchRepository;
import za.co.handyflow.platform.ap.domain.repository.ApSupplierBankingRepository;
import za.co.handyflow.platform.shared.EmailService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test found while checking ApRemittanceEmailService as a
 * candidate for the shared-template migration (one of the 9 files with
 * independent inline email HTML): remittanceEmailBody interpolated
 * supplierName and paymentRef directly into HTML with no escaping at
 * all — the same bug category as the 30 already found and fixed in
 * EmailTemplates. Fixed in place (this file was NOT migrated onto
 * EmailTemplates.wrap() — see the class's own note on why: it can't be
 * tenant-branded without a module-boundary change ap's package-info.java
 * doesn't currently allow, and forcing it onto wrap() would incorrectly
 * ADD HandyFlow branding to what's today a neutral document).
 */
@ExtendWith(MockitoExtension.class)
class ApRemittanceEmailServiceEscapingTest {

    @Mock
    private ApBillRepository billRepo;
    @Mock
    private ApEftBatchRepository batchRepo;
    @Mock
    private ApSupplierBankingRepository supplierBankingRepo;
    @Mock
    private ApPdfGenerator pdfGenerator;
    @Mock
    private EmailService emailService;

    private ApRemittanceEmailService service() {
        return new ApRemittanceEmailService(billRepo, batchRepo, supplierBankingRepo, pdfGenerator, emailService);
    }

    @Test
    @DisplayName("escapes supplierName")
    void escapesSupplierName() {
        String payload = "<script>alert(1)</script>";
        String html = service().remittanceEmailBody(payload, new BigDecimal("1000.00"), "REF123");

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("escapes paymentRef")
    void escapesPaymentRef() {
        String payload = "<script>alert(1)</script>";
        String html = service().remittanceEmailBody("Acme Supplies", new BigDecimal("1000.00"), payload);

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("tolerates a null amount and null paymentRef")
    void tolerateNulls() {
        String html = service().remittanceEmailBody("Acme Supplies", null, null);
        assertThat(html).contains("Acme Supplies");
        assertThat(html).doesNotContain("Payment reference");
    }
}
