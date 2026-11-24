package za.co.handyflow.platform.accounting.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks: ledger journals for invoicing documents are dated on the document, never on "now". */
class PostingDateWiringTest {

    private static final Path HANDLER = Path.of(
            "src/main/java/za/co/handyflow/platform/accounting/application/internal/InvoicingAccountingEventHandler.java");

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
        return n;
    }

    @Test void theHandlerNeverDatesAJournalWithTheServersToday() throws IOException {
        String s = Files.readString(HANDLER);
        assertFalse(s.contains("LocalDate.now()"), "use the document date");
        assertFalse(s.contains("Instant.now()"));
    }

    @Test void everyJournalRequestUsesTheDocumentDateOrTheDateTheEventCarries() throws IOException {
        String s = Files.readString(HANDLER);
        assertEquals(5, count(s, "documentDate(event.tenantId(), event.occurredOn())"),
                "invoice, payment, receipt fallback, refund fallback and credit note");
        assertEquals(5, count(s, "new CreateJournalEntryRequest("));
    }

    @Test void theThreeArgumentConstructorSurvivesForTheExistingTests() throws IOException {
        String s = Files.readString(HANDLER);
        assertTrue(s.contains("@Autowired\n    InvoicingAccountingEventHandler("));
        assertTrue(s.contains("this(accountRepo, bankAccountRepo, ledgerPosting, null);"));
        assertTrue(s.contains("BusinessCalendar.zoneOf(calendar, tenantId)"));
    }

    @Test void theLedgerServiceUsesTheCompanysTodayForReversalsAgingAndTheMonthlySummary() throws IOException {
        String s = Files.readString(Path.of(
                "src/main/java/za/co/handyflow/platform/accounting/application/internal/AccountingService.java"));
        assertFalse(s.contains("LocalDate.now()"));
        assertFalse(s.contains("YearMonth.now()"));
        assertTrue(s.contains("reversalDate != null ? reversalDate : today(tenantId)"));
        assertTrue(s.contains("LocalDate today = today(tenantId);"));
        assertTrue(s.contains("YearMonth.from(today(tenantId))"));
        assertTrue(s.contains("@org.springframework.beans.factory.annotation.Autowired(required = false)"),
                "the calendar is injected into a field so the constructor is unchanged");
    }
}
