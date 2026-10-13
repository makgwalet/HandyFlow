package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.ContactMethod;
import za.co.handyflow.platform.debtcollection.domain.model.PromiseToPay;
import za.co.handyflow.platform.debtcollection.domain.model.PtpFrequency;
import za.co.handyflow.platform.debtcollection.domain.model.PtpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PtpResponse(
        UUID id,
        UUID caseId,
        UUID seriesId,
        PtpStatus status,
        BigDecimal amount,
        LocalDate dueDate,
        LocalDate graceEnd,
        PtpFrequency frequency,
        int occurrencesRemaining,
        ContactMethod source,
        String terms,
        BigDecimal baselineOutstanding,
        BigDecimal amountReceived,
        BigDecimal shortfall,
        LocalDate resolvedDate,
        String resolutionNote,
        String createdByName,
        Instant createdAt
) {
    public static PtpResponse of(PromiseToPay p) {
        return new PtpResponse(p.getId(), p.getCaseId(), p.getSeriesId(), p.getStatus(), p.getAmount(),
                p.getDueDate(), p.graceEnd(), p.getFrequency(), p.getOccurrencesRemaining(), p.getSource(),
                p.getTerms(), p.getBaselineOutstanding(), p.getAmountReceived(),
                p.getAmount().subtract(p.getAmountReceived()).max(BigDecimal.ZERO), p.getResolvedDate(),
                p.getResolutionNote(), p.getCreatedByName(), p.getCreatedAt());
    }
}
