package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A proposed change to an approved PO, with its line changes. */
public record PoAmendmentResponse(
        UUID id,
        int amendmentNumber,
        int baseRevision,
        String status,
        boolean material,
        String reason,
        String changeSummary,
        BigDecimal previousTotal,
        BigDecimal proposedTotal,
        LocalDate newRequiredByDate,
        UUID newDeliverToLocation,
        String newNotes,
        String newTerms,
        String requestedByName,
        Instant createdAt,
        String decidedByName,
        Instant decidedAt,
        String decisionNote,
        List<Line> lines
) {
    public record Line(
            UUID id,
            String action,
            UUID poLineId,
            String itemName,
            String supplierSku,
            BigDecimal qtyOrdered,
            BigDecimal unitCost,
            BigDecimal vatRate
    ) {}
}
