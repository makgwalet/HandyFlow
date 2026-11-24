package za.co.handyflow.platform.accounting.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-scan checks: the Balance Sheet and Trial Balance must read everything up to the date. Run from the platform folder. */
class PositionReportWiringTest {

    private static final Path ROOT = Path.of("src/main/java/za/co/handyflow/platform/accounting");

    private static String read(String relative) throws IOException { return Files.readString(ROOT.resolve(relative)); }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing " + signature);
        int next = source.indexOf("\n    @Transactional", start + signature.length());
        int nextPrivate = source.indexOf("\n    private ", start + signature.length());
        int end = source.length();
        if (next >= 0) end = Math.min(end, next);
        if (nextPrivate >= 0) end = Math.min(end, nextPrivate);
        return source.substring(start, end);
    }

    @Test void balanceSheetAndTrialBalanceUseThePositionRules() throws IOException {
        String s = read("application/internal/AccountingService.java");
        for (String sig : new String[]{"public FinancialReportResponse getBalanceSheet(", "public FinancialReportResponse getTrialBalance("}) {
            String body = method(s, sig);
            assertTrue(body.contains("position(tenantId, from, to)"), sig + " must use the as-at position");
            assertFalse(body.contains("findPostedInRange"), sig + " must not read only the period");
        }
    }

    @Test void thePositionReadsEveryJournalUpToTheDateAndEveryAccount() throws IOException {
        String s = read("application/internal/AccountingService.java");
        String body = method(s, "private PositionReportRules.Position position(");
        assertTrue(body.contains("journalRepo.findPostedUpTo(tenantId, to)"));
        assertTrue(body.contains("accountRepo.findAllIncludingInactive(tenantId)"));
        assertFalse(body.contains("findAllActive"));
    }

    @Test void theUpToQueryHasNoStartDateAndKeepsReversedJournals() throws IOException {
        String r = read("domain/repository/AccJournalEntryRepository.java");
        int at = r.indexOf("findPostedUpTo");
        String query = r.substring(r.lastIndexOf("@Query", at), at);
        assertTrue(query.contains("e.entryDate <= :to"));
        assertFalse(query.contains("BETWEEN"));
        assertTrue(query.contains("'POSTED', 'REVERSED'"));
        assertTrue(query.contains("e.deletedAt IS NULL"));
        assertTrue(query.contains("e.tenantId = :#{#tenantId.value}"));
    }

    @Test void balanceSheetDrillDownUsesTheSameCumulativeSourceButIncomeAndExpenseStayInThePeriod() throws IOException {
        String body = method(read("application/internal/AccountingService.java"), "public AccountDrillDownResponse getAccountDrillDown(");
        assertTrue(body.contains("journalRepo.findPostedUpTo(tenantId, to)"));
        assertTrue(body.contains("journalRepo.findPostedInRange(tenantId, from, to)"));
        assertTrue(body.contains("\"INCOME\".equals(account.getAccountType())"));
        assertTrue(body.contains("\"EXPENSE\".equals(account.getAccountType())"));
    }
}
