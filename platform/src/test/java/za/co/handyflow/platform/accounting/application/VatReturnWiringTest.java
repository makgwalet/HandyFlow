package za.co.handyflow.platform.accounting.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks: VAT201 amounts come from the ledger, and the invoice schedule keeps overdue invoices. */
class VatReturnWiringTest {

    private static final Path PLATFORM = Path.of("src/main/java/za/co/handyflow/platform");

    private static String read(String relative) throws IOException { return Files.readString(PLATFORM.resolve(relative)); }

    private static String vatMethod() throws IOException {
        String s = read("accounting/application/internal/AccountingService.java");
        int start = s.indexOf("public Vat201Response getVat201(");
        assertTrue(start >= 0);
        int end = s.indexOf("// ── AR Aging", start);
        assertTrue(end > start);
        return s.substring(start, end);
    }

    @Test void vatAmountsComeFromTheLedgerRules() throws IOException {
        String body = vatMethod();
        assertTrue(body.contains("VatReturnRules.compute(accounts, lines)"));
        assertTrue(body.contains("vat.outputVat()"));
        assertTrue(body.contains("vat.inputVat()"));
        assertFalse(body.contains("vatSummary.totalOutputVat()"), "output VAT must not come from the invoice sum");
    }

    @Test void theLedgerIsReadForThePeriodWithEveryAccount() throws IOException {
        String body = vatMethod();
        assertTrue(body.contains("journalRepo.findPostedInRange(tenantId, from, to)"));
        assertTrue(body.contains("accountRepo.findAllIncludingInactive(tenantId)"));
    }

    @Test void theInvoiceScheduleKeepsOverdueInvoices() throws IOException {
        String r = read("invoicing/domain/repository/InvoiceRepository.java");
        int at = r.indexOf("findAllForVat");
        String query = r.substring(r.lastIndexOf("@Query", at), at);
        assertTrue(query.contains("'OVERDUE'"));
        for (String status : new String[]{"'ISSUED'", "'PARTIALLY_PAID'", "'PAID'", "'OVERPAID'"})
            assertTrue(query.contains(status), status);
        assertFalse(query.contains("'DRAFT'") || query.contains("'CANCELLED'"));
    }

    @Test void theRemindersAndTheAuditPackStillUseGetVat201() throws IOException {
        assertTrue(read("accounting/application/internal/AccountingNotificationScheduler.java").contains("accountingService.getVat201("));
        assertTrue(read("accounting/application/internal/AuditPackService.java").contains("accounting.getVat201("));
        assertTrue(read("accounting/application/internal/AccountingReportPdfService.java").contains("accountingService.getVat201("));
    }
}
