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
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
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

    @Transactional(readOnly = true)
    public void sendBillRemittance(TenantId tenantId, UUID billId) {
        ApBill bill = billRepo.findByIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId.toString()));

        String email = resolveSupplierEmail(tenantId, bill.getSupplierName());

        // generateBillRemittance() already throws BILL_NOT_PAID if this
        // bill isn't PAID — not duplicated here, just letting it throw.
        byte[] pdf = pdfGenerator.generateBillRemittance(tenantId, billId);

        String subject = "Remittance Advice — " + bill.getBillNumber();
        String body = remittanceEmailBody(bill.getSupplierName(), bill.getTotalAmount(), bill.getPaymentRef());
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
        String body = remittanceEmailBody(supplierName, null, batch.getPaymentRef());
        emailService.sendWithAttachment(email, subject, body, "remittance-advice.pdf", pdf);

        log.info("Sent batch remittance email for batch={} supplier={} to={}", batchId, supplierName, email);
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

    private String remittanceEmailBody(String supplierName, BigDecimal amount, String paymentRef) {
        return """
            <!DOCTYPE html>
            <html><body style="font-family:Arial,sans-serif;color:#0F172A;max-width:600px;margin:0 auto;padding:20px">
              <div style="background:#1B3A6B;padding:24px;border-radius:8px 8px 0 0">
                <h1 style="color:white;margin:0;font-size:20px">Remittance Advice</h1>
              </div>
              <div style="background:#F8FAFC;padding:24px;border-radius:0 0 8px 8px">
                <p style="font-size:15px">Dear %s,</p>
                <p style="font-size:14px;color:#374151">Please find attached the remittance advice confirming payment%s.</p>
                %s
                <p style="font-size:13px;color:#64748B;margin-top:20px">If you have any questions about this payment, please contact us directly.</p>
              </div>
            </body></html>
            """.formatted(
                supplierName,
                amount != null ? " of R " + amount : "",
                paymentRef != null ? "<p style=\"font-size:13px;color:#64748B\">Payment reference: " + paymentRef + "</p>" : "");
    }
}