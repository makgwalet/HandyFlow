package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Direct mirror of {@code accounting.AccountingService.getMatchCandidates}'s own return shape. */
public record MatchCandidateResponse(
        UUID journalLineId, UUID journalEntryId, String entryNumber, LocalDate entryDate,
        String description, BigDecimal amount, boolean exactMatch
) {}
