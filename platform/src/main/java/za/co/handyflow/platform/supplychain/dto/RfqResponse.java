package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** An RFQ with its lines and invitations (the list endpoint leaves both empty). */
public record RfqResponse(
        UUID id,
        String rfqNumber,
        String status,
        String title,
        String description,
        UUID requisitionId,
        UUID deliverToLocation,
        LocalDate requiredByDate,
        Instant closesAt,
        String currency,
        Weights weights,
        String soleSourceJustification,
        BigDecimal estimatedTotal,
        int quotationCount,
        UUID awardedQuotationId,
        String awardReason,
        boolean overridesRecommendation,
        String awardedByName,
        Instant awardedAt,
        UUID poId,
        String createdByName,
        Instant createdAt,
        List<Line> lines,
        List<Invitation> invitations
) {
    public record Weights(int price, int leadTime, int technical, int performance, int bbbee) {}

    public record Line(
            UUID id,
            int lineNumber,
            UUID requisitionLineId,
            UUID catalogueItemId,
            String itemName,
            String description,
            BigDecimal quantity,
            BigDecimal estimatedUnitCost
    ) {}

    public record Invitation(
            UUID supplierId,
            String supplierName,
            String status,
            boolean emailed,
            String declineReason,
            Instant invitedAt,
            Instant respondedAt
    ) {}
}
