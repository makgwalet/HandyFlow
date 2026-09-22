package za.co.handyflow.platform.ap.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApEftBatchRepository;
import za.co.handyflow.platform.ap.domain.repository.ApSupplierBankingRepository;
import za.co.handyflow.platform.ap.domain.model.ApSupplierBanking;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the tenant-branding fix to ApRemittanceEmailService:
 * now that tenant branding is a resolved platform decision (strategic
 * roadmap backlog, Part 0, Decision 2), ap's module boundary was widened
 * to allow depending on identity, and the remittance email is built via
 * EmailTemplates.remittanceAdvice(...) (own test suite:
 * EmailTemplatesRemittanceAdviceTest) with the tenant's real company
 * name. This test covers the resolution step specific to this class:
 * fetching that name via TenantFacade, and falling back sensibly — never
 * throwing — when the tenant's profile is incomplete.
 */
@ExtendWith(MockitoExtension.class)
class ApRemittanceEmailServiceTest {

    @Mock private ApBillRepository billRepo;
    @Mock private ApEftBatchRepository batchRepo;
    @Mock private ApSupplierBankingRepository supplierBankingRepo;
    @Mock private ApPdfGenerator pdfGenerator;
    @Mock private EmailService emailService;
    @Mock private TenantFacade tenantFacade;

    private ApRemittanceEmailService service() {
        return new ApRemittanceEmailService(billRepo, batchRepo, supplierBankingRepo, pdfGenerator,
                emailService, tenantFacade);
    }

    private static TenantDetails detailsWithCompanyName(String name) {
        return new TenantDetails(UUID.randomUUID(), name, "zeta", null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("uses the tenant's real company name in the remittance email body")
    void usesRealTenantCompanyName() {
        TenantId tenantId = TenantId.generate();
        UUID billId = UUID.randomUUID();

        ApBill bill = ApBill.createFromSupplyChain(tenantId, UUID.randomUUID(), "Acme Supplies",
                "BILL-001", LocalDate.now(), LocalDate.now().plusDays(30),
                new BigDecimal("1000.00"), BigDecimal.ZERO, "SC-1", UUID.randomUUID());

        when(billRepo.findByIdAndTenantId(billId, tenantId)).thenReturn(Optional.of(bill));
        when(supplierBankingRepo.findByTenantIdAndSupplierName(tenantId, "Acme Supplies"))
                .thenReturn(Optional.of(ApSupplierBanking.create(tenantId, "Acme Supplies",
                        null, null, null, null, null, "supplier@example.com", null, UUID.randomUUID())));
        when(tenantFacade.findTenantDetails(tenantId))
                .thenReturn(Optional.of(detailsWithCompanyName("Zeta Earthmoving (Pty) Ltd")));
        when(pdfGenerator.generateBillRemittance(tenantId, billId)).thenReturn(new byte[0]);

        service().sendBillRemittance(tenantId, billId);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithAttachment(any(), any(), bodyCaptor.capture(), any(), any());
        assertThat(bodyCaptor.getValue()).contains("<h1>Zeta Earthmoving (Pty) Ltd</h1>");
    }

    @Test
    @DisplayName("falls back to a generic label, never throws, when the tenant has no company name on file")
    void fallsBackWhenTenantDetailsMissing() {
        TenantId tenantId = TenantId.generate();
        UUID billId = UUID.randomUUID();

        ApBill bill = ApBill.createFromSupplyChain(tenantId, UUID.randomUUID(), "Acme Supplies",
                "BILL-001", LocalDate.now(), LocalDate.now().plusDays(30),
                new BigDecimal("1000.00"), BigDecimal.ZERO, "SC-1", UUID.randomUUID());

        when(billRepo.findByIdAndTenantId(billId, tenantId)).thenReturn(Optional.of(bill));
        when(supplierBankingRepo.findByTenantIdAndSupplierName(tenantId, "Acme Supplies"))
                .thenReturn(Optional.of(ApSupplierBanking.create(tenantId, "Acme Supplies",
                        null, null, null, null, null, "supplier@example.com", null, UUID.randomUUID())));
        when(tenantFacade.findTenantDetails(tenantId)).thenReturn(Optional.empty());
        when(pdfGenerator.generateBillRemittance(tenantId, billId)).thenReturn(new byte[0]);

        service().sendBillRemittance(tenantId, billId);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithAttachment(any(), any(), bodyCaptor.capture(), any(), any());
        assertThat(bodyCaptor.getValue()).contains("<h1>Your Supplier</h1>");
    }
}
