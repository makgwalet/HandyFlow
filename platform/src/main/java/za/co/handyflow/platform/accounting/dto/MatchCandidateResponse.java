package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A journal line suggested as a possible match for a bank transaction —
 * same GL account as the bank account, correct debit/credit side for the
 * transaction's direction, not already linked to another transaction.
 * exactMatch is true when both the amount and date match exactly;
 * candidates are still returned outside that (within a date window) since
 * a real bank statement rarely lines up perfectly with when a journal was
 * entered.
 */
public record MatchCandidateResponse(
        UUID       journalLineId,
        UUID       journalEntryId,
        String     journalEntryNumber,
        LocalDate  entryDate,
        String     description,
        BigDecimal amount,
        boolean    exactMatch
) {}