package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Every underlying POSTED journal line that fed into one report line's
 * displayed amount, for a given account + date range. Works identically
 * for P&L, Balance Sheet, and Trial Balance — all three ultimately derive
 * from the same source (journalRepo.findPostedInRange() filtered to lines
 * matching this account), so there's no report-type-specific logic here.
 * openingBalance is included separately since it's a static account
 * property, not itself a journal line, but it's part of why the report
 * line's total looks the way it does for P&L/Balance Sheet (Trial
 * Balance doesn't use it at all — see getTrialBalance()).
 */
public record AccountDrillDownResponse(
        String accountCode,
        String accountName,
        BigDecimal openingBalance,
        List<DrillDownLine> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netMovement
) {
    public record DrillDownLine(
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            String entryDescription,
            String lineDescription,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}
}