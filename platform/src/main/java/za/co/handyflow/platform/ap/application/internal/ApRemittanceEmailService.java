package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.model.ApEftBatch;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.ap.domain.repository.ApEftBatchRepository;
import za.co.handyflow.platform.ap.domain.repository.ApSupplierBankingRepository;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Separate small service, same modular-separation precedent as
 * ApRecurringBillService/ApSupplierBankingService. Deliberately does NOT
 * reimplement PDF generation or email-sending — reuses
 * ApPdfGenerator.generateBillRemittance()/generateBatchRemittance() (both
 * already existed, download-only until now) and EmailService's own
 * sendWithAttachment(), the same method Accounting's PDF-attached
 * reminders already use. The actual fix here is entirely about WHERE the
 * recipient email comes from: ApSupplierBanking, keyed by supplier name —
 * this is what was missing, not the PDF or email machinery, both of
 * which already worked.
 * <p>
 * FIX: previously built its own inline HTML with no tenant branding at
 * all (ap's package-info.java didn't allow depending on identity). Now
 * that tenant branding is a resolved platform decision (strategic
 * roadmap backlog, Part 0, Decision 2), ap's boundary was widened
 * specifically for this and the email body is built via
 * EmailTemplates.remittanceAdvice(...), correctly tenant-branded — this
 * is the tenant's own AP department paying a supplier, not a HandyFlow
 * document.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApRemittanceEmailService {

    private final ApBillRepository            billRepo;
    private final ApEftBatchRepository        batchRepo;
    private final ApSupplierBankingRepository supplierBankingRepo;
    private final ApPdfGenerator              pdfGenerator;
    private final EmailService                emailService;
    private final TenantFacade                tenantFacade;

    @Transactional(readOnly = true)
    public void sendBillRemittance(TenantId tenantId, UUID billId) {
        ApBill bill = billRepo.findByIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId.toString()));

        String email = resolveSupplierEmail(tenantId, bill.getSupplierName());

        // generateBillRemittance() already throws BILL_NOT_PAID if this
        // bill isn't PAID — not duplicated here, just letting it throw.
        byte[] pdf = pdfGenerator.generateBillRemittance(tenantId, billId);

        String subject = "Remittance Advice — " + bill.getBillNumber();
        String body = EmailTemplates.remittanceAdvice(
                tenantCompanyName(tenantId), bill.getSupplierName(), bill.getTotalAmount(), bill.getPaymentRef());
        emailService.sendWithAttachment(email, subject, body, "remittance-advice.pdf", pdf);

        log.info("Sent remittance email for bill={} to={}", billId, email);
    }

    @Transactional(readOnly = true)
    public void sendBatchRemittance(TenantId tenantId, UUID batchId, String supplierName) {
        ApEftBatch batch = batchRepo.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("EFT Batch", batchId.toString()));

        String email = resolveSupplierEmail(tenantId, supplierName);

        // generateBatchRemittance() already throws SUPPLIER_NOT_IN_BATCH
        // if nothing matches — not duplicated here either.
        byte[] pdf = pdfGenerator.generateBatchRemittance(tenantId, batchId, supplierName);

        String subject = "Remittance Advice — Batch " + batch.getBatchNumber();
        String body = EmailTemplates.remittanceAdvice(
                tenantCompanyName(tenantId), supplierName, null, batch.getPaymentRef());
        emailService.sendWithAttachment(email, subject, body, "remittance-advice.pdf", pdf);

        log.info("Sent batch remittance email for batch={} supplier={} to={}", batchId, supplierName, email);
    }

    // Falls back to a generic label rather than throwing — a missing/
    // incomplete tenant profile shouldn't block a supplier from actually
    // getting paid and being told about it; unlike resolveSupplierEmail
    // below, there's a sensible degraded behaviour here.
    private String tenantCompanyName(TenantId tenantId) {
        return tenantFacade.findTenantDetails(tenantId)
                .map(TenantDetails::companyName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Your Supplier");
    }

    // Deliberately narrow: a missing email is a real, actionable gap
    // (go configure it on the Suppliers tab), not something to silently
    // skip or guess at — unlike the CSV export's blank-column fallback,
    // there's no sensible "send to nothing" behavior for an email.
    private String resolveSupplierEmail(TenantId tenantId, String supplierName) {
        String email = supplierBankingRepo.findByTenantIdAndSupplierName(tenantId, supplierName)
                .map(b -> b.getEmail())
                .orElse(null);
        if (email == null || email.isBlank()) {
            throw new HandyFlowException(
                    "No email address configured for '" + supplierName
                            + "' — add one on the Suppliers tab before sending a remittance email",
                    HttpStatus.BAD_REQUEST, "SUPPLIER_EMAIL_NOT_CONFIGURED");
        }
        return email;
    }
}